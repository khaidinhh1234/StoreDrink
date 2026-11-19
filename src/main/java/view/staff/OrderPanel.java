package view.staff;

import view.layout.NotificationDialog;

import model.Order;
import model.OrderDetail;
import model.Drink;
import model.Customer;
import model.User;
import service.DrinkService;
import service.OrderService;
import service.CustomerService;
import service.UserService;
import view.layout.DataSync;
import view.layout.RefreshablePanel;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import view.layout.UITheme;
import java.awt.*;
import java.text.NumberFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Locale;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OrderPanel extends JPanel implements RefreshablePanel {
    private final User user;
    private final OrderService orderService = new OrderService();
    private final DrinkService drinkService = new DrinkService();
    private final UserService userService = new UserService();
    private final DefaultTableModel model;
    private final JTable table;
    private final JTextField searchField = new JTextField(22);
    private final JComboBox<String> statusFilter = new JComboBox<>(new String[] { "Tất cả trạng thái", "Đã duyệt - đang pha chế", "Hoàn thành", "Đã hủy" });
    private final JComboBox<String> typeFilter = new JComboBox<>(new String[] { "Tất cả loại đơn", "Tại bàn", "Mang đi" });
    private final TableRowSorter<DefaultTableModel> sorter;
    private final NumberFormat currency = UITheme.newVndFormatter();
    private final JProgressBar progress = new JProgressBar(0, 3);
    private Order selectedOrder;
    private boolean invoicePrintInProgress;

    public OrderPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout());
        setBackground(UITheme.BACKGROUND);

        JLabel title = new JLabel(
                "STAFF".equalsIgnoreCase(user.getRole()) ? "Vận hành đơn hàng" : "Theo dõi đơn hàng");
        title.setForeground(UITheme.TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setBorder(BorderFactory.createEmptyBorder(24, 28, 12, 28));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(title, BorderLayout.CENTER);
        add(heading, BorderLayout.NORTH);

        model = new DefaultTableModel(
                new Object[] { "Mã đơn", "Loại đơn", "Khách hàng", "Bàn", "Tổng tiền",
                        "Trạng thái", "Ngày tạo", "Thao tác" },
                0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(model);
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.setRowHeight(44);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setFillsViewportHeight(true);
        int[] preferredWidths = { 76, 120, 170, 62, 120, 170, 150, 330 };
        for (int column = 0; column < preferredWidths.length; column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(preferredWidths[column]);
        }
        table.getColumnModel().getColumn(7).setPreferredWidth(330);
        table.getColumnModel().getColumn(7).setMinWidth(330);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateSelectedOrderInfo();
            }
        });
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                int row = table.rowAtPoint(event.getPoint());
                int column = table.columnAtPoint(event.getPoint());
                if (event.getClickCount() != 1 || row < 0) {
                    return;
                }
                if (column == 7) {
                    Rectangle cell = table.getCellRect(row, column, true);
                    int localX = event.getX() - cell.x;
                    int modelRow = table.convertRowIndexToModel(row);
                    List<String> actions = actionLabels(statusCode(
                            String.valueOf(model.getValueAt(modelRow, 5))));
                    if (!actions.isEmpty()) {
                        int gap = 5;
                        int usableWidth = Math.max(1, cell.width - gap * (actions.size() - 1));
                        int step = Math.max(1, usableWidth / actions.size() + gap);
                        int actionIndex = Math.min(actions.size() - 1, localX / step);
                        selectOrderRow(row);
                        performRowAction(actions.get(actionIndex));
                    }
                }
            }
        });
        table.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent event) {
                int row = table.rowAtPoint(event.getPoint());
                int column = table.columnAtPoint(event.getPoint());
                boolean staff = "STAFF".equalsIgnoreCase(user.getRole());
                boolean hasRowActions = staff || "ADMIN".equalsIgnoreCase(user.getRole());
                boolean clickable = row >= 0 && column == 7 && hasRowActions;
                table.setCursor(Cursor.getPredefinedCursor(
                        clickable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });

        JButton refreshButton = new JButton("Làm mới");
        refreshButton.addActionListener(e -> loadOrders());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        progress.setStringPainted(true);
        progress.setString("Chọn đơn hàng");
        actions.add(progress);
        actions.add(refreshButton);

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filterBar.setOpaque(false);
        searchField.setToolTipText("Tìm theo mã đơn, khách hàng hoặc bàn");
        searchField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UITheme.BORDER),
                BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        filterBar.add(new JLabel("Tìm kiếm"));
        filterBar.add(searchField);
        filterBar.add(statusFilter);
        filterBar.add(typeFilter);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void apply() { applyOrderFilters(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { apply(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { apply(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { apply(); }
        });
        statusFilter.addActionListener(e -> applyOrderFilters());
        typeFilter.addActionListener(e -> applyOrderFilters());
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        add(actions, BorderLayout.SOUTH);
        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(filterBar, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        UITheme.styleTree(this);
        UITheme.styleButton(refreshButton, UITheme.Action.NEUTRAL);
        table.getColumnModel().getColumn(7).setCellRenderer(new OrderActionRenderer());
        loadOrders();
        DataSync.register(this, this);
    }

    @Override public void refreshData() {
        if (!table.isEditing()) loadOrders();
    }

    private void loadOrders() {
        try {
            if ("STAFF".equalsIgnoreCase(user.getRole())) recoverPaidCounterOrders();
            List<Order> orders = "CUSTOMER".equalsIgnoreCase(user.getRole())
                    ? orderService.getForUser(user.getId())
                    : orderService.getAll();
            orders = orders.stream().filter(order -> {
                String status = order.getStatus() == null ? "" : order.getStatus().toUpperCase(Locale.ROOT);
                return "CONFIRMED".equals(status) || "COMPLETED".equals(status) || "CANCELLED".equals(status);
            }).collect(Collectors.toCollection(ArrayList::new));
            Map<Integer, String> orderEmployeeNames = new java.util.HashMap<>();
            if (orders.stream().anyMatch(order -> "TAKEAWAY".equalsIgnoreCase(order.getOrderType()))) {
                try {
                    for (User employee : userService.getAll()) {
                        if (employee.getFullName() != null && !employee.getFullName().isBlank()) {
                            orderEmployeeNames.put(employee.getId(), employee.getFullName().trim());
                        }
                    }
                } catch (Exception ignored) {
                    // Keep the order list usable if the optional employee-name lookup fails.
                }
            }
            orders.sort(Comparator
                    .comparingInt((Order order) -> {
                        String status = order.getStatus() == null ? "" : order.getStatus().toUpperCase(Locale.ROOT);
                        return switch (status) {
                            case "CONFIRMED" -> 0;
                            case "COMPLETED" -> 1;
                            default -> 2;
                        };
                    })
                    .thenComparing(Order::getCreatedAt,
                            Comparator.nullsFirst(Comparator.reverseOrder())));
            Integer selectedId = selectedOrder == null ? null : selectedOrder.getId();
            model.setRowCount(0);
            for (Order order : orders) {
                model.addRow(new Object[] {
                        order.getId(),
                        "TAKEAWAY".equalsIgnoreCase(order.getOrderType())
                                ? orderEmployeeNames.getOrDefault(order.getCreatedByUserId(), "Nhân viên") + " ORDER"
                                : "TẠI BÀN",
                        valueOrDash(order.getCustomerName()),
                        order.getTableId() == null ? "-" : String.format("%02d", order.getTableId()),
                        currency.format(order.getTotal()) + " đ",
                        statusLabel(order.getStatus()),
                        formatDateTime(order.getCreatedAt()),
                        actionLabel(order.getStatus())
                });
            }
            applyOrderFilters();
            if (selectedId != null) {
                for (int row = 0; row < model.getRowCount(); row++) {
                    if (((Number) model.getValueAt(row, 0)).intValue() == selectedId) {
                        int viewRow = table.convertRowIndexToView(row);
                        if (viewRow >= 0) table.setRowSelectionInterval(viewRow, viewRow);
                        break;
                    }
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            NotificationDialog.showMessageDialog(this, "Không tải được danh sách đơn hàng: "
                    + exception.getMessage(), "Lỗi", NotificationDialog.ERROR_MESSAGE);
        }
    }

    private void applyOrderFilters() {
        if (sorter == null) return;
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        String status = String.valueOf(statusFilter.getSelectedItem());
        String type = String.valueOf(typeFilter.getSelectedItem());
        sorter.setRowFilter(new javax.swing.RowFilter<>() {
            @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                String rowStatus = String.valueOf(entry.getValue(5));
                String rowType = String.valueOf(entry.getValue(1));
                if (!"Tất cả trạng thái".equals(status) && !rowStatus.equals(status)) return false;
                if ("Tại bàn".equals(type) && !"TẠI BÀN".equals(rowType)) return false;
                if ("Mang đi".equals(type) && !rowType.endsWith("ORDER")) return false;
                if (query.isEmpty()) return true;
                for (int column = 0; column <= 6; column++) {
                    if (String.valueOf(entry.getValue(column)).toLowerCase(Locale.ROOT).contains(query)) return true;
                }
                return false;
            }
        });
    }

    /** Finishes a staff-created counter order after a saved payment if the app was interrupted. */
    private void recoverPaidCounterOrders() {
        try {
            List<Order> processing = orderService.getAll().stream()
                    .filter(order -> "PAYMENT_PROCESSING".equalsIgnoreCase(order.getStatus())
                            && order.getSourceCheckoutRequestId() == null)
                    .toList();
            List<model.Payment> payments = new service.PaymentService().getAll();
            for (Order order : processing) {
                boolean paid = payments.stream().anyMatch(payment -> payment.getOrderId() == order.getId()
                        && "SUCCESS".equalsIgnoreCase(payment.getStatus()));
                if (paid) orderService.confirmCashierOrder(order.getId(), user, null);
            }
        } catch (Exception recoveryFailure) {
            // Keep the order screen usable; a cashier can retry after restoring API connectivity.
            recoveryFailure.printStackTrace();
        }
    }

    private void processSelectedStatus(String newStatus) {
        if (!"STAFF".equalsIgnoreCase(user.getRole())) return;
        if (!"CANCELLED".equalsIgnoreCase(newStatus)) return;
        if (!ensureSelectedOrder()) return;
        int orderId = selectedOrder.getId();
        String cancelReason = null;
        if ("CANCELLED".equals(newStatus)) {
            boolean paidConfirmed="CONFIRMED".equalsIgnoreCase(selectedOrder.getStatus());
            boolean refundAlreadyRecorded = false;
            if (paidConfirmed) {
                try { refundAlreadyRecorded = new service.PaymentService().hasFullOrderRefund(orderId); }
                catch (Exception exception) { showError("Không kiểm tra được lịch sử hoàn tiền", exception); return; }
            }
            cancelReason = NotificationDialog.showInputDialog(this,
                    "Nhập lý do hủy đơn #" + orderId + ":"
                            + (paidConfirmed ? refundAlreadyRecorded
                                    ? "\nKhoản hoàn tiền đã được ghi nhận. Không hoàn thêm lần nữa."
                                    : "\nChỉ tiếp tục nếu đã thực sự trả tiền cho khách." : ""),
                    paidConfirmed ? "Hủy và hoàn tiền" : "Lý do hủy đơn", NotificationDialog.QUESTION_MESSAGE);
            if (cancelReason == null) return;
            if (cancelReason.isBlank()) {
                NotificationDialog.showMessageDialog(this, "Vui lòng nhập lý do hủy đơn.",
                        "Thiếu lý do", NotificationDialog.WARNING_MESSAGE);
                return;
            }
            if (NotificationDialog.showConfirmDialog(this,
                    (paidConfirmed ? refundAlreadyRecorded
                            ? "Khoản hoàn tiền đã có trong lịch sử. Tiếp tục hủy đơn mà không hoàn thêm.\n"
                            : "Xác nhận đã hoàn tiền " + String.format("%,.0f đ", selectedOrder.getTotal())
                                    + " qua " + selectedOrder.getPaymentMethod() + " cho khách.\n" : "")
                            + "Xác nhận hủy đơn #" + orderId + " với lý do: " + cancelReason.trim(),
                    paidConfirmed?"Xác nhận hoàn tiền":"Xác nhận hủy đơn", NotificationDialog.YES_NO_OPTION,
                    NotificationDialog.WARNING_MESSAGE) != NotificationDialog.YES_OPTION) return;
        }
        try {
            orderService.updateStatus(selectedOrder, newStatus, user, cancelReason);
            selectedOrder.setStatus(newStatus);
            loadOrders();
            updateProgress();

            NotificationDialog.showMessageDialog(this,
                    "Đã hủy đơn #" + orderId + ". Hệ thống đã ghi nhận hoàn tiền và hoàn kho (nếu kho đã bị trừ)." );
        } catch (Exception exception) {
            loadOrders();
            showError("Không thể cập nhật đơn hàng", exception);
        }
    }

    private void updateSelectedOrderInfo() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            selectedOrder = null;
            progress.setValue(0);
            progress.setString("Chọn đơn hàng");
            return;
        }
        int row = table.convertRowIndexToModel(viewRow);
        int id = (int) model.getValueAt(row, 0);
        try {
            selectedOrder = orderService.getAll().stream()
                    .filter(order -> order.getId() == id).findFirst().orElse(null);
            updateProgress();
        } catch (Exception exception) {
            showError("Không tải được thông tin đơn", exception);
        }
    }

    private void updateProgress() {
        if (selectedOrder == null) {
            progress.setValue(0);
            progress.setString("Chọn đơn hàng");
            return;
        }

        int value = switch (selectedOrder.getStatus() == null
                ? ""
                : selectedOrder.getStatus().toUpperCase(Locale.ROOT)) {
            case "CONFIRMED" -> 1;
            case "COMPLETED" -> 3;
            case "CANCELLED" -> 0;
            default -> 0;
        };

        progress.setValue(value);
        progress.setString(statusLabel(selectedOrder.getStatus()));
    }

    private void editSelectedOrder() {
        if (!"STAFF".equalsIgnoreCase(user.getRole())) return;
        if (!ensureSelectedOrder())
            return;
        if (!"CONFIRMED".equalsIgnoreCase(selectedOrder.getStatus())) {
            NotificationDialog.showMessageDialog(
                    this,
                    "Chỉ đơn đã duyệt, đang pha chế mới được sửa.");
            return;
        }
        try {
            List<OrderDetail> details = orderService.getDetails(selectedOrder.getId());
            List<Drink> drinks = drinkService.getAll();
            showOrderEditor(details, drinks);
        } catch (Exception exception) {
            showError("Không thể sửa order", exception);
        }
    }

    private String actionLabel(String status) {
        List<String> labels = actionLabels(status);
        return String.join(" | ", labels);
    }

    private List<String> actionLabels(String status) {
        if ("CUSTOMER".equalsIgnoreCase(user.getRole())) {
            return List.of();
        }
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
                case "CANCELLED" -> List.of("Chi tiết", "Lý do hủy");
                case "CONFIRMED" -> List.of("Chi tiết");
                case "COMPLETED" -> List.of("Chi tiết");
                default -> List.of();
            };
        }
        return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "CONFIRMED" -> List.of("Sửa món", "In bill", "Hoàn tất đơn", "Hủy & hoàn tiền");
            case "COMPLETED" -> List.of("Chi tiết", "In hóa đơn");
            case "CANCELLED" -> List.of("Chi tiết", "Lý do hủy");
            default -> List.of();
        };
    }
    private void selectOrderRow(int row) {
        if (table.getSelectedRow() != row) {
            table.setRowSelectionInterval(row, row);
        } else {
            updateSelectedOrderInfo();
        }
    }

    private void performRowAction(String action) {
        switch (action) {
            case "Sửa order" -> editSelectedOrder();
            case "Sửa món" -> editSelectedOrder();
            case "Hoàn tất đơn" -> completePreparationOrder();
            case "Hủy & hoàn tiền" -> processSelectedStatus("CANCELLED");
            case "In bill" -> printSelectedInvoice();
            case "Chi tiết" -> showOrderDetails();
            case "Lý do hủy" -> showCancelReason();
            case "In hóa đơn" -> printSelectedInvoice();
            default -> { }
        }
    }

    private final class QuantityCellRenderer extends JPanel implements TableCellRenderer {
        private final JButton minus = new JButton("−");
        private final JLabel quantity = new JLabel("0", SwingConstants.CENTER);
        private final JButton plus = new JButton("+");

        private QuantityCellRenderer() {
            setLayout(new GridLayout(1, 3, 5, 0));
            setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
            setOpaque(true);
            styleQuantityButton(minus, UITheme.Action.DESTRUCTIVE);
            styleQuantityButton(plus, UITheme.Action.CREATE);
            quantity.setOpaque(true);
            quantity.setBackground(UITheme.BACKGROUND);
            quantity.setForeground(UITheme.TEXT);
            quantity.setFont(new Font("Segoe UI", Font.BOLD, 13));
            add(minus);
            add(quantity);
            add(plus);
        }

        private void styleQuantityButton(JButton button, UITheme.Action action) {
            UITheme.styleButton(button, action);
            button.setFont(new Font("Segoe UI", Font.BOLD, 16));
        }

        @Override
        public Component getTableCellRendererComponent(JTable source, Object value,
                                                       boolean selected, boolean focused, int row, int column) {
            quantity.setText(value == null ? "0" : value.toString());
            setBackground(selected ? UITheme.TABLE_SELECTION : UITheme.CARD);
            revalidate();
            doLayout();
            return this;
        }
    }

    private String drinkName(OrderDetail detail, Map<Integer, Drink> drinkMap) {
        Drink drink = drinkMap.get(detail.getDrinkId());
        return drink == null ? "đồ uống này" : drink.getName();
    }

    private final class OrderActionRenderer extends JPanel implements TableCellRenderer {
        private OrderActionRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable source, Object value,
                                                       boolean selected, boolean focused, int row, int column) {
            String status = statusCode(String.valueOf(source.getValueAt(row, 5)));
            List<String> labels = actionLabels(status);
            setLayout(new GridLayout(1, Math.max(1, labels.size()), 5, 0));
            setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            setBackground(selected ? UITheme.TABLE_SELECTION : UITheme.CARD);
            removeAll();
            for (String label : labels) {
                JButton actionButton = new JButton(label);
                UITheme.Action action = switch (label) {
                    case "Sửa order" -> UITheme.Action.EDIT;
                    case "Hoàn tất đơn" -> UITheme.Action.PAYMENT;
                    case "Hủy & hoàn tiền" -> UITheme.Action.DESTRUCTIVE;
                    case "Sửa món" -> UITheme.Action.EDIT;
                    case "In bill" -> UITheme.Action.PRINT;
                    case "In hóa đơn" -> UITheme.Action.PRINT;
                    default -> UITheme.Action.NEUTRAL;
                };
                UITheme.styleButton(actionButton, action);
                actionButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
                actionButton.setPreferredSize(new Dimension(126, 34));
                add(actionButton);
            }
            revalidate();
            doLayout();
            return this;
        }
    }

    private void showOrderEditor(List<OrderDetail> details, List<Drink> drinks) {

        // Giữ cố định order đang sửa
        Order editingOrder = selectedOrder;

        if (editingOrder == null) {
            NotificationDialog.showMessageDialog(this, "Không xác định được order đang sửa.");
            return;
        }

        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this),
                "Sửa order #" + editingOrder.getId(),
                Dialog.ModalityType.APPLICATION_MODAL);
        JPanel editorContent = new JPanel(new BorderLayout(0, 12));
        editorContent.setBackground(UITheme.BACKGROUND);
        editorContent.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        dialog.setContentPane(editorContent);

        DefaultTableModel detailModel = new DefaultTableModel(
                new Object[] { "Đồ uống / tùy chọn", "Đơn giá", "Số lượng", "Thành tiền", "Tùy chỉnh" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable detailTable = new JTable(detailModel);
        detailTable.setRowHeight(48);
        detailTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        UITheme.styleTable(detailTable);

        Map<Integer, Drink> drinkMap = new java.util.HashMap<>();

        for (Drink drink : drinks) {
            drinkMap.put(drink.getId(), drink);
        }

        // =========================
        // HIỂN THỊ DANH SÁCH MÓN
        // =========================
        Runnable reload = () -> {

            detailModel.setRowCount(0);

            for (OrderDetail detail : details) {

                Drink drink = drinkMap.get(detail.getDrinkId());
                String drinkName = drink == null
                        ? "Đồ uống #" + detail.getDrinkId()
                        : drink.getName();

                StringBuilder label=new StringBuilder(drinkName).append(" · Size ").append(detail.getSize()==null?"S":detail.getSize());
                if(detail.getToppings()!=null&&!detail.getToppings().isEmpty())label.append(" · ").append(detail.getToppings().stream().map(t->t.name).collect(java.util.stream.Collectors.joining(", ")));
                if(detail.getNote()!=null&&!detail.getNote().isBlank())label.append(" · ").append(detail.getNote());
                detailModel.addRow(new Object[] {
                        label.toString(),
                        currency.format(detail.getPrice()) + " đ",
                        detail.getQuantity(),
                        currency.format(detail.getSubtotal()) + " đ", "Tùy chỉnh"
                });
            }
        };

        reload.run();
        JScrollPane detailScroll = new JScrollPane(detailTable);
        detailScroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));
        detailScroll.getViewport().setBackground(UITheme.CARD);

        JLabel heading = new JLabel("Món trong đơn  •  #" + editingOrder.getId());
        heading.setForeground(UITheme.TEXT);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 18));
        editorContent.add(heading, BorderLayout.NORTH);
        editorContent.add(detailScroll, BorderLayout.CENTER);

        // =========================
        // COMBOBOX ĐỒ UỐNG
        // =========================
        JComboBox<Drink> drinkCombo = new JComboBox<>(drinks.toArray(new Drink[0]));

        drinkCombo.setRenderer(new DefaultListCellRenderer() {

            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean selected,
                    boolean focused) {

                super.getListCellRendererComponent(
                        list,
                        value,
                        index,
                        selected,
                        focused);

                if (value instanceof Drink drink) {
                    setText(
                            drink.getName()
                                    + " - "
                                    + currency.format(drink.getPrice())
                                    + " đ");
                }

                return this;
            }
        });

        JTextField quantityField = new JTextField("1", 4);

        JButton add = new JButton("Thêm món");
        JButton save = new JButton("Lưu order");

        detailTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        detailTable.getColumnModel().getColumn(2).setCellRenderer(new QuantityCellRenderer());
        detailTable.getColumnModel().getColumn(4).setPreferredWidth(105);
        detailTable.getColumnModel().getColumn(4).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer(){
            {setHorizontalAlignment(SwingConstants.CENTER);setForeground(UITheme.PRIMARY);setFont(new Font("Segoe UI",Font.BOLD,12));}
        });
        detailTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                int row = detailTable.rowAtPoint(event.getPoint());
                int column = detailTable.columnAtPoint(event.getPoint());
                if(row>=0&&column==4){OrderDetail target=details.get(row);Drink chosen=drinkMap.get(target.getDrinkId());if(chosen!=null){OrderDetail edited=customizeOrderLine(dialog,chosen,target);if(edited!=null){details.set(row,edited);reload.run();}}return;}
                if (row < 0 || column != 2) {
                    return;
                }
                Rectangle cell = detailTable.getCellRect(row, column, true);
                int localX = event.getX() - cell.x;
                int delta = localX < cell.width / 3 ? -1
                        : localX >= cell.width * 2 / 3 ? 1 : 0;
                if (delta == 0) {
                    return;
                }

                OrderDetail detail = details.get(row);
                if (delta > 0) {
                    detail.setQuantity(detail.getQuantity() + 1);
                } else if (detail.getQuantity() > 1) {
                    detail.setQuantity(detail.getQuantity() - 1);
                } else {
                    if (details.size() <= 1) {
                        NotificationDialog.showMessageDialog(dialog,
                                "Order phải còn ít nhất 1 đồ uống. Hãy thêm món khác trước khi xóa món này.",
                                "Không thể xóa món cuối cùng", NotificationDialog.INFORMATION_MESSAGE);
                        return;
                    }
                    int confirmation = NotificationDialog.showConfirmDialog(dialog,
                            "Bạn có muốn xóa " + drinkName(detail, drinkMap) + " khỏi order không?",
                            "Xác nhận xóa món", NotificationDialog.YES_NO_OPTION, NotificationDialog.WARNING_MESSAGE);
                    if (confirmation != NotificationDialog.YES_OPTION) {
                        return;
                    }
                    details.remove(row);
                }
                if (details.contains(detail)) {
                    detail.setSubtotal(detail.getQuantity() * detail.getPrice());
                }
                reload.run();
            }
        });
        detailTable.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent event) {
                boolean quantityCell = detailTable.rowAtPoint(event.getPoint()) >= 0
                        && detailTable.columnAtPoint(event.getPoint()) == 2;
                detailTable.setCursor(Cursor.getPredefinedCursor(
                        quantityCell ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });

        // ==================================================
        // THÊM MÓN
        // ==================================================
        add.addActionListener(event -> {

            Drink drink = (Drink) drinkCombo.getSelectedItem();

            if (drink == null) {
                NotificationDialog.showMessageDialog(
                        dialog,
                        "Hãy chọn đồ uống.");
                return;
            }

            try {

                int quantity = Integer.parseInt(
                        quantityField.getText().trim());

                if (quantity <= 0) {
                    throw new NumberFormatException();
                }

                // Kiểm tra món đã có trong order chưa
                boolean alreadyInOrder = details.stream().anyMatch(item -> item.getDrinkId() == drink.getId());

                if (!alreadyInOrder && !drink.isActive()) {
                    NotificationDialog.showMessageDialog(dialog,
                            drink.getName() + " đã ngừng bán và không thể thêm vào order mới.",
                            "Đồ uống đã ngừng bán", NotificationDialog.INFORMATION_MESSAGE);
                    return;
                }

                if (quantity > drink.getQuantity()) {
                    NotificationDialog.showMessageDialog(dialog,
                            "Tồn kho còn " + drink.getQuantity()
                                    + " cốc cho món này. Vui lòng giảm số lượng.",
                            "Vượt tồn kho", NotificationDialog.INFORMATION_MESSAGE);
                    return;
                }

                OrderDetail configured=customizeOrderLine(dialog,drink,null);
                if(configured==null)return;
                configured.setOrderId(editingOrder.getId());configured.setQuantity(quantity);configured.setSubtotal(configured.getPrice()*quantity);
                OrderDetail identical=details.stream().filter(line->sameOrderLine(line,configured)).findFirst().orElse(null);
                if(identical==null)details.add(configured);else{identical.setQuantity(identical.getQuantity()+quantity);identical.setSubtotal(identical.getPrice()*identical.getQuantity());}

                reload.run();

                NotificationDialog.showMessageDialog(
                        dialog,
                        "Đã thêm "
                                + quantity
                                + " x "
                                + drink.getName());

            } catch (NumberFormatException exception) {

                NotificationDialog.showMessageDialog(
                        dialog,
                        "Số lượng phải là số nguyên dương.");
            }
        });

        // ==================================================
        // LƯU ORDER
        // ==================================================
        save.addActionListener(event -> {

            try {

                if (details.isEmpty()) {
                    NotificationDialog.showMessageDialog(dialog,
                            "Order phải có ít nhất 1 đồ uống.",
                            "Chưa thể lưu order", NotificationDialog.INFORMATION_MESSAGE);
                    return;
                }

                // Nếu đang sửa số lượng trực tiếp trên JTable
                if (detailTable.isEditing()) {
                    detailTable.getCellEditor().stopCellEditing();
                }

                for (int row = 0; row < details.size(); row++) {

                    Object value = detailModel.getValueAt(row, 2);

                    int quantity = Integer.parseInt(
                            String.valueOf(value));

                    if (quantity <= 0) {
                        throw new NumberFormatException();
                    }

                    details.get(row).setQuantity(quantity);
                }
                double revisedTotal=details.stream().mapToDouble(d->d.getPrice()*d.getQuantity()).sum();
                double delta=revisedTotal-editingOrder.getTotal();
                orderService.validateOrderDetailsStock(editingOrder.getId(), details, user);
                boolean adjustmentConfirmed=false;
                String adjustmentReason=null;
                if("CONFIRMED".equalsIgnoreCase(editingOrder.getStatus())&&Math.abs(delta)>0.0001){
                    boolean refund=delta<0;
                    String amount=currency.format(Math.abs(delta))+" đ";
                    String instruction=refund
                            ?"Chênh lệch giảm "+amount+". Hãy hoàn khoản này cho khách trước khi chọn thành công."
                            :"Chênh lệch tăng "+amount+". Hãy thu thêm khoản này từ khách trước khi chọn thành công.";
                    Object[] outcomes={refund?"Hoàn tiền thành công":"Thanh toán thành công",
                            refund?"Hoàn tiền thất bại":"Thanh toán thất bại","Hủy"};
                    int result=JOptionPane.showOptionDialog(dialog,instruction,
                            refund?"Hoàn tiền phần chênh lệch":"Thu thêm phần chênh lệch",
                            JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,outcomes,outcomes[0]);
                    if(result!=0){
                        dialog.dispose();
                        loadOrders();
                        NotificationDialog.showMessageDialog(this,
                                result==1?"Giao dịch chênh lệch thất bại. Thay đổi món không được lưu; order giữ nguyên."
                                        :"Đã hủy xử lý chênh lệch. Thay đổi món không được lưu; order giữ nguyên.",
                                "Không xác nhận thay đổi",NotificationDialog.INFORMATION_MESSAGE);
                        return;
                    }
                    adjustmentConfirmed=true;
                    adjustmentReason=(refund?"Hoàn tiền":"Thu thêm")
                            +" phần chênh lệch khi sửa món đơn #"+editingOrder.getId();
                }
                orderService.saveOrderDetails(editingOrder.getId(), details, user,
                        adjustmentConfirmed ? delta : null, adjustmentReason);

                dialog.dispose();

                loadOrders();

                String savedMessage = !adjustmentConfirmed
                        ? "Đã lưu danh sách món trong order."
                        : delta < 0
                                ? "Đã lưu món, hoàn tiền " + currency.format(Math.abs(delta))
                                        + " đ và ghi vào lịch sử thanh toán."
                                : "Đã lưu món, thu thêm " + currency.format(delta)
                                        + " đ và ghi vào lịch sử thanh toán.";
                NotificationDialog.showMessageDialog(this, savedMessage);

            } catch (NumberFormatException exception) {

                NotificationDialog.showMessageDialog(
                        dialog,
                        "Số lượng phải là số nguyên dương.");

            } catch (Exception exception) {

                showError(
                        "Không thể lưu danh sách món",
                        exception);
            }
        });

        // =========================
        // PANEL BUTTON
        // =========================
        JPanel controls = new JPanel(new BorderLayout(0, 8));
        controls.setBackground(UITheme.BACKGROUND);
        JPanel addControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        addControls.setOpaque(false);
        JLabel addLabel = new JLabel("Thêm món:");
        addLabel.setForeground(UITheme.MUTED);
        JLabel quantityLabel = new JLabel("Số lượng:");
        quantityLabel.setForeground(UITheme.MUTED);
        addControls.add(addLabel);
        addControls.add(drinkCombo);
        addControls.add(quantityLabel);
        addControls.add(quantityField);
        addControls.add(add);
        JPanel saveControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        saveControls.setOpaque(false);
        saveControls.add(save);
        controls.add(addControls, BorderLayout.CENTER);
        controls.add(saveControls, BorderLayout.SOUTH);
        editorContent.add(controls, BorderLayout.SOUTH);

        UITheme.styleTree(editorContent);
        UITheme.styleButton(add, UITheme.Action.CREATE);
        UITheme.styleButton(save, UITheme.Action.CONFIRM);
        detailTable.getColumnModel().getColumn(2).setCellRenderer(new QuantityCellRenderer());
        dialog.setSize(840, 520);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private OrderDetail customizeOrderLine(Component owner, Drink drink, OrderDetail original) {
        try {
            List<Drink.SizeOption> sizeOptions=new ArrayList<>(drink.getAvailableSizes().stream().limit(3).toList());
            if(original!=null&&sizeOptions.stream().noneMatch(option->option.getName().equalsIgnoreCase(original.getSize())))
                sizeOptions.add(new Drink.SizeOption(original.getSize(),drink.getPrice()+original.getSizeExtraPrice()));
            JComboBox<Drink.SizeOption> size=new JComboBox<>(sizeOptions.toArray(Drink.SizeOption[]::new));
            if(original!=null)for(Drink.SizeOption option:sizeOptions)if(option.getName().equalsIgnoreCase(original.getSize()))size.setSelectedItem(option);

            List<model.Topping> options=new ArrayList<>(new service.ToppingService().getForDrink(drink.getId()));
            if(options.size()>3)options=new ArrayList<>(options.subList(0,3));
            if(original!=null&&original.getToppings()!=null)for(OrderDetail.ToppingSnapshot snapshot:original.getToppings()){
                if(options.stream().noneMatch(option->option.getId()==snapshot.toppingId)){
                    if(options.size()>=3){
                        int replaceIndex=-1;
                        for(int index=options.size()-1;index>=0;index--){
                            int candidateId=options.get(index).getId();
                            boolean historical=original.getToppings().stream().anyMatch(topping->topping.toppingId==candidateId);
                            if(!historical){replaceIndex=index;break;}
                        }
                        if(replaceIndex>=0)options.remove(replaceIndex);
                    }
                    if(options.size()<3){model.Topping old=new model.Topping(snapshot.name,snapshot.price);old.setId(snapshot.toppingId);options.add(old);}
                }
            }
            JPanel toppingPanel=new JPanel(new GridLayout(0,2,6,6));List<JCheckBox> checks=new ArrayList<>();
            for(model.Topping topping:options){JCheckBox check=new JCheckBox(topping.getName()+" (+"+currency.format(topping.getPrice())+" đ)");check.putClientProperty("topping",topping);
                if(original!=null&&original.getToppings()!=null&&original.getToppings().stream().anyMatch(t->t.toppingId==topping.getId()))check.setSelected(true);
                checks.add(check);toppingPanel.add(check);}
            if(options.isEmpty())toppingPanel.add(new JLabel("Chưa cấu hình topping cho món này."));
            JTextField note=new JTextField(original==null?"":original.getNote()==null?"":original.getNote(),25);
            JLabel priceLabel=new JLabel();priceLabel.setFont(new Font("Segoe UI",Font.BOLD,16));priceLabel.setForeground(UITheme.SUCCESS);
            Runnable refreshPrice=()->{Drink.SizeOption selected=(Drink.SizeOption)size.getSelectedItem();double unit=selected==null?drink.getPrice():selected.getPrice();for(JCheckBox check:checks)if(check.isSelected())unit+=((model.Topping)check.getClientProperty("topping")).getPrice();priceLabel.setText("Đơn giá mới: "+currency.format(unit)+" đ");};
            size.addActionListener(e->refreshPrice.run());for(JCheckBox check:checks)check.addActionListener(e->refreshPrice.run());refreshPrice.run();
            JPanel form=new JPanel(new BorderLayout(8,8));form.add(new JLabel("Tùy chỉnh: "+drink.getName()),BorderLayout.NORTH);JPanel center=new JPanel(new GridLayout(0,1,5,5));center.add(new JLabel("Chọn size"));center.add(size);center.add(new JLabel("Topping (tối đa 3)"));center.add(new JScrollPane(toppingPanel));center.add(new JLabel("Ghi chú món"));center.add(note);center.add(priceLabel);form.add(center,BorderLayout.CENTER);
            if(JOptionPane.showConfirmDialog(owner,form,"Size, topping và ghi chú",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return null;
            Drink.SizeOption selected=(Drink.SizeOption)size.getSelectedItem();double unitPrice=selected==null?drink.getPrice():selected.getPrice();
            OrderDetail result=original==null?new OrderDetail(0,0,drink.getId(),1,unitPrice):new OrderDetail(original.getId(),original.getOrderId(),original.getDrinkId(),original.getQuantity(),unitPrice);
            result.setDrinkName(drink.getName());result.setSize(selected==null?"S":selected.getName());result.setSizeExtraPrice(unitPrice-drink.getPrice());result.setNote(note.getText().trim());
            List<OrderDetail.ToppingSnapshot> chosen=new ArrayList<>();for(JCheckBox check:checks)if(check.isSelected()){model.Topping topping=(model.Topping)check.getClientProperty("topping");chosen.add(new OrderDetail.ToppingSnapshot(topping.getId(),topping.getName(),topping.getPrice()));}
            result.setToppings(chosen);result.setToppingTotal(chosen.stream().mapToDouble(topping->topping.price).sum());result.setPrice(unitPrice+result.getToppingTotal());result.setSubtotal(result.getPrice()*result.getQuantity());return result;
        }catch(Exception ex){showError("Không tải được tùy chọn món",ex);return null;}
    }

    private boolean sameOrderLine(OrderDetail a, OrderDetail b){
        if(a.getDrinkId()!=b.getDrinkId()||!java.util.Objects.equals(a.getSize(),b.getSize())||!java.util.Objects.equals(a.getNote(),b.getNote()))return false;
        List<Integer> x=a.getToppings()==null?List.of():a.getToppings().stream().map(t->t.toppingId).sorted().toList();
        List<Integer> y=b.getToppings()==null?List.of():b.getToppings().stream().map(t->t.toppingId).sorted().toList();return x.equals(y);
    }

    private void completePreparationOrder() {
        if(!"STAFF".equalsIgnoreCase(user.getRole()) || !ensureSelectedOrder()) return;
        if(!"CONFIRMED".equalsIgnoreCase(selectedOrder.getStatus())) return;
        if(NotificationDialog.showConfirmDialog(this,"Xác nhận đơn #"+selectedOrder.getId()+" đã pha chế và giao xong?","Hoàn tất đơn",NotificationDialog.YES_NO_OPTION,NotificationDialog.QUESTION_MESSAGE)!=NotificationDialog.YES_OPTION)return;
        try{orderService.completeOrder(selectedOrder,user);loadOrders();updateProgress();NotificationDialog.showMessageDialog(this,"Đơn đã hoàn tất.");}
        catch(Exception ex){loadOrders();showError("Không thể hoàn tất đơn",ex);}
    }

    private void showOrderDetails() {
        if (!ensureSelectedOrder()) return;
        try {
            Order fresh = orderService.getAll().stream()
                    .filter(order -> order.getId() == selectedOrder.getId()).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hàng."));
            List<OrderDetail> details = orderService.getDetails(fresh.getId());
            Map<Integer, String> employeeNames = new java.util.HashMap<>();
            Map<Integer, String> customerAccountNames = new java.util.HashMap<>();
            for (User employee : new UserService().getAll()) {
                employeeNames.put(employee.getId(), employee.getFullName());
                if ("CUSTOMER".equalsIgnoreCase(employee.getRole())) {
                    customerAccountNames.put(employee.getId(), employee.getFullName());
                }
            }
            List<Customer> customers = new CustomerService().getAll();
            Map<Integer, String> drinkNames = new java.util.HashMap<>();
            for (Drink drink : drinkService.getAll()) drinkNames.put(drink.getId(), drink.getName());
            showOrderDetailsDialog(fresh, details, employeeNames, customerAccountNames, drinkNames, customers);
        } catch (Exception exception) {
            showError("Không thể xem chi tiết đơn", exception);
        }
    }

    private void showOrderDetailsDialog(Order order, List<OrderDetail> details,
                                        Map<Integer, String> employeeNames, Map<Integer, String> customerAccountNames,
                                        Map<Integer, String> drinkNames,
                                        List<Customer> customers) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Chi tiết đơn hàng #" + order.getId(), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(0, 14));
        dialog.getContentPane().setBackground(UITheme.BACKGROUND);
        ((JComponent) dialog.getContentPane()).setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        JPanel headingText = new JPanel();
        headingText.setOpaque(false);
        headingText.setLayout(new BoxLayout(headingText, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("CHI TIẾT ĐƠN HÀNG");
        title.setFont(new Font("Segoe UI", Font.BOLD, 21));
        title.setForeground(UITheme.TEXT);
        JLabel orderCode = new JLabel("Mã đơn hàng  #" + order.getId());
        orderCode.setFont(new Font("Segoe UI", Font.BOLD, 14));
        orderCode.setForeground(UITheme.PRIMARY);
        headingText.add(title);
        headingText.add(Box.createVerticalStrut(4));
        headingText.add(orderCode);
        header.add(headingText, BorderLayout.CENTER);
        JButton close = new JButton("Đóng");
        UITheme.styleButton(close, UITheme.Action.NEUTRAL);
        close.addActionListener(event -> dialog.dispose());
        header.add(close, BorderLayout.EAST);
        dialog.add(header, BorderLayout.NORTH);

        JPanel infoStack = new JPanel();
        infoStack.setOpaque(false);
        infoStack.setLayout(new BoxLayout(infoStack, BoxLayout.Y_AXIS));
        JPanel orderInfo = detailsCard("Thông tin đơn hàng", List.<String[]>of(
                new String[] { "Trạng thái", statusLabel(order.getStatus()) },
                new String[] { "Khách hàng", resolveCustomerName(order, customers, customerAccountNames) },
                new String[] { "Ngày tạo", formatDateTime(order.getCreatedAt()) },
                new String[] { "Loại đơn", "TAKEAWAY".equalsIgnoreCase(order.getOrderType()) ? "Mang đi" : "Tại bàn" },
                new String[] { "Bàn", order.getTableId() == null ? "-" : String.format("%02d", order.getTableId()) },
                new String[] { "Số điện thoại", valueOrDash(order.getCustomerPhone()) }
        ));
        JLabel statusValue = (JLabel) orderInfo.getClientProperty("order.statusValue");
        if (statusValue != null) styleStatusValue(statusValue, order.getStatus());
        infoStack.add(orderInfo);

        List<String[]> staffFields = new ArrayList<>();
        String status = order.getStatus() == null ? "" : order.getStatus().toUpperCase(Locale.ROOT);
        staffFields.add(new String[] { "Người duyệt", employeeName(order.getConfirmedBy(), employeeNames) });
        staffFields.add(new String[] { "Thời điểm duyệt", formatDateTime(order.getConfirmedAt()) });
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            staffFields.add(new String[] { "Người thanh toán", employeeName(order.getCompletedBy(), employeeNames) });
            staffFields.add(new String[] { "Thời điểm thanh toán", formatDateTime(order.getCompletedAt()) });
        }
        if ("CANCELLED".equals(status)) {
            staffFields.add(new String[] { "Người hủy", cancelledByName(order.getCancelledBy(), employeeNames) });
            staffFields.add(new String[] { "Thời điểm hủy", formatDateTime(order.getCancelledAt()) });
            staffFields.add(new String[] { "Lý do hủy", valueOrDash(order.getCancelReason()) });
        }
        if (!staffFields.isEmpty()) {
            infoStack.add(Box.createVerticalStrut(8));
            infoStack.add(detailsCard("Thông tin nhân viên xử lý", staffFields));
        }
        dialog.add(infoStack, BorderLayout.NORTH);

        DefaultTableModel itemsModel = new DefaultTableModel(
                new Object[] { "STT", "Tên đồ uống", "Tùy chọn món", "Số lượng", "Đơn giá", "Thành tiền" }, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        int totalQuantity = 0;
        for (int index = 0; index < details.size(); index++) {
            OrderDetail detail = details.get(index);
            totalQuantity += detail.getQuantity();
            List<String> options = new ArrayList<>();
            options.add("Size " + (detail.getSize() == null || detail.getSize().isBlank() ? "S" : detail.getSize()));
            if (detail.getToppings() != null && !detail.getToppings().isEmpty()) {
                options.add("Topping: " + detail.getToppings().stream()
                        .map(topping -> topping.name + " (+" + currency.format(topping.price) + " đ)")
                        .collect(java.util.stream.Collectors.joining(", ")));
            }
            if (detail.getNote() != null && !detail.getNote().isBlank()) options.add("Ghi chú: " + detail.getNote());
            String drinkName = detail.getDrinkName() == null || detail.getDrinkName().isBlank()
                    ? drinkNames.getOrDefault(detail.getDrinkId(), "Sản phẩm không tồn tại")
                    : detail.getDrinkName();
            itemsModel.addRow(new Object[] { index + 1,
                    drinkName,
                    String.join("\n", options), detail.getQuantity(), currency.format(detail.getPrice()) + " đ",
                    currency.format(detail.getSubtotal()) + " đ" });
        }
        JTable itemsTable = new JTable(itemsModel);
        itemsTable.setRowHeight(52);
        itemsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        itemsTable.getColumnModel().getColumn(2).setCellRenderer((source, value, selected, focused, row, column) -> {
            JTextArea area = new JTextArea(value == null ? "" : value.toString());
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setOpaque(true);
            area.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
            area.setBackground(selected ? source.getSelectionBackground() : source.getBackground());
            area.setForeground(selected ? source.getSelectionForeground() : source.getForeground());
            area.setFont(source.getFont());
            return area;
        });
        itemsTable.setFillsViewportHeight(true);
        JScrollPane itemsScroll = new JScrollPane(itemsTable);
        itemsScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        itemsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        itemsScroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));
        JPanel itemsCard = new JPanel(new BorderLayout(0, 9));
        itemsCard.setBackground(UITheme.CARD);
        itemsCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER), BorderFactory.createEmptyBorder(8, 10, 10, 10)));
        JLabel itemsTitle = new JLabel("Danh sách món đã đặt");
        itemsTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        itemsTitle.setForeground(UITheme.TEXT);
        itemsCard.add(itemsTitle, BorderLayout.NORTH);
        itemsCard.add(itemsScroll, BorderLayout.CENTER);
        dialog.add(itemsCard, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(UITheme.CARD);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        JLabel count = new JLabel("Tổng số lượng món: " + totalQuantity);
        count.setForeground(UITheme.MUTED);
        count.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JLabel total = new JLabel("TỔNG THANH TOÁN: " + currency.format(order.getTotal()) + " đ");
        total.setForeground(UITheme.SUCCESS);
        total.setFont(new Font("Segoe UI", Font.BOLD, 19));
        footer.add(count, BorderLayout.WEST);
        footer.add(total, BorderLayout.EAST);
        dialog.add(footer, BorderLayout.SOUTH);

        UITheme.styleTree(dialog.getContentPane());
        itemsTable.setRowHeight(52);
        itemsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        itemsTable.getColumnModel().getColumn(0).setPreferredWidth(55);
        itemsTable.getColumnModel().getColumn(1).setPreferredWidth(190);
        itemsTable.getColumnModel().getColumn(2).setPreferredWidth(330);
        itemsTable.getColumnModel().getColumn(3).setPreferredWidth(70);
        itemsTable.getColumnModel().getColumn(4).setPreferredWidth(115);
        itemsTable.getColumnModel().getColumn(5).setPreferredWidth(125);
        itemsTable.setPreferredScrollableViewportSize(new Dimension(750,
                itemsTable.getTableHeader().getPreferredSize().height
                        + Math.max(1, itemsTable.getRowCount()) * itemsTable.getRowHeight() + 4));
        dialog.pack();
        dialog.setSize(840, Math.max(530, dialog.getHeight()));
        dialog.setMinimumSize(new Dimension(760, 530));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel detailsCard(String title, List<String[]> values) {
        JPanel card = new JPanel(new BorderLayout(0, 7));
        card.setBackground(UITheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER), BorderFactory.createEmptyBorder(9, 11, 10, 11)));
        JLabel heading = new JLabel(title);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 15));
        heading.setForeground(UITheme.TEXT);
        card.add(heading, BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(0, 2, 14, 6));
        grid.setOpaque(false);
        for (String[] pair : values) {
            JPanel field = new JPanel(new BorderLayout(0, 3));
            field.setOpaque(false);
            JLabel label = new JLabel(pair[0]);
            label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            label.setForeground(UITheme.MUTED);
            JLabel value = new JLabel(pair[1]);
            value.setFont(new Font("Segoe UI", Font.BOLD, 12));
            value.setForeground(UITheme.TEXT);
            if ("Trạng thái".equals(pair[0])) card.putClientProperty("order.statusValue", value);
            field.add(label, BorderLayout.NORTH);
            field.add(value, BorderLayout.CENTER);
            grid.add(field);
        }
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private String resolveCustomerName(Order order, List<Customer> customers,
                                       Map<Integer, String> customerAccountNames) {
        if (order.getCustomerName() != null && !order.getCustomerName().isBlank()) {
            return order.getCustomerName().trim();
        }
        String phone = order.getCustomerPhone() == null ? "" : order.getCustomerPhone().trim();
        if (!phone.isEmpty()) {
            for (Customer customer : customers) {
                if (customer.getPhone() != null && phone.equals(customer.getPhone().trim())
                        && customer.getName() != null && !customer.getName().isBlank()) {
                    return customer.getName().trim();
                }
            }
        }
        String accountName = customerAccountNames.get(order.getUserId());
        return accountName == null || accountName.isBlank() ? "Chưa có thông tin khách hàng" : accountName;
    }

    private void styleStatusValue(JLabel label, String status) {
        Color color = switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "CONFIRMED" -> UITheme.PRIMARY;
            case "COMPLETED" -> UITheme.SUCCESS;
            case "CANCELLED" -> UITheme.DANGER;
            default -> UITheme.SECONDARY;
        };
        label.setOpaque(true);
        label.setForeground(color == UITheme.WARNING ? UITheme.TEXT : Color.WHITE);
        label.setBackground(color);
        label.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
    }

    private String employeeName(Integer id, Map<Integer, String> employeeNames) {
        if (id == null || id <= 0) return "-";
        return employeeNames.getOrDefault(id, "-");
    }

    private String cancelledByName(String id, Map<Integer, String> employeeNames) {
        if (id == null || id.isBlank()) return "Chưa có";
        if ("SYSTEM".equalsIgnoreCase(id)) return "Hệ thống";
        try {
            return employeeName(Integer.valueOf(id), employeeNames);
        } catch (NumberFormatException ignored) {
            return id;
        }
    }

    private void showCancelReason() {
        if (!ensureSelectedOrder()) return;
        if (!"CANCELLED".equalsIgnoreCase(selectedOrder.getStatus())) return;
        String reason = selectedOrder.getCancelReason();
        String cancelledBy = selectedOrder.getCancelledBy() == null ? "-" : selectedOrder.getCancelledBy();
        String cancelledAt = formatDateTime(selectedOrder.getCancelledAt());
        NotificationDialog.showMessageDialog(this,
                "Lý do: " + (reason == null || reason.isBlank() ? "Chưa ghi nhận" : reason)
                        + "\nNguồn/người hủy: " + cancelledBy + "\nThời điểm: " + cancelledAt,
                "Lịch sử hủy đơn #" + selectedOrder.getId(), NotificationDialog.INFORMATION_MESSAGE);
    }

    private void printSelectedInvoice() {
        if (invoicePrintInProgress) return;
        if (!"STAFF".equalsIgnoreCase(user.getRole())) {
            NotificationDialog.showMessageDialog(this, "Chỉ nhân viên thu ngân được phép in bill.",
                    "Không có quyền in bill", NotificationDialog.WARNING_MESSAGE);
            return;
        }
        if (!ensureSelectedOrder())
            return;
        Order orderToPrint = selectedOrder;
        invoicePrintInProgress = true;
        utils.ReceiptPrinter.printAsync(orderToPrint, () -> {
            boolean completed = "COMPLETED".equalsIgnoreCase(orderToPrint.getStatus());
            boolean confirmedPaid = "CONFIRMED".equalsIgnoreCase(orderToPrint.getStatus())
                    && new service.PaymentService().getAll().stream().anyMatch(payment ->
                            payment.getOrderId() == orderToPrint.getId()
                                    && "SUCCESS".equalsIgnoreCase(payment.getStatus()));
            if (!completed && !confirmedPaid) {
                throw new IllegalStateException("Chỉ có thể in bill khi thanh toán đã thành công.");
            }
            return orderService.getDetails(orderToPrint.getId());
        }, exception -> showError("Không thể in hóa đơn", exception),
                () -> invoicePrintInProgress = false);
    }

    private boolean ensureSelectedOrder() {
        if (selectedOrder == null) {
            updateSelectedOrderInfo();
        }
        if (selectedOrder == null) {
            NotificationDialog.showMessageDialog(this, "Hãy chọn một đơn hàng.");
            return false;
        }
        return true;
    }

    private void showError(String message, Exception exception) {
        exception.printStackTrace();
        NotificationDialog.showMessageDialog(this, message + ": " + exception.getMessage(),
                "Lỗi", NotificationDialog.ERROR_MESSAGE);
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "CONFIRMED" -> "Đã duyệt - đang pha chế";
            case "COMPLETED" -> "Hoàn thành";
            case "CANCELLED" -> "Đã hủy";
            default -> "Không xác định";
        };
    }

    private String statusCode(String label) {
        return switch (label) {
            case "Đã duyệt - đang pha chế" -> "CONFIRMED";
            case "Hoàn thành", "Đã thanh toán - hoàn thành" -> "COMPLETED";
            case "Đã hủy" -> "CANCELLED";
            default -> "";
        };
    }

    private String paymentLabel(String paymentMethod) {
        return switch (paymentMethod == null ? "" : paymentMethod.toUpperCase(Locale.ROOT)) {
            case "CASH" -> "Tiền mặt";
            case "TRANSFER", "BANK", "CK" -> "Chuyển khoản";
            case "UNPAID" -> "Chưa thanh toán";
            default -> paymentMethod == null ? "-" : paymentMethod;
        };
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String formatDateTime(String value) {
        if (value == null || value.isBlank())
            return "-";
        try {
            return LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception exception) {
            return value;
        }
    }
}
