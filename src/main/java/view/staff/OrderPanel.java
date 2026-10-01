package view.staff;

import model.Order;
import model.OrderDetail;
import model.Drink;
import model.User;
import service.DrinkService;
import service.OrderService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Locale;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.Comparator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.Timer;

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
        setBackground(new Color(245, 247, 250));

        JLabel title = new JLabel(
                "STAFF".equalsIgnoreCase(user.getRole()) ? "Vận hành đơn hàng" : "Quản lý đơn hàng");
        title.setFont(new Font("Arial", Font.BOLD, 24));
        title.setBorder(BorderFactory.createEmptyBorder(20, 20, 12, 20));
        notification.setForeground(new Color(180, 55, 35));
        notification.setBorder(BorderFactory.createEmptyBorder(0, 20, 8, 20));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(title, BorderLayout.NORTH);
        heading.add(notification, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);

        model = new DefaultTableModel(
                new Object[] {"Mã đơn", "Loại đơn", "Khách hàng", "Bàn", "Tổng tiền",
                        "Trạng thái", "Ngày tạo"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(model);
        table.setRowHeight(32);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateSelectedOrderInfo();
        });

        JButton refreshButton = new JButton("Làm mới");
        JButton editButton = new JButton("Sửa order");
        JButton statusButton = new JButton("Cập nhật trạng thái");
        JButton completeButton = new JButton("Thanh toán đơn");
        JButton printButton = new JButton("In hóa đơn");
        statusButton.setVisible(!"CUSTOMER".equalsIgnoreCase(user.getRole()));
        refreshButton.addActionListener(e -> loadOrders());
        statusButton.addActionListener(e -> updateSelectedStatus());
        editButton.addActionListener(e -> editSelectedOrder());
        completeButton.addActionListener(e -> completeSelectedOrder());
        printButton.addActionListener(e -> printSelectedInvoice());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        progress.setStringPainted(true);
        progress.setString("Chọn đơn hàng");
        actions.add(progress);
        actions.add(editButton);
        actions.add(statusButton);
        actions.add(completeButton);
        actions.add(printButton);
        actions.add(refreshButton);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        add(actions, BorderLayout.SOUTH);
        add(scrollPane, BorderLayout.CENTER);
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
            model.setRowCount(0);
            for (Order order : orders) {
                model.addRow(new Object[] {
                        order.getId(),
                        "TAKEAWAY".equalsIgnoreCase(order.getOrderType()) ? "MANG VỀ" : "TẠI BÀN",
                        order.getCustomerName() == null ? order.getUserId() : order.getCustomerName(),
                        order.getTableId() == null ? "-" : String.format("%02d", order.getTableId()),
                        currency.format(order.getTotal()) + " đ",
                        statusLabel(order.getStatus()),
                        formatDateTime(order.getCreatedAt())
                });
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            JOptionPane.showMessageDialog(this, "Không tải được danh sách đơn hàng: "
                    + exception.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateSelectedStatus() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Hãy chọn một đơn hàng.");
            return;
        }
        if ("Đã thanh toán - hoàn thành".equals(model.getValueAt(selectedRow, 5))) {
            JOptionPane.showMessageDialog(this,
                    "Đơn đã thanh toán và hoàn thành, không cần cập nhật trạng thái.");
            return;
        }
        String[] statusLabels = {"Chưa xác nhận", "Đã xác nhận", "Đã hủy"};
        String currentStatus = (String) model.getValueAt(selectedRow, 5);
        String statusLabel = (String) JOptionPane.showInputDialog(
                this, "Trạng thái mới:", "Cập nhật đơn hàng",
                JOptionPane.PLAIN_MESSAGE, null,
                statusLabels, currentStatus);
        if (statusLabel == null) {
            return;
        }
        String status = statusCode(statusLabel);
        try {
            int id = (int) model.getValueAt(selectedRow, 0);
            Order order = orderService.getAll().stream()
                    .filter(item -> item.getId() == id)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hàng"));
            orderService.updateStatus(order, status);
            selectedOrder = order;
            loadOrders();
            updateProgress();
        } catch (Exception exception) {
            exception.printStackTrace();
            JOptionPane.showMessageDialog(this, "Không thể cập nhật đơn hàng: "
                    + exception.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
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
            if (selectedOrder == null) return;
            int value = switch (selectedOrder.getStatus()) {
                case "CONFIRMED" -> 1;
                case "COMPLETED" -> 3;
                case "CANCELLED" -> 0;
                default -> 0;
            };
            progress.setValue(value);
            progress.setString(statusLabel(selectedOrder.getStatus()));
        }

    private void editSelectedOrder() {
            if (!ensureSelectedOrder()) return;
            if (!"PENDING".equalsIgnoreCase(selectedOrder.getStatus())
                    && !"CONFIRMED".equalsIgnoreCase(selectedOrder.getStatus())) {
                JOptionPane.showMessageDialog(this,
                        "Chỉ được sửa đơn đang chờ xác nhận hoặc đã xác nhận.");
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

    private void showOrderEditor(List<OrderDetail> details, List<Drink> drinks) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Sửa order #" + selectedOrder.getId(), Dialog.ModalityType.APPLICATION_MODAL);
        DefaultTableModel detailModel = new DefaultTableModel(
                new Object[] {"Đồ uống", "Đơn giá", "Số lượng", "Thành tiền"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return column == 2; }
        };
        JTable detailTable = new JTable(detailModel);
        detailTable.setRowHeight(28);
        Map<Integer, Drink> drinkMap = new java.util.HashMap<>();
        List<OrderDetail> removedDetails = new ArrayList<>();
        for (Drink drink : drinks) drinkMap.put(drink.getId(), drink);
        Runnable reload = () -> {
            detailModel.setRowCount(0);
            for (OrderDetail detail : details) {
                Drink drink = drinkMap.get(detail.getDrinkId());
                detailModel.addRow(new Object[] {
                        drink == null ? "Đồ uống #" + detail.getDrinkId() : drink.getName(),
                        currency.format(detail.getPrice()) + " đ", detail.getQuantity(),
                        currency.format(detail.getSubtotal()) + " đ"});
            }
        };
        reload.run();
        JComboBox<Drink> drinkCombo = new JComboBox<>(drinks.toArray(new Drink[0]));
        drinkCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setText(value instanceof Drink d ? d.getName() + " - " + currency.format(d.getPrice()) + " đ" : "");
                return this;
            }
        });
        JTextField quantityField = new JTextField("1", 4);
        JButton add = new JButton("Thêm món");
        JButton remove = new JButton("Bớt món / xóa");
        JButton save = new JButton("Lưu order");
        add.addActionListener(event -> {
            Drink drink = (Drink) drinkCombo.getSelectedItem();
            if (drink == null) return;
            try {
                int quantity = Integer.parseInt(quantityField.getText().trim());
                if (quantity <= 0) throw new NumberFormatException();
                OrderDetail existing = details.stream()
                        .filter(item -> item.getDrinkId() == drink.getId()).findFirst().orElse(null);
                if (existing == null) details.add(new OrderDetail(0, selectedOrder.getId(),
                        drink.getId(), quantity, drink.getPrice()));
                else {
                    existing.setQuantity(existing.getQuantity() + quantity);
                    existing.setPrice(drink.getPrice());
                }
                reload.run();
                JOptionPane.showMessageDialog(dialog,
                        "Đã thêm " + drink.getName() + ". Bấm 'Lưu order' để cập nhật đơn hàng.");
            } catch (NumberFormatException exception) {
                JOptionPane.showMessageDialog(dialog, "Số lượng phải là số dương.");
            }
        });
        remove.addActionListener(event -> {
            int row = detailTable.getSelectedRow();
            if (row < 0) return;
            OrderDetail detail = details.get(row);
            if (detail.getId() == 0) details.remove(row);
            else if (detail.getQuantity() > 1) detail.setQuantity(detail.getQuantity() - 1);
            else {
                details.remove(row);
                removedDetails.add(detail);
            }
            reload.run();
        });
        save.addActionListener(event -> {
            try {
                if (detailTable.isEditing()) {
                    detailTable.getCellEditor().stopCellEditing();
                }
                for (OrderDetail detail : removedDetails) orderService.deleteDetail(detail);
                for (int row = 0; row < details.size(); row++) {
                    Object value = detailModel.getValueAt(row, 2);
                    int quantity = Integer.parseInt(String.valueOf(value));
                    if (quantity <= 0) throw new NumberFormatException();
                    details.get(row).setQuantity(quantity);
                    if (details.get(row).getId() == 0) orderService.addDetail(details.get(row));
                    else orderService.updateDetail(details.get(row));
                }
                recalculateTotal();
                dialog.dispose();
                loadOrders();
                JOptionPane.showMessageDialog(this, "Đã lưu danh sách món trong order.");
            } catch (Exception exception) {
                showError("Không thể lưu danh sách món", exception);
            }
        });
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(drinkCombo); controls.add(new JLabel("Số lượng:"));
        controls.add(quantityField); controls.add(add); controls.add(remove); controls.add(save);
        dialog.add(new JScrollPane(detailTable), BorderLayout.CENTER);
        dialog.add(controls, BorderLayout.SOUTH);
        dialog.setSize(760, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void recalculateTotal() throws Exception {
            List<OrderDetail> details = orderService.getDetails(selectedOrder.getId());
            selectedOrder.setTotal(details.stream().mapToDouble(OrderDetail::getSubtotal).sum());
            orderService.updateOrder(selectedOrder);
        }

    private void completeSelectedOrder() {
            if (!ensureSelectedOrder()) return;
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
                    new String[] {"Tiền mặt", "Chuyển khoản"},
                    "Tiền mặt");
                if (paymentLabel == null) return;
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
            if (!ensureSelectedOrder()) return;
            try {
                List<OrderDetail> details = orderService.getDetails(selectedOrder.getId());
                StringBuilder invoice = new StringBuilder("STORE DRINK\nHÓA ĐƠN #")
                        .append(selectedOrder.getId()).append("\n")
                        .append("Khách hàng: ").append(selectedOrder.getCustomerName()).append("\n")
                        .append("Bàn: ").append(selectedOrder.getTableId() == null ? "-" : selectedOrder.getTableId()).append("\n")
                        .append("Thanh toán: ").append(paymentLabel(selectedOrder.getPaymentMethod())).append("\n")
                        .append("Trạng thái: ").append(statusLabel(selectedOrder.getStatus())).append("\n------------------------------\n");
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
                if (pageIndex > 0) return java.awt.print.Printable.NO_SUCH_PAGE;
                graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
                int y = 40;
                for (String line : text.split("\\R")) {
                    graphics.drawString(line, 40, y);
                    y += 15;
                }
                return java.awt.print.Printable.PAGE_EXISTS;
            });
            if (job.printDialog()) job.print();
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
            case "CONFIRMED" -> "Đã xác nhận";
            case "COMPLETED" -> "Đã thanh toán - hoàn thành";
            case "CANCELLED" -> "Đã hủy";
            default -> "Chưa xác nhận";
        };
    }

    private String statusCode(String label) {
        return switch (label) {
            case "Đã xác nhận" -> "CONFIRMED";
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
        if (value == null || value.isBlank()) return "-";
        try {
            return LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception exception) {
            return value;
        }
    }
}
