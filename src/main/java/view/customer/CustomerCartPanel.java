package view.customer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.text.NumberFormat;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import model.OrderDetail;
import model.User;
import service.DrinkService;
import service.CashierRequestService;
import view.layout.NotificationDialog;
import view.layout.UITheme;
public class CustomerCartPanel extends JPanel {
    private final User user;
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Đồ uống", "Số lượng", "Đơn giá", "Thành tiền"}, 0);
    private final JTable table = new JTable(model);
    private final JLabel total = new JLabel();
    private final NumberFormat currency = UITheme.newVndFormatter();
    private final DrinkService drinks = new DrinkService();

    public CustomerCartPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        setBackground(UITheme.BACKGROUND);
        JLabel title = new JLabel("GIỎ HÀNG CỦA BẠN");
        title.setFont(new Font("Arial", Font.BOLD, 26));
        title.setForeground(UITheme.TEXT);
        add(title, BorderLayout.NORTH);
        table.setRowHeight(44);
        UITheme.styleTable(table);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton remove = new JButton("Xóa món");
        remove.addActionListener(e -> remove());
        JButton pay = new JButton("Thanh toán / Gửi yêu cầu");
        pay.addActionListener(e -> pay());
        total.setFont(new Font("Arial", Font.BOLD, 18));
        total.setForeground(UITheme.TEXT);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);
        bottom.add(total);
        bottom.add(remove);
        bottom.add(pay);
        add(bottom, BorderLayout.SOUTH);
        UITheme.styleButton(remove, UITheme.Action.DESTRUCTIVE);
        UITheme.styleButton(pay, UITheme.Action.CREATE);
        refresh();
    }

    public void refresh() {
        model.setRowCount(0);
        for (CustomerCart.Item item : CustomerCart.items()) {
            model.addRow(new Object[] {item.configurationLabel(), item.quantity(),
                    currency.format(item.unitPrice()) + " đ",
                    currency.format(item.subtotal()) + " đ"});
        }
        total.setText("Tổng: " + currency.format(CustomerCart.total()) + " đ");
    }

    private void remove() {
        int row = table.getSelectedRow();
        if (row >= 0) CustomerCart.items().remove(row);
        refresh();
    }

    private void pay() {
        if (CustomerCart.items().isEmpty()) {
            NotificationDialog.showMessageDialog(this, "Giỏ hàng đang trống.");
            return;
        }
        try {
            Map<Integer, model.Drink> currentDrinks = drinks.getAll().stream()
                    .collect(Collectors.toMap(drink -> drink.getId(), drink -> drink));
            Map<Integer, Integer> requestedQuantities = CustomerCart.items().stream()
                    .collect(Collectors.groupingBy(item -> item.drink().getId(),
                            Collectors.summingInt(CustomerCart.Item::quantity)));
            for (Map.Entry<Integer, Integer> requested : requestedQuantities.entrySet()) {
                model.Drink current = currentDrinks.get(requested.getKey());
                if (current == null || !current.isActive()) {
                    NotificationDialog.showMessageDialog(this, "Đồ uống mã " + requested.getKey()
                            + " đã ngừng bán. Vui lòng xóa món này khỏi giỏ hàng.");
                    refresh();
                    return;
                }
                if (requested.getValue() > current.getQuantity()) {
                    NotificationDialog.showMessageDialog(this, current.getName()
                            + " chỉ còn " + current.getQuantity() + " cốc trong kho.");
                    refresh();
                    return;
                }
            }
            var details = CustomerCart.items().stream()
                    .map(item -> {
                        OrderDetail detail = new OrderDetail(0, 0, item.drink().getId(),
                                item.quantity(), item.unitPrice());
                        detail.setDrinkName(item.drink().getName());
                        detail.setSize(item.size);
                        detail.setSizeExtraPrice(item.sizeExtraPrice);
                        detail.setToppings(item.toppings);
                        detail.setToppingTotal(item.toppings.stream().mapToDouble(topping -> topping.price).sum());
                        detail.setNote(item.note);
                        detail.setSubtotal(item.subtotal());
                        return detail;
                    }).toList();
            Object[] methods = {"Tiền mặt tại quầy", "Chuyển khoản QR", "Hủy"};
            int methodChoice = javax.swing.JOptionPane.showOptionDialog(this,
                    "Tổng tiền: " + currency.format(CustomerCart.total()) + " đ\nChọn phương thức thanh toán:",
                    "Thanh toán đơn hàng", javax.swing.JOptionPane.DEFAULT_OPTION,
                    javax.swing.JOptionPane.QUESTION_MESSAGE, null, methods, methods[0]);
            if (methodChoice < 0 || methodChoice == 2) return;

            String method = methodChoice == 0 ? "CASH" : "TRANSFER";
            String requestStatus = "WAITING_CASHIER";
            if ("CASH".equals(method)) {
                int confirm = javax.swing.JOptionPane.showConfirmDialog(this,
                        "Vui lòng đến quầy thu ngân để thanh toán. Gửi yêu cầu cho nhân viên?",
                        "Thanh toán tiền mặt", javax.swing.JOptionPane.YES_NO_OPTION,
                        javax.swing.JOptionPane.INFORMATION_MESSAGE);
                if (confirm != javax.swing.JOptionPane.YES_OPTION) return;
            } else {
                javax.swing.JPanel qrPanel = new javax.swing.JPanel(new java.awt.BorderLayout(8, 8));
                qrPanel.add(new javax.swing.JLabel("MÃ QR NGÂN HÀNG (mô phỏng)", javax.swing.SwingConstants.CENTER), java.awt.BorderLayout.CENTER);
                qrPanel.add(new javax.swing.JLabel("Số tiền: " + currency.format(CustomerCart.total()) + " đ",
                        javax.swing.SwingConstants.CENTER), java.awt.BorderLayout.SOUTH);
                Object[] results = {"Tôi đã chuyển khoản", "Thanh toán thất bại", "Hủy"};
                int result = javax.swing.JOptionPane.showOptionDialog(this, qrPanel, "Thanh toán bằng QR",
                        javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE,
                        null, results, results[0]);
                if (result != 0) {
                    if (result == 1) NotificationDialog.showMessageDialog(this,
                            "Thanh toán thất bại. Không tạo đơn và không trừ kho; bạn có thể thử lại.");
                    return;
                }
                requestStatus = "WAITING_CONFIRMATION";
            }

            String customerName = user.getFullName() == null || user.getFullName().isBlank()
                    ? user.getUsername() : user.getFullName().trim();
            CashierRequestService.Request request = new CashierRequestService.Request(
                    customerName, null, null, CustomerCart.total(), "TAKEAWAY", details,
                    requestStatus, LocalDateTime.now().toString(), method);
            request.customerUserId = user.getId();
            int requestId = new CashierRequestService().create(new com.google.gson.Gson().toJson(request));
            CustomerCart.items().clear();
            refresh();
            NotificationDialog.showMessage(this,
                    "Đã gửi yêu cầu TMP" + requestId + " đến nhân viên để "
                            + ("CASH".equals(method) ? "nhận tiền mặt" : "kiểm tra chuyển khoản")
                            + ". Đơn chính thức sẽ được tạo sau khi nhân viên xác nhận thanh toán.",
                    "Đã gửi yêu cầu thanh toán", NotificationDialog.Type.SUCCESS);
        } catch (Exception exception) {
            exception.printStackTrace();
            NotificationDialog.showMessageDialog(this, "Không thể gửi order: " + exception.getMessage());
        }
    }
}
