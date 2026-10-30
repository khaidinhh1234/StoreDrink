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
import view.layout.UITheme;
import java.awt.*;
import java.text.NumberFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Locale;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

public class OrderPanel extends JPanel implements RefreshablePanel {
    private final User user;
    private final OrderService orderService = new OrderService();
    private final DrinkService drinkService = new DrinkService();
    private final UserService userService = new UserService();
    private final DefaultTableModel model;
    private final JTable table;
    private final NumberFormat currency = UITheme.newVndFormatter();
    private final JLabel notification = new JLabel();
    private final JProgressBar progress = new JProgressBar(0, 3);
    private final Set<Integer> alertedOverdueOrderIds = new HashSet<>();
    private final Map<Integer, Timer> overdueAlertTimers = new java.util.HashMap<>();
    private Order selectedOrder;

    public OrderPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout());
        setBackground(UITheme.BACKGROUND);

        JLabel title = new JLabel(
                "STAFF".equalsIgnoreCase(user.getRole()) ? "Vận hành đơn hàng" : "Theo dõi đơn hàng");
        title.setForeground(UITheme.TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setBorder(BorderFactory.createEmptyBorder(24, 28, 12, 28));
        notification.setForeground(UITheme.DANGER);
        notification.setBorder(BorderFactory.createEmptyBorder(0, 20, 8, 20));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(title, BorderLayout.NORTH);
        heading.add(notification, BorderLayout.SOUTH);
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
                if (column == 5 && "STAFF".equalsIgnoreCase(user.getRole())
                        && "PENDING".equalsIgnoreCase(statusCode(
                        String.valueOf(model.getValueAt(row, column))))) {
                    selectOrderRow(row);
                    updateSelectedStatus();
                } else if (column == 7) {
                    Rectangle cell = table.getCellRect(row, column, true);
                    int localX = event.getX() - cell.x;
                    List<String> actions = actionLabels(statusCode(
                            String.valueOf(model.getValueAt(row, 5))));
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
                boolean clickable = row >= 0 && ((column == 7 && hasRowActions)
                        || (column == 5 && staff && "PENDING".equalsIgnoreCase(statusCode(
                                String.valueOf(model.getValueAt(row, 5))))));
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

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        add(actions, BorderLayout.SOUTH);
        add(scrollPane, BorderLayout.CENTER);
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
            List<Order> orders = "CUSTOMER".equalsIgnoreCase(user.getRole())
                    ? orderService.getForUser(user.getId())
                    : orderService.getAll();
            orders.sort(Comparator
                    .comparingInt((Order order) -> {
                        String status = order.getStatus() == null ? "" : order.getStatus().toUpperCase(Locale.ROOT);
                        return switch (status) {
                            case "PENDING" -> 0;
                            case "CONFIRMED" -> 1;
                            default -> 2;
                        };
                    })
                    .thenComparing(Order::getCreatedAt,
                            Comparator.nullsFirst(Comparator.reverseOrder())));
            List<Order> pendingOrders = orders.stream()
                    .filter(order -> "PENDING".equalsIgnoreCase(order.getStatus()))
                    .toList();
            int pendingCount = pendingOrders.size();
            List<Order> overdueOrders = pendingOrders.stream().filter(this::hasWaitedFiveMinutes).toList();
            Set<Integer> currentlyOverdueIds = new HashSet<>();
            for (Order order : overdueOrders) {
                currentlyOverdueIds.add(order.getId());
            }
            alertedOverdueOrderIds.retainAll(currentlyOverdueIds);
            scheduleOverdueAlerts(pendingOrders, currentlyOverdueIds);
            List<Order> newlyOverdue = overdueOrders.stream()
                    .filter(order -> !alertedOverdueOrderIds.contains(order.getId())).toList();
            if (pendingCount > 0) {
                notification.setText("Có " + pendingCount + " order đang chờ duyệt");
                notification.setForeground(UITheme.MUTED);
            } else {
                notification.setText("");
            }
            Integer selectedId = selectedOrder == null ? null : selectedOrder.getId();
            model.setRowCount(0);
            for (Order order : orders) {
                model.addRow(new Object[] {
                        order.getId(),
                        "TAKEAWAY".equalsIgnoreCase(order.getOrderType()) ? "Nhân viên Order" : "TẠI BÀN",
                        valueOrDash(order.getCustomerName()),
                        order.getTableId() == null ? "-" : String.format("%02d", order.getTableId()),
                        currency.format(order.getTotal()) + " đ",
                        statusLabel(order.getStatus()),
                        formatDateTime(order.getCreatedAt()),
                        actionLabel(order.getStatus())
                });
            }
            if (selectedId != null) {
                for (int row = 0; row < model.getRowCount(); row++) {
                    if (((Number) model.getValueAt(row, 0)).intValue() == selectedId) {
                        table.setRowSelectionInterval(row, row);
                        break;
                    }
                }
            }
            if (isShowing() && !newlyOverdue.isEmpty()) {
                newlyOverdue.forEach(order -> alertedOverdueOrderIds.add(order.getId()));
                showOverdueAlert(newlyOverdue);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            NotificationDialog.showMessageDialog(this, "Không tải được danh sách đơn hàng: "
                    + exception.getMessage(), "Lỗi", NotificationDialog.ERROR_MESSAGE);
        }
    }

    private void showOverdueAlert(List<Order> orders) {
        String orderIds = orders.stream().map(order -> "#" + order.getId())
                .collect(Collectors.joining(", "));
        String message = orders.size() + " đơn hàng đã chờ duyệt quá 5 phút: " + orderIds
                + "\nVui lòng kiểm tra và xử lý. Đơn sẽ tự hủy sau 30 phút nếu chưa được duyệt.";
        NotificationDialog.showMessageDialog(this, message, "Cảnh báo đơn chờ duyệt",
                NotificationDialog.WARNING_MESSAGE);
    }

    private void scheduleOverdueAlerts(List<Order> pendingOrders, Set<Integer> overdueIds) {
        Set<Integer> pendingIds = new HashSet<>();
        for (Order order : pendingOrders) pendingIds.add(order.getId());
        overdueAlertTimers.entrySet().removeIf(entry -> {
            if (!pendingIds.contains(entry.getKey()) || overdueIds.contains(entry.getKey())) {
                entry.getValue().stop();
                return true;
            }
            return false;
        });
        for (Order order : pendingOrders) {
            if (overdueIds.contains(order.getId()) || overdueAlertTimers.containsKey(order.getId())) continue;
            try {
                LocalDateTime createdAt = LocalDateTime.parse(order.getCreatedAt());
                long remainingMs = Duration.between(LocalDateTime.now(), createdAt.plusMinutes(5)).toMillis();
                int delay = (int) Math.max(1, Math.min(Integer.MAX_VALUE, remainingMs));
                Timer timer = new Timer(delay, event -> {
                    overdueAlertTimers.remove(order.getId());
                    loadOrders();
                });
                timer.setRepeats(false);
                overdueAlertTimers.put(order.getId(), timer);
                timer.start();
            } catch (RuntimeException ignored) {
                // Ignore legacy records with an unrecognized creation time.
            }
        }
    }

    private boolean hasWaitedFiveMinutes(Order order) {
        if (order.getCreatedAt() == null || order.getCreatedAt().isBlank()) return false;
        try {
            return Duration.between(LocalDateTime.parse(order.getCreatedAt()), LocalDateTime.now())
                    .compareTo(Duration.ofMinutes(5)) >= 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void updateSelectedStatus() {
        if (!"STAFF".equalsIgnoreCase(user.getRole())) {
            return;
        }

        if (!ensureSelectedOrder()) {
            return;
        }

        String current = selectedOrder.getStatus() == null
                ? "PENDING"
                : selectedOrder.getStatus().toUpperCase(Locale.ROOT);

        // Chỉ cho phép đúng luồng nghiệp vụ:
        // PENDING -> CONFIRMED hoặc CANCELLED
        // CONFIRMED -> CANCELLED
        // COMPLETED/CANCELLED -> không đổi bằng nút này.
        String[] statusLabels;

        switch (current) {
            case "PENDING" -> statusLabels = new String[] {
                    "Duyệt đơn",
                    "Hủy đơn"
            };
            case "CONFIRMED" -> statusLabels = new String[] {
                    "Hủy đơn"
            };
            case "COMPLETED", "CANCELLED" -> {
                NotificationDialog.showMessageDialog(
                        this,
                        "Đơn này đã " + statusLabel(current) + ". Không thể cập nhật trạng thái bằng nút này.",
                        "Không thể cập nhật",
                        NotificationDialog.INFORMATION_MESSAGE);
                return;
            }
            default -> {
                NotificationDialog.showMessageDialog(
                        this,
                        "Trạng thái đơn không hợp lệ: " + current,
                        "Lỗi dữ liệu",
                        NotificationDialog.ERROR_MESSAGE);
                return;
            }
        }

        String choice = (String) NotificationDialog.showInputDialog(
                this,
                "Thao tác với đơn #" + selectedOrder.getId() + ":",
                "Xử lý đơn hàng",
                NotificationDialog.PLAIN_MESSAGE,
                null,
                statusLabels,
                statusLabels[0]);

        if (choice == null) {
            return;
        }
        String newStatus = "Duyệt đơn".equals(choice) ? "CONFIRMED" : "CANCELLED";
        processSelectedStatus(newStatus);
    }

    private void processSelectedStatus(String newStatus) {
        if (!"STAFF".equalsIgnoreCase(user.getRole())) return;
        if (!ensureSelectedOrder()) return;
        int orderId = selectedOrder.getId();
        String cancelReason = null;
        if ("CANCELLED".equals(newStatus)) {
            cancelReason = NotificationDialog.showInputDialog(this,
                    "Nhập lý do hủy đơn #" + orderId + ":", "Lý do hủy đơn", NotificationDialog.QUESTION_MESSAGE);
            if (cancelReason == null) return;
            if (cancelReason.isBlank()) {
                NotificationDialog.showMessageDialog(this, "Vui lòng nhập lý do hủy đơn.",
                        "Thiếu lý do", NotificationDialog.WARNING_MESSAGE);
                return;
            }
            if (NotificationDialog.showConfirmDialog(this,
                    "Xác nhận hủy đơn #" + orderId + " với lý do: " + cancelReason.trim(),
                    "Xác nhận hủy đơn", NotificationDialog.YES_NO_OPTION,
                    NotificationDialog.WARNING_MESSAGE) != NotificationDialog.YES_OPTION) return;
        }
        boolean stockWillBeReturned = "CANCELLED".equals(newStatus)
                && Boolean.TRUE.equals(selectedOrder.getStockDeducted());

        try {
            orderService.updateStatus(selectedOrder, newStatus, user, cancelReason);
            selectedOrder.setStatus(newStatus);
            loadOrders();
            updateProgress();

            if ("CONFIRMED".equals(newStatus)) {
                NotificationDialog.showMessageDialog(
                        this,
                        "Đã duyệt đơn, chuyển sang pha chế và trừ số lượng trong kho.");
            } else {
                NotificationDialog.showMessageDialog(
                        this,
                        "Đã hủy đơn #" + orderId
                                + (stockWillBeReturned ? ". Số lượng đã trừ được hoàn lại kho."
                                        : ". Đơn đang chờ duyệt nên không có hàng cần hoàn kho."));
            }
        } catch (OrderService.OrderExpiredException expired) {
            loadOrders();
            NotificationDialog.showMessageDialog(this, expired.getMessage(),
                    "Đơn đã hết hạn", NotificationDialog.INFORMATION_MESSAGE);
        } catch (Exception exception) {
            loadOrders();
            showError("Không thể cập nhật đơn hàng", exception);
        }
    }

    private void updateSelectedOrderInfo() {
        int row = table.getSelectedRow();
        if (row < 0) {
            selectedOrder = null;
            progress.setValue(0);
            progress.setString("Chọn đơn hàng");
            return;
        }
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
                ? "PENDING"
                : selectedOrder.getStatus().toUpperCase(Locale.ROOT)) {
            case "PENDING" -> 0;
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
        if (!"PENDING".equalsIgnoreCase(selectedOrder.getStatus())) {
            NotificationDialog.showMessageDialog(
                    this,
                    "Đơn đã được duyệt nên không thể sửa order. Chỉ có thể thanh toán.");
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
                case "PENDING", "CONFIRMED" -> List.of("Chi tiết");
                case "COMPLETED" -> List.of("Chi tiết", "In hóa đơn");
                default -> List.of();
            };
        }
        return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "PENDING" -> List.of("Duyệt đơn", "Sửa order", "Hủy đơn");
            case "CONFIRMED" -> List.of("Thanh toán", "Hủy đơn");
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
            case "Duyệt đơn" -> processSelectedStatus("CONFIRMED");
            case "Hủy đơn" -> processSelectedStatus("CANCELLED");
            case "Sửa order" -> editSelectedOrder();
            case "Thanh toán" -> completeSelectedOrder();
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
                    case "Duyệt đơn" -> UITheme.Action.CONFIRM;
                    case "Sửa order" -> UITheme.Action.EDIT;
                    case "Thanh toán" -> UITheme.Action.PAYMENT;
                    case "Hủy đơn" -> UITheme.Action.DESTRUCTIVE;
                    case "In hóa đơn" -> UITheme.Action.PRINT;
                    default -> UITheme.Action.NEUTRAL;
                };
                UITheme.styleButton(actionButton, action);
                actionButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
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
                new Object[] { "Đồ uống", "Đơn giá", "Số lượng", "Thành tiền" }, 0) {
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

                detailModel.addRow(new Object[] {
                        drinkName,
                        currency.format(detail.getPrice()) + " đ",
                        detail.getQuantity(),
                        currency.format(detail.getSubtotal()) + " đ"
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
        detailTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                int row = detailTable.rowAtPoint(event.getPoint());
                int column = detailTable.columnAtPoint(event.getPoint());
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
                OrderDetail existing = details.stream()
                        .filter(item -> item.getDrinkId() == drink.getId())
                        .findFirst()
                        .orElse(null);

                if (existing == null && !drink.isActive()) {
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

                if (existing == null) {

                    // QUAN TRỌNG:
                    // Dùng editingOrder thay selectedOrder
                    details.add(
                            new OrderDetail(
                                    0,
                                    editingOrder.getId(),
                                    drink.getId(),
                                    quantity,
                                    drink.getPrice()));

                } else {

                    // Nếu món đã tồn tại -> cộng thêm số lượng
                    existing.setQuantity(
                            existing.getQuantity() + quantity);

                    // Giữ nguyên giá đã chốt lúc món được thêm vào order.
                    existing.setSubtotal(existing.getQuantity() * existing.getPrice());
                }

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
                orderService.saveOrderDetails(editingOrder.getId(), details, user);

                dialog.dispose();

                loadOrders();

                NotificationDialog.showMessageDialog(
                        this,
                        "Đã lưu danh sách món trong order.");

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

    private void completeSelectedOrder() {
        if (!"STAFF".equalsIgnoreCase(user.getRole())) return;
        if (!ensureSelectedOrder())
            return;
        if (!"CONFIRMED".equalsIgnoreCase(selectedOrder.getStatus())) {
            NotificationDialog.showMessageDialog(this,
                    "Chỉ đơn đã xác nhận mới được thanh toán.",
                    "Chưa thể thanh toán", NotificationDialog.INFORMATION_MESSAGE);
            return;
        }
        try {
            List<OrderDetail> details = orderService.getDetails(selectedOrder.getId());
            Map<Integer, Drink> drinks = new java.util.HashMap<>();
            for (Drink drink : drinkService.getAll()) drinks.put(drink.getId(), drink);
            Map<Integer, String> customers = new java.util.HashMap<>();
            for (User employee : userService.getAll()) customers.put(employee.getId(), employee.getFullName());
            String paymentLabel = NotificationDialog.showPayment(this, selectedOrder, details,
                    drinks, user, customers, currency);
            if (paymentLabel == null) return;
            orderService.completePayment(selectedOrder,
                    "Tiền mặt".equals(paymentLabel) ? "CASH" : "TRANSFER", user);
            loadOrders();
            updateProgress();
            NotificationDialog.showMessageDialog(this,
                    "Thanh toán thành công. Đơn hàng đã chuyển sang trạng thái hoàn thành.");
            int print = NotificationDialog.showConfirmDialog(this,
                    "Thanh toán thành công. Bạn có muốn in hóa đơn không?",
                    "In hóa đơn", NotificationDialog.YES_NO_OPTION);
            if (print == NotificationDialog.YES_OPTION) {
                printSelectedInvoice();
            }
        } catch (Exception exception) {
            loadOrders();
            showError("Không thể hoàn tất đơn hàng", exception);
        }
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
        if (!"PENDING".equals(status)) {
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
        }
        if (!staffFields.isEmpty()) {
            infoStack.add(Box.createVerticalStrut(8));
            infoStack.add(detailsCard("Thông tin nhân viên xử lý", staffFields));
        }
        dialog.add(infoStack, BorderLayout.NORTH);

        DefaultTableModel itemsModel = new DefaultTableModel(
                new Object[] { "STT", "Tên đồ uống", "Số lượng", "Đơn giá", "Thành tiền" }, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        int totalQuantity = 0;
        for (int index = 0; index < details.size(); index++) {
            OrderDetail detail = details.get(index);
            totalQuantity += detail.getQuantity();
            itemsModel.addRow(new Object[] { index + 1,
                    drinkNames.getOrDefault(detail.getDrinkId(), "Sản phẩm không tồn tại"),
                    detail.getQuantity(), currency.format(detail.getPrice()) + " đ",
                    currency.format(detail.getSubtotal()) + " đ" });
        }
        JTable itemsTable = new JTable(itemsModel);
        itemsTable.setRowHeight(34);
        itemsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
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
        itemsTable.setRowHeight(36);
        itemsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        itemsTable.getColumnModel().getColumn(0).setPreferredWidth(55);
        itemsTable.getColumnModel().getColumn(1).setPreferredWidth(370);
        itemsTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        itemsTable.getColumnModel().getColumn(3).setPreferredWidth(135);
        itemsTable.getColumnModel().getColumn(4).setPreferredWidth(150);
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
            case "PENDING" -> UITheme.WARNING;
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
        if (!ensureSelectedOrder())
            return;
        if (!"COMPLETED".equalsIgnoreCase(selectedOrder.getStatus())) {
            NotificationDialog.showMessageDialog(this,
                    "Chỉ có thể in hóa đơn sau khi đơn đã được duyệt và thanh toán.",
                    "Chưa thể in hóa đơn", NotificationDialog.INFORMATION_MESSAGE);
            return;
        }
        try {
            List<OrderDetail> details = orderService.getDetails(selectedOrder.getId());
            StringBuilder invoice = new StringBuilder("STORE DRINK\nHÓA ĐƠN #")
                    .append(selectedOrder.getId()).append("\n")
                    .append("Khách hàng: ").append(selectedOrder.getCustomerName()).append("\n")
                    .append("Bàn: ").append(selectedOrder.getTableId() == null ? "-" : selectedOrder.getTableId())
                    .append("\n")
                    .append("Thanh toán: ").append(paymentLabel(selectedOrder.getPaymentMethod())).append("\n")
                    .append("Trạng thái: ").append(statusLabel(selectedOrder.getStatus()))
                    .append("\n------------------------------\n");
            for (OrderDetail detail : details) {
                invoice.append("Đồ uống #").append(detail.getDrinkId()).append(" x ")
                        .append(detail.getQuantity()).append(" = ")
                        .append(currency.format(detail.getSubtotal())).append(" đ\n");
            }
            invoice.append("------------------------------\nTỔNG: ")
                    .append(currency.format(selectedOrder.getTotal())).append(" đ\n");
            printText(invoice.toString());
        } catch (Exception exception) {
            showError("Không thể in hóa đơn", exception);
        }
    }

    private void printText(String text) throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0)
                return java.awt.print.Printable.NO_SUCH_PAGE;
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            int y = 40;
            for (String line : text.split("\\R")) {
                graphics.drawString(line, 40, y);
                y += 15;
            }
            return java.awt.print.Printable.PAGE_EXISTS;
        });
        if (job.printDialog())
            job.print();
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
            case "PENDING" -> "Chờ duyệt";
            case "CONFIRMED" -> "Đã duyệt - đang pha chế";
            case "COMPLETED" -> "Đã thanh toán - hoàn thành";
            case "CANCELLED" -> "Đã hủy";
            default -> "Không xác định";
        };
    }

    private String statusCode(String label) {
        return switch (label) {
            case "Chờ duyệt" -> "PENDING";
            case "Đã duyệt - đang pha chế" -> "CONFIRMED";
            case "Đã thanh toán - hoàn thành" -> "COMPLETED";
            case "Đã hủy" -> "CANCELLED";
            default -> "PENDING";
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
