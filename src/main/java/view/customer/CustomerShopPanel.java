package view.customer;

import model.Drink;
import model.OrderDetail;
import model.User;
import service.DrinkService;
import service.OrderService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CustomerShopPanel extends JPanel {
    private final User user;
    private final boolean staffTakeaway;
    private final boolean guestOrder;
    private final Integer tableId;
    private final DrinkService drinkService = new DrinkService();
    private final OrderService orderService = new OrderService();
    private final List<Drink> drinks = new ArrayList<>();
    private final List<CartItem> cart = new ArrayList<>();
    private final DefaultTableModel drinkModel;
    private final DefaultTableModel cartModel;
    private final JTable drinkTable;
    private final JTable cartTable;
    private final DrinkVisual drinkVisual = new DrinkVisual();
    private final JLabel totalLabel = new JLabel();
    private final NumberFormat currency = NumberFormat.getInstance(Locale.of("vi", "VN"));
    private final JTextArea receiptArea = new JTextArea();
    private JTextField customerNameField;
    private JTextField customerPhoneField;

    public CustomerShopPanel(User user) {
        this(user, false);
    }

    public CustomerShopPanel(User user, boolean staffTakeaway) {
        this(user, staffTakeaway, false, null);
    }

    public CustomerShopPanel(Integer tableId) {
        this(null, false, true, tableId);
    }

    private CustomerShopPanel(User user, boolean staffTakeaway, boolean guestOrder, Integer tableId) {
        this.user = user;
        this.staffTakeaway = staffTakeaway;
        this.guestOrder = guestOrder;
        this.tableId = tableId;
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(new Color(245, 247, 250));

        JLabel title = new JLabel(guestOrder
                ? "Đặt món tại bàn " + String.format("%02d", tableId)
                : staffTakeaway ? "Tạo đơn mang về cho khách hàng" : "Chọn đồ uống và đặt hàng");
        title.setFont(new Font("Arial", Font.BOLD, 24));
        add(title, BorderLayout.NORTH);

        if (staffTakeaway || guestOrder) {
            JPanel customerInfo = new JPanel(new GridLayout(1, 4, 8, 0));
            customerInfo.setBorder(BorderFactory.createTitledBorder("Thông tin khách hàng"));
            customerNameField = new JTextField();
            customerPhoneField = new JTextField();
            customerInfo.add(new JLabel("Tên khách:"));
            customerInfo.add(customerNameField);
            customerInfo.add(new JLabel("Số điện thoại (không bắt buộc):"));
            customerInfo.add(customerPhoneField);
            add(customerInfo, BorderLayout.SOUTH);
        }

        drinkModel = tableModel(new Object[] {"ID", "Tên đồ uống", "Giá", "Còn lại"});
        drinkTable = new JTable(drinkModel);
        drinkTable.setRowHeight(30);
        drinkTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && drinkTable.getSelectedRow() >= 0) {
                drinkVisual.setDrink(drinks.get(drinkTable.getSelectedRow()));
            }
        });
        JButton addButton = new JButton("Thêm vào giỏ");
        addButton.addActionListener(e -> addSelectedDrink());
        JButton reloadButton = new JButton("Làm mới");
        reloadButton.addActionListener(e -> loadDrinks());
        JPanel drinkContent = new JPanel(new BorderLayout(8, 8));
        drinkContent.add(drinkVisual, BorderLayout.NORTH);
        drinkContent.add(new JScrollPane(drinkTable), BorderLayout.CENTER);
        JPanel drinkPanel = section("Đồ uống đang bán", drinkContent,
                addButton, reloadButton);

        cartModel = tableModel(new Object[] {"Tên đồ uống", "SL", "Đơn giá", "Thành tiền"});
        cartTable = new JTable(cartModel);
        cartTable.setRowHeight(30);
        JButton removeButton = new JButton("Xóa khỏi giỏ");
        removeButton.addActionListener(e -> removeSelectedItem());
        JButton testReceiptButton = new JButton("In hóa đơn thử");
        testReceiptButton.addActionListener(e -> showReceipt(false));
        JButton payButton = new JButton(guestOrder ? "Gửi order" : "Thanh toán và in hóa đơn");
        payButton.addActionListener(e -> {
            if (guestOrder) {
                sendGuestOrder();
            } else {
                payAndPrint();
            }
        });
        totalLabel.setFont(new Font("Arial", Font.BOLD, 16));
        JPanel cartActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        cartActions.setOpaque(false);
        cartActions.add(totalLabel);
        cartActions.add(removeButton);
        cartActions.add(testReceiptButton);
        cartActions.add(payButton);
        JPanel cartPanel = section("Giỏ hàng", new JScrollPane(cartTable), cartActions);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, drinkPanel, cartPanel);
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);
        loadDrinks();
        updateCart();
    }

    private DefaultTableModel tableModel(Object[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private JPanel section(String title, JComponent content, Component... actions) {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(content, BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        for (Component action : actions) footer.add(action);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private void loadDrinks() {
        try {
            drinks.clear();
            drinks.addAll(drinkService.getAll().stream()
                    .filter(drink -> drink.isActive() && drink.getQuantity() > 0)
                    .collect(Collectors.toList()));
            drinkModel.setRowCount(0);
            for (Drink drink : drinks) {
                drinkModel.addRow(new Object[] {drink.getId(), drink.getName(),
                        formatMoney(drink.getPrice()), drink.getQuantity()});
            }
            if (!drinks.isEmpty()) {
                drinkTable.setRowSelectionInterval(0, 0);
            } else {
                drinkVisual.setDrink(null);
            }
        } catch (Exception exception) {
            showError("Không tải được đồ uống", exception);
        }
    }

    private void addSelectedDrink() {
        int row = drinkTable.getSelectedRow();
        if (row < 0) {
            showMessage("Hãy chọn một đồ uống.");
            return;
        }
        Drink drink = drinks.get(row);
        CartItem item = cart.stream().filter(entry -> entry.drink.getId() == drink.getId())
                .findFirst().orElse(null);
        if (item == null) cart.add(new CartItem(drink, 1));
        else if (item.quantity < drink.getQuantity()) item.quantity++;
        else {
            showMessage("Số lượng trong giỏ không được vượt quá tồn kho.");
            return;
        }
        updateCart();
    }

    private void removeSelectedItem() {
        int row = cartTable.getSelectedRow();
        if (row >= 0) cart.remove(row);
        updateCart();
    }

    private void updateCart() {
        cartModel.setRowCount(0);
        for (CartItem item : cart) {
            cartModel.addRow(new Object[] {item.drink.getName(), item.quantity,
                    formatMoney(item.drink.getPrice()), formatMoney(item.subtotal())});
        }
        totalLabel.setText("Tổng: " + formatMoney(total()) + " đ");
    }

    private double total() {
        return cart.stream().mapToDouble(CartItem::subtotal).sum();
    }

    private void showReceipt(boolean print) {
        if (cart.isEmpty()) {
            showMessage("Giỏ hàng đang trống.");
            return;
        }
        receiptArea.setText(buildReceipt("CHƯA THANH TOÁN"));
        receiptArea.setCaretPosition(0);
        if (print) {
            try {
                printReceipt();
            } catch (PrinterException exception) {
                showError("Không thể in hóa đơn", exception);
            }
        } else {
            JOptionPane.showMessageDialog(this, new JScrollPane(receiptArea),
                    "Hóa đơn thử", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void payAndPrint() {
        if (cart.isEmpty()) {
            showMessage("Giỏ hàng đang trống.");
            return;
        }
        String payment = (String) JOptionPane.showInputDialog(this, "Chọn phương thức thanh toán:",
                "Thanh toán", JOptionPane.PLAIN_MESSAGE, null,
                new String[] {"CASH", "TRANSFER"}, "CASH");
        if (payment == null) return;
        try {
            List<OrderDetail> details = cart.stream().map(item ->
                    new OrderDetail(0, 0, item.drink.getId(), item.quantity, item.drink.getPrice()))
                    .collect(Collectors.toList());
            String customerName = staffTakeaway ? customerNameField.getText().trim() : user.getFullName();
            String customerPhone = staffTakeaway ? customerPhoneField.getText().trim() : null;
            if (customerName.isEmpty()) {
                showMessage("Hãy nhập tên khách hàng.");
                return;
            }

            var order = orderService.createOrder(user.getId(), null, customerName, customerPhone,
                    total(), payment, staffTakeaway ? "TAKEAWAY" : "DINE_IN", details);
            for (CartItem item : cart) {
                item.drink.setQuantity(item.drink.getQuantity() - item.quantity);
                drinkService.update(item.drink);
            }
            order.setStatus("COMPLETED");
            orderService.updateStatus(order, "COMPLETED");
            receiptArea.setText(buildReceipt("ĐÃ THANH TOÁN - " + payment + " - MÃ ĐƠN " + order.getId()));
            printReceipt();
            cart.clear();
            updateCart();
            loadDrinks();
        } catch (Exception exception) {
            showError("Thanh toán thất bại", exception);
        }
    }

    private void sendGuestOrder() {
        if (cart.isEmpty()) {
            showMessage("Giỏ hàng đang trống.");
            return;
        }
        String customerName = customerNameField.getText().trim();
        String customerPhone = customerPhoneField.getText().trim();
        if (customerName.isEmpty()) {
            showMessage("Hãy nhập tên khách hàng.");
            return;
        }
        try {
            List<OrderDetail> details = cart.stream().map(item ->
                    new OrderDetail(0, 0, item.drink.getId(), item.quantity, item.drink.getPrice()))
                    .collect(Collectors.toList());
            var order = orderService.createOrder(0, tableId, customerName, customerPhone,
                    total(), "UNPAID", "DINE_IN", details);
            receiptArea.setText(buildReceipt("ĐÃ GỬI ORDER - CHỜ DUYỆT - MÃ ĐƠN " + order.getId()));
            JOptionPane.showMessageDialog(this,
                    "Đã gửi order. Nhân viên sẽ xác nhận đơn hàng của bạn.",
                    "Gửi order thành công", JOptionPane.INFORMATION_MESSAGE);
            cart.clear();
            updateCart();
            loadDrinks();
        } catch (Exception exception) {
            showError("Không thể gửi order", exception);
        }
    }

    private String buildReceipt(String status) {
        StringBuilder receipt = new StringBuilder();
        receipt.append("          STORE DRINK\n");
        receipt.append("          HÓA ĐƠN\n");
        receipt.append("--------------------------------\n");
        String receiptCustomer = guestOrder || staffTakeaway
                ? customerNameField.getText().trim() : user.getFullName();
        receipt.append("Khách hàng: ").append(receiptCustomer).append("\n");
        if (tableId != null) {
            receipt.append("Bàn: ").append(String.format("%02d", tableId)).append("\n");
        }
        receipt.append("Trạng thái: ").append(status).append("\n");
        receipt.append("--------------------------------\n");
        for (CartItem item : cart) {
            receipt.append(item.drink.getName()).append("\n")
                    .append("  ").append(item.quantity).append(" x ")
                    .append(formatMoney(item.drink.getPrice())).append(" = ")
                    .append(formatMoney(item.subtotal())).append(" đ\n");
        }
        receipt.append("--------------------------------\n");
        receipt.append("TỔNG CỘNG: ").append(formatMoney(total())).append(" đ\n");
        return receipt.toString();
    }

    private void printReceipt() throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0) return java.awt.print.Printable.NO_SUCH_PAGE;
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            int y = 40;
            for (String line : receiptArea.getText().split("\\R")) {
                graphics.drawString(line, 40, y);
                y += 15;
            }
            return java.awt.print.Printable.PAGE_EXISTS;
        });
        if (job.printDialog()) {
            job.print();
            showMessage("Đã gửi hóa đơn tới máy in.");
        }
    }

    private String formatMoney(double value) { return currency.format(value); }
    private void showMessage(String message) { JOptionPane.showMessageDialog(this, message); }
    private void showError(String message, Exception exception) {
        exception.printStackTrace();
        JOptionPane.showMessageDialog(this, message + ": " + exception.getMessage(),
                "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    private static class CartItem {
        private final Drink drink;
        private int quantity;
        private CartItem(Drink drink, int quantity) { this.drink = drink; this.quantity = quantity; }
        private double subtotal() { return drink.getPrice() * quantity; }
    }

    private static class DrinkVisual extends JPanel {
        private Drink drink;
        private BufferedImage productImage;
        private long imageRequest;

        private DrinkVisual() {
            setPreferredSize(new Dimension(0, 150));
            setOpaque(false);
        }

        private void setDrink(Drink drink) {
            this.drink = drink;
            this.productImage = null;
            long request = ++imageRequest;
            if (drink != null && drink.getImage() != null && !drink.getImage().isBlank()) {
                new SwingWorker<BufferedImage, Void>() {
                    @Override
                    protected BufferedImage doInBackground() throws IOException {
                        return javax.imageio.ImageIO.read(new URL(drink.getImage()));
                    }

                    @Override
                    protected void done() {
                        if (request != imageRequest) {
                            return;
                        }
                        try {
                            productImage = get();
                        } catch (Exception exception) {
                            productImage = null;
                        }
                        repaint();
                    }
                }.execute();
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            int width = getWidth();
            int height = getHeight();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color background = drink == null ? new Color(230, 236, 244) : categoryColor(drink.getCategoryId());
            g.setPaint(new GradientPaint(0, 0, background.brighter(), width, height, background.darker()));
            g.fillRoundRect(2, 2, width - 4, height - 4, 18, 18);

            if (drink != null) {
                if (productImage != null) {
                    drawProductImage(g, productImage, 18, 38, width - 36, 98);
                } else {
                    drawFallbackDrink(g, drink, width);
                }
                g.setColor(Color.WHITE);
                g.setFont(new Font("Arial", Font.BOLD, 16));
                g.drawString(drink.getName(), 18, 27);
            } else {
                g.setColor(new Color(70, 82, 98));
                g.setFont(new Font("Arial", Font.BOLD, 16));
                g.drawString("Chọn một món để xem hình minh họa", 18, 30);
            }
            g.dispose();
        }

        private void drawProductImage(Graphics2D g, BufferedImage image,
                int x, int y, int targetWidth, int targetHeight) {
            double scale = Math.min((double) targetWidth / image.getWidth(),
                    (double) targetHeight / image.getHeight());
            int imageWidth = (int) (image.getWidth() * scale);
            int imageHeight = (int) (image.getHeight() * scale);
            int imageX = x + (targetWidth - imageWidth) / 2;
            int imageY = y + (targetHeight - imageHeight) / 2;
            Shape oldClip = g.getClip();
            g.setClip(new RoundRectangle2D.Double(x, y, targetWidth, targetHeight, 16, 16));
            g.drawImage(image, imageX, imageY, imageWidth, imageHeight, null);
            g.setClip(oldClip);
        }

        private void drawFallbackDrink(Graphics2D g, Drink drink, int width) {
            int cupWidth = Math.min(92, Math.max(64, width / 7));
            int cupHeight = 86;
            int cupX = width / 2 - cupWidth / 2;
            int cupY = 38;
            g.setColor(new Color(255, 255, 255, 210));
            g.fillRoundRect(cupX, cupY, cupWidth, cupHeight, 16, 16);
            g.setColor(new Color(255, 255, 255, 235));
            g.fillOval(cupX - 7, cupY + 30, 22, 35);
            g.setColor(liquidColor(drink.getCategoryId()));
            g.fillRoundRect(cupX + 7, cupY + 25, cupWidth - 14, cupHeight - 30, 10, 10);
            g.setColor(new Color(95, 62, 35));
            g.setStroke(new BasicStroke(5));
            g.drawLine(width / 2 + 15, cupY - 18, width / 2 + 28, cupY + 28);
        }

        private Color categoryColor(int categoryId) {
            return switch (categoryId) {
                case 1 -> new Color(170, 120, 82);
                case 2 -> new Color(88, 174, 160);
                case 3 -> new Color(245, 166, 76);
                case 4 -> new Color(224, 126, 165);
                default -> new Color(129, 112, 196);
            };
        }

        private Color liquidColor(int categoryId) {
            return switch (categoryId) {
                case 1 -> new Color(105, 56, 30);
                case 2 -> new Color(226, 220, 157);
                case 3 -> new Color(247, 139, 39);
                case 4 -> new Color(238, 148, 184);
                default -> new Color(116, 91, 170);
            };
        }
    }
}
