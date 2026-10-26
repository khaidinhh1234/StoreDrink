package view.staff;

import model.Order;
import model.OrderDetail;
import model.Drink;
import model.User;
import service.DrinkService;
import service.OrderService;
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
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OrderPanel extends JPanel {
    private final User user;
    private final OrderService orderService = new OrderService();
    private final DrinkService drinkService = new DrinkService();
    private final DefaultTableModel model;
    private final JTable table;
    private final NumberFormat currency = NumberFormat.getInstance(Locale.of("vi", "VN"));
    private final JLabel notification = new JLabel();
    private final JProgressBar progress = new JProgressBar(0, 3);
    private Order selectedOrder;
    private int lastPendingCount;

    public OrderPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout());
        setBackground(UITheme.BACKGROUND);

        JLabel title = new JLabel(
                "STAFF".equalsIgnoreCase(user.getRole()) ? "Vận hành đơn hàng" : "Quản lý đơn hàng");
        title.setForeground(UITheme.TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setBorder(BorderFactory.createEmptyBorder(24, 28, 12, 28));
        notification.setForeground(new Color(255, 128, 105));
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
        table.getColumnModel().getColumn(7).setPreferredWidth(230);
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
                if (column == 5 && "PENDING".equalsIgnoreCase(statusCode(
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
                boolean clickable = row >= 0 && (column == 7
                        || (column == 5 && "PENDING".equalsIgnoreCase(statusCode(
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
        table.getColumnModel().getColumn(7).setCellRenderer(new OrderActionRenderer());
        loadOrders();
        new Timer(5000, event -> loadOrders()).start();
    }

    private void loadOrders() {
        try {
            List<Order> orders = "CUSTOMER".equalsIgnoreCase(user.getRole())
                    ? orderService.getForUser(user.getId())
                    : orderService.getAll();
            orders.sort(Comparator
                    .comparing((Order order) -> !"PENDING".equalsIgnoreCase(order.getStatus()))
                    .thenComparing(Order::getCreatedAt,
                            Comparator.nullsFirst(Comparator.reverseOrder())));
            int pendingCount = (int) orders.stream().filter(order -> "PENDING".equals(order.getStatus())).count();
            if (pendingCount > lastPendingCount && lastPendingCount >= 0) {
                notification.setText("Có " + pendingCount + " order đang chờ duyệt");
            } else if (pendingCount == 0) {
                notification.setText("");
            }
            lastPendingCount = pendingCount;
            Integer selectedId = selectedOrder == null ? null : selectedOrder.getId();
            model.setRowCount(0);
            for (Order order : orders) {
                model.addRow(new Object[] {
                        order.getId(),
                        "TAKEAWAY".equalsIgnoreCase(order.getOrderType()) ? "Nhân viên Order" : "TẠI BÀN",
                        order.getCustomerName() == null ? order.getUserId() : order.getCustomerName(),
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
        } catch (Exception exception) {
            exception.printStackTrace();
            JOptionPane.showMessageDialog(this, "Không tải được danh sách đơn hàng: "
                    + exception.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateSelectedStatus() {
        if ("CUSTOMER".equalsIgnoreCase(user.getRole())) {
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
                JOptionPane.showMessageDialog(
                        this,
                        "Đơn này đã " + statusLabel(current) + ". Không thể cập nhật trạng thái bằng nút này.",
                        "Không thể cập nhật",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            default -> {
                JOptionPane.showMessageDialog(
                        this,
                        "Trạng thái đơn không hợp lệ: " + current,
                        "Lỗi dữ liệu",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        String choice = (String) JOptionPane.showInputDialog(
                this,
                "Thao tác với đơn #" + selectedOrder.getId() + ":",
                "Xử lý đơn hàng",
                JOptionPane.PLAIN_MESSAGE,
                null,
                statusLabels,
                statusLabels[0]);

        if (choice == null) {
            return;
        }
        String newStatus = "Duyệt đơn".equals(choice) ? "CONFIRMED" : "CANCELLED";
        boolean stockWillBeReturned = "CANCELLED".equals(newStatus)
                && "CONFIRMED".equalsIgnoreCase(selectedOrder.getStatus())
                && Boolean.TRUE.equals(selectedOrder.getStockDeducted());

        try {
            if ("CONFIRMED".equals(newStatus)) {
                selectedOrder.setProcessedByUserId(user.getId());
            }
            orderService.updateStatus(selectedOrder, newStatus);
            selectedOrder.setStatus(newStatus);
            loadOrders();
            updateProgress();

            if ("CONFIRMED".equals(newStatus)) {
                JOptionPane.showMessageDialog(
                        this,
                        "Đã duyệt đơn, chuyển sang pha chế và trừ số lượng trong kho.");
            } else {
                JOptionPane.showMessageDialog(
                        this,
                        "Đã hủy đơn #" + selectedOrder.getId()
                                + (stockWillBeReturned ? ". Số lượng đã trừ được hoàn lại kho."
                                        : ". Đơn đang chờ duyệt nên không có hàng cần hoàn kho."));
            }
        } catch (Exception exception) {
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
        if (!ensureSelectedOrder())
            return;
        if (!"PENDING".equalsIgnoreCase(selectedOrder.getStatus())) {
            JOptionPane.showMessageDialog(
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
        return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "PENDING" -> List.of("Sửa order");
            case "CONFIRMED" -> List.of("Thanh toán");
            case "COMPLETED" -> List.of("In hóa đơn");
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
            case "Thanh toán" -> completeSelectedOrder();
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
            styleQuantityButton(minus, new Color(220, 78, 79));
            styleQuantityButton(plus, new Color(22, 172, 133));
            quantity.setOpaque(true);
            quantity.setBackground(new Color(31, 33, 58));
            quantity.setForeground(UITheme.TEXT);
            quantity.setFont(new Font("Segoe UI", Font.BOLD, 13));
            add(minus);
            add(quantity);
            add(plus);
        }

        private void styleQuantityButton(JButton button, Color color) {
            button.setBackground(color);
            button.setForeground(Color.WHITE);
            button.setFont(new Font("Segoe UI", Font.BOLD, 16));
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        }

        @Override
        public Component getTableCellRendererComponent(JTable source, Object value,
                boolean selected, boolean focused, int row, int column) {
            quantity.setText(value == null ? "0" : value.toString());
            setBackground(selected ? new Color(34, 43, 78) : UITheme.CARD);
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
            setBackground(selected ? new Color(34, 43, 78) : UITheme.CARD);
            removeAll();
            for (String label : labels) {
                JButton actionButton = new JButton(label);
                actionButton.setFocusPainted(false);
                actionButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
                actionButton.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                actionButton.setBackground(UITheme.PRIMARY);
                actionButton.setForeground(Color.WHITE);
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
            JOptionPane.showMessageDialog(this, "Không xác định được order đang sửa.");
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
        List<OrderDetail> removedDetails = new ArrayList<>();

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
        UITheme.styleButton(add);
        UITheme.styleButton(save);

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
                    Drink drink = drinkMap.get(detail.getDrinkId());
                    if (drink == null || detail.getQuantity() >= drink.getQuantity()) {
                        JOptionPane.showMessageDialog(dialog,
                                drink == null ? "Không tìm thấy thông tin tồn kho của món này."
                                        : "Số lượng không được vượt tồn kho (" + drink.getQuantity() + " cốc).",
                                "Đã đạt giới hạn tồn kho", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    detail.setQuantity(detail.getQuantity() + 1);
                } else if (detail.getQuantity() > 1) {
                    detail.setQuantity(detail.getQuantity() - 1);
                } else {
                    if (details.size() <= 1) {
                        JOptionPane.showMessageDialog(dialog,
                                "Order phải còn ít nhất 1 đồ uống. Hãy thêm món khác trước khi xóa món này.",
                                "Không thể xóa món cuối cùng", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    int confirmation = JOptionPane.showConfirmDialog(dialog,
                            "Bạn có muốn xóa " + drinkName(detail, drinkMap) + " khỏi order không?",
                            "Xác nhận xóa món", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (confirmation != JOptionPane.YES_OPTION) {
                        return;
                    }
                    details.remove(row);
                    if (detail.getId() != 0) {
                        removedDetails.add(detail);
                    }
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
                JOptionPane.showMessageDialog(
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
                    JOptionPane.showMessageDialog(dialog,
                            drink.getName() + " đã ngừng bán và không thể thêm vào order mới.",
                            "Đồ uống đã ngừng bán", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                int alreadyInOrder = existing == null ? 0 : existing.getQuantity();
                int available = drink.getQuantity() - alreadyInOrder;
                if (quantity > available) {
                    JOptionPane.showMessageDialog(dialog,
                            "Tồn kho còn " + Math.max(0, available)
                                    + " cốc cho món này. Vui lòng giảm số lượng.",
                            "Vượt tồn kho", JOptionPane.INFORMATION_MESSAGE);
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

                JOptionPane.showMessageDialog(
                        dialog,
                        "Đã thêm "
                                + quantity
                                + " x "
                                + drink.getName());

            } catch (NumberFormatException exception) {

                JOptionPane.showMessageDialog(
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
                    JOptionPane.showMessageDialog(dialog,
                            "Order phải có ít nhất 1 đồ uống.",
                            "Chưa thể lưu order", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                Map<Integer, Drink> currentDrinks = drinkService.getAll().stream()
                        .collect(Collectors.toMap(Drink::getId, drink -> drink));
                for (OrderDetail detail : details) {
                    Drink currentDrink = currentDrinks.get(detail.getDrinkId());
                    if (currentDrink == null || detail.getQuantity() > currentDrink.getQuantity()
                            || (detail.getId() == 0 && !currentDrink.isActive())) {
                        String itemName = currentDrink == null ? drinkName(detail, drinkMap)
                                : currentDrink.getName();
                        int stock = currentDrink == null ? 0 : currentDrink.getQuantity();
                        String message = currentDrink != null && !currentDrink.isActive()
                                ? itemName + " đã ngừng bán nên không thể thêm vào order mới."
                                : itemName + " chỉ còn " + stock + " cốc trong kho. Hãy điều chỉnh số lượng.";
                        JOptionPane.showMessageDialog(dialog, message,
                                "Không thể lưu order", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                }

                // Nếu đang sửa số lượng trực tiếp trên JTable
                if (detailTable.isEditing()) {
                    detailTable.getCellEditor().stopCellEditing();
                }

                // -----------------------------
                // XÓA MÓN
                // -----------------------------
                for (OrderDetail detail : removedDetails) {

                    orderService.deleteDetail(detail);
                }

                // -----------------------------
                // THÊM / CẬP NHẬT MÓN
                // -----------------------------
                for (int row = 0; row < details.size(); row++) {

                    Object value = detailModel.getValueAt(row, 2);

                    int quantity = Integer.parseInt(
                            String.valueOf(value));

                    if (quantity <= 0) {
                        throw new NumberFormatException();
                    }

                    OrderDetail detail = details.get(row);

                    detail.setQuantity(quantity);

                    // Món mới
                    if (detail.getId() == 0) {

                        orderService.addDetail(detail);

                    }

                    // Món cũ
                    else {

                        orderService.updateDetail(detail);
                    }
                }

                // Tính lại tổng tiền
                recalculateTotal(editingOrder);

                dialog.dispose();

                loadOrders();

                JOptionPane.showMessageDialog(
                        this,
                        "Đã lưu danh sách món trong order.");

            } catch (NumberFormatException exception) {

                JOptionPane.showMessageDialog(
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
        detailTable.getColumnModel().getColumn(2).setCellRenderer(new QuantityCellRenderer());
        dialog.setSize(840, 520);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void recalculateTotal(Order order) throws Exception {

        List<OrderDetail> details = orderService.getDetails(order.getId());

        double total = details.stream()
                .mapToDouble(OrderDetail::getSubtotal)
                .sum();

        order.setTotal(total);

        orderService.updateOrder(order);
    }

    private void completeSelectedOrder() {
        if (!ensureSelectedOrder())
            return;
        if (!"CONFIRMED".equalsIgnoreCase(selectedOrder.getStatus())) {
            JOptionPane.showMessageDialog(this,
                    "Chỉ đơn đã xác nhận mới được thanh toán.",
                    "Chưa thể thanh toán", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String paymentLabel = (String) JOptionPane.showInputDialog(
                this,
                "Chọn phương thức thanh toán:",
                "Thanh toán đơn #" + selectedOrder.getId(),
                JOptionPane.PLAIN_MESSAGE,
                null,
                new String[] { "Tiền mặt", "Chuyển khoản" },
                "Tiền mặt");
        if (paymentLabel == null)
            return;
        try {
            orderService.completePayment(selectedOrder,
                    "Tiền mặt".equals(paymentLabel) ? "CASH" : "TRANSFER");
            loadOrders();
            updateProgress();
            JOptionPane.showMessageDialog(this,
                    "Thanh toán thành công. Đơn hàng đã chuyển sang trạng thái hoàn thành.");
            int print = JOptionPane.showConfirmDialog(this,
                    "Thanh toán thành công. Bạn có muốn in hóa đơn không?",
                    "In hóa đơn", JOptionPane.YES_NO_OPTION);
            if (print == JOptionPane.YES_OPTION) {
                printSelectedInvoice();
            }
        } catch (Exception exception) {
            showError("Không thể hoàn tất đơn hàng", exception);
        }
    }

    private void printSelectedInvoice() {
        if (!ensureSelectedOrder())
            return;
        if (!"COMPLETED".equalsIgnoreCase(selectedOrder.getStatus())) {
            JOptionPane.showMessageDialog(this,
                    "Chỉ có thể in hóa đơn sau khi đơn đã được duyệt và thanh toán.",
                    "Chưa thể in hóa đơn", JOptionPane.INFORMATION_MESSAGE);
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
            JOptionPane.showMessageDialog(this, "Hãy chọn một đơn hàng.");
            return false;
        }
        return true;
    }

    private void showError(String message, Exception exception) {
        exception.printStackTrace();
        JOptionPane.showMessageDialog(this, message + ": " + exception.getMessage(),
                "Lỗi", JOptionPane.ERROR_MESSAGE);
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
