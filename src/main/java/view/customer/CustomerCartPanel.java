package view.customer;

import model.OrderDetail;
import model.User;
import service.DrinkService;
import service.OrderService;
import view.layout.UITheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomerCartPanel extends JPanel {
    private final User user;
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Đồ uống", "Số lượng", "Đơn giá", "Thành tiền"}, 0);
    private final JTable table = new JTable(model);
    private final JLabel total = new JLabel();
    private final NumberFormat currency = NumberFormat.getInstance(Locale.of("vi", "VN"));
    private final OrderService orders = new OrderService();
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
        JButton pay = new JButton("Gửi order chờ duyệt");
        pay.addActionListener(e -> pay());
        total.setFont(new Font("Arial", Font.BOLD, 18));
        total.setForeground(UITheme.TEXT);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);
        bottom.add(total);
        bottom.add(remove);
        bottom.add(pay);
        add(bottom, BorderLayout.SOUTH);
        refresh();
    }

    public void refresh() {
        model.setRowCount(0);
        for (CustomerCart.Item item : CustomerCart.items()) {
            model.addRow(new Object[] {item.drink().getName(), item.quantity(),
                    currency.format(item.drink().getPrice()) + " đ",
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
            JOptionPane.showMessageDialog(this, "Giỏ hàng đang trống.");
            return;
        }
        try {
            Map<Integer, model.Drink> currentDrinks = drinks.getAll().stream()
                    .collect(Collectors.toMap(drink -> drink.getId(), drink -> drink));
            for (CustomerCart.Item item : CustomerCart.items()) {
                model.Drink current = currentDrinks.get(item.drink().getId());
                if (current == null || !current.isActive()) {
                    JOptionPane.showMessageDialog(this, item.drink().getName()
                            + " đã ngừng bán. Vui lòng xóa món này khỏi giỏ hàng.");
                    refresh();
                    return;
                }
                if (item.quantity() > current.getQuantity()) {
                    JOptionPane.showMessageDialog(this, item.drink().getName()
                            + " chỉ còn " + current.getQuantity() + " cốc trong kho.");
                    refresh();
                    return;
                }
            }
            var details = CustomerCart.items().stream()
                    .map(item -> new OrderDetail(0, 0, item.drink().getId(),
                            item.quantity(), item.drink().getPrice())).toList();
            orders.createOrder(user.getId(), CustomerCart.total(), "UNPAID", details);
            CustomerCart.items().clear();
            refresh();
            JOptionPane.showMessageDialog(this,
                    "Order đã được gửi và đang chờ nhân viên duyệt. Chưa trừ tồn kho.",
                    "Đã gửi order", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception exception) {
            exception.printStackTrace();
            JOptionPane.showMessageDialog(this, "Không thể gửi order: " + exception.getMessage());
        }
    }
}
