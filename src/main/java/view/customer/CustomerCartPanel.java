package view.customer;

import model.OrderDetail;
import model.User;
import service.DrinkService;
import service.OrderService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.text.NumberFormat;
import java.util.Locale;

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
        setBackground(new Color(245, 247, 250));
        JLabel title = new JLabel("GIỎ HÀNG CỦA BẠN");
        title.setFont(new Font("Arial", Font.BOLD, 26));
        add(title, BorderLayout.NORTH);
        table.setRowHeight(38);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton remove = new JButton("Xóa món");
        remove.addActionListener(e -> remove());
        JButton pay = new JButton("Thanh toán và in hóa đơn");
        pay.addActionListener(e -> pay());
        total.setFont(new Font("Arial", Font.BOLD, 18));
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
        String payment = (String) JOptionPane.showInputDialog(this, "Phương thức thanh toán:",
                "Thanh toán", JOptionPane.PLAIN_MESSAGE, null,
                new String[] {"CASH", "TRANSFER"}, "CASH");
        if (payment == null) return;
        try {
            var details = CustomerCart.items().stream()
                    .map(item -> new OrderDetail(0, 0, item.drink().getId(),
                            item.quantity(), item.drink().getPrice())).toList();
            var order = orders.createOrder(user.getId(), CustomerCart.total(), payment, details);
            for (CustomerCart.Item item : CustomerCart.items()) {
                item.drink().setQuantity(item.drink().getQuantity() - item.quantity());
                drinks.update(item.drink());
            }
            orders.updateStatus(order, "COMPLETED");
            printInvoice(order.getId(), payment);
            CustomerCart.items().clear();
            refresh();
        } catch (Exception exception) {
            exception.printStackTrace();
            JOptionPane.showMessageDialog(this, "Thanh toán thất bại: " + exception.getMessage());
        }
    }

    private void printInvoice(int orderId, String payment) throws PrinterException {
        StringBuilder receipt = new StringBuilder("STORE DRINK\nHÓA ĐƠN #" + orderId + "\n"
                + "Khách hàng: " + user.getFullName() + "\n"
                + "Thanh toán: " + payment + "\n----------------------\n");
        for (CustomerCart.Item item : CustomerCart.items()) {
            receipt.append(item.drink().getName()).append(" x ").append(item.quantity())
                    .append(" = ").append(currency.format(item.subtotal())).append(" đ\n");
        }
        receipt.append("----------------------\nTỔNG: ")
                .append(currency.format(CustomerCart.total())).append(" đ");
        String text = receipt.toString();
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0) return java.awt.print.Printable.NO_SUCH_PAGE;
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            int y = 40;
            for (String line : text.split("\\R")) { graphics.drawString(line, 40, y); y += 16; }
            return java.awt.print.Printable.PAGE_EXISTS;
        });
        if (job.printDialog()) job.print();
    }
}
