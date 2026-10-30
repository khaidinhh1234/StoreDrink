package view.customer;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import view.layout.NotificationDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

import model.Drink;
import model.OrderDetail;
import model.User;
import service.DrinkService;
import service.OrderService;
import view.layout.DataSync;
import view.layout.RefreshablePanel;
import view.layout.UITheme;

public class CustomerShopPanel extends JPanel implements RefreshablePanel {
    private final User user;
    private final boolean staffTakeaway;
    private final boolean guestOrder;
    private final Integer tableId;
    private final DrinkService drinkService = new DrinkService();
    private final OrderService orderService = new OrderService();
    private final List<Drink> drinks = new ArrayList<>();
    private final List<CartItem> cart = new ArrayList<>();
    private final DefaultTableModel cartModel;
    private final JTable cartTable;
    private final JPanel drinkGrid = new JPanel(new GridLayout(0, 4, 10, 10));
    private final JLabel totalLabel = new JLabel();
    private final NumberFormat currency = UITheme.newVndFormatter();
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
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(UITheme.BACKGROUND);

        JLabel title = new JLabel(guestOrder
                ? "Đặt món tại bàn " + String.format("%02d", tableId)
                : staffTakeaway ? "Tạo đơn cho khách hàng" : "Chọn đồ uống và đặt hàng");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(UITheme.TEXT);
        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        heading.add(title, BorderLayout.CENTER);
        if (guestOrder) {
            JButton exitButton = new JButton("Thoát đặt món");
            styleActionButton(exitButton, UITheme.Action.NEUTRAL);
            exitButton.addActionListener(event -> exitGuestOrder());
            heading.add(exitButton, BorderLayout.EAST);
        }
        add(heading, BorderLayout.NORTH);

        if (staffTakeaway || guestOrder) {
            JPanel customerInfo = new JPanel(new GridLayout(1, 4, 8, 0));
            customerInfo.setBorder(panelBorder("Thông tin khách hàng"));
            customerInfo.setBackground(UITheme.CARD);
            customerNameField = new JTextField();
            customerPhoneField = new JTextField();
            customerInfo.add(new JLabel("Tên khách:"));
            customerInfo.add(customerNameField);
            customerInfo.add(new JLabel("Số điện thoại (không bắt buộc):"));
            customerInfo.add(customerPhoneField);
            UITheme.styleTree(customerInfo);
            add(customerInfo, BorderLayout.SOUTH);
        }

        JButton reloadButton = new JButton("Làm mới");
        reloadButton.addActionListener(e -> loadDrinks());
        drinkGrid.setBackground(UITheme.CARD);
        JScrollPane drinkScroll = new JScrollPane(drinkGrid);
        drinkScroll.setBorder(BorderFactory.createEmptyBorder());
        drinkScroll.getViewport().setBackground(UITheme.CARD);
        drinkScroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel drinkPanel = section("Đồ uống đang bán", drinkScroll, reloadButton);

        cartModel = tableModel(new Object[] { "Tên đồ uống", "SL", "Đơn giá", "Thành tiền" });
        cartTable = new JTable(cartModel);
        cartTable.setRowHeight(42);
        JButton removeButton = new JButton("Xóa khỏi giỏ");
        removeButton.addActionListener(e -> removeSelectedItem());
        JButton testReceiptButton = new JButton("In hóa đơn thử");
        testReceiptButton.addActionListener(e -> showReceipt(false));
        JButton payButton = new JButton("Gửi order");
        payButton.addActionListener(e -> {
            if (guestOrder) {
                sendGuestOrder();
            } else {
                sendStaffOrder();
            }
        });
        totalLabel.setFont(new Font("Arial", Font.BOLD, 16));
        totalLabel.setForeground(UITheme.TEXT);
        JPanel cartActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        cartActions.setOpaque(false);
        cartActions.add(totalLabel);
        cartActions.add(removeButton);
        cartActions.add(testReceiptButton);
        cartActions.add(payButton);
        JPanel cartPanel = section("Giỏ hàng", new JScrollPane(cartTable), cartActions);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, drinkPanel, cartPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setOpaque(false);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setDividerSize(8);
        splitPane.setBackground(UITheme.BACKGROUND);
        add(splitPane, BorderLayout.CENTER);
        UITheme.styleTree(this);
        styleActionButton(reloadButton, UITheme.Action.NEUTRAL);
        styleActionButton(removeButton, UITheme.Action.DESTRUCTIVE);
        styleActionButton(testReceiptButton, UITheme.Action.PRINT);
        styleActionButton(payButton, UITheme.Action.CREATE);
        loadDrinks();
        updateCart();
        DataSync.register(this, this);
    }

    @Override public void refreshData() { loadDrinks(); }

    private DefaultTableModel tableModel(Object[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private JPanel section(String title, JComponent content, Component... actions) {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(UITheme.CARD);
        panel.setBorder(panelBorder(title));
        if (content instanceof JScrollPane scrollPane) {
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.getViewport().setBackground(UITheme.CARD);
        }
        panel.add(content, BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        for (Component action : actions)
            footer.add(action);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private TitledBorder panelBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14),
                UITheme.MUTED);
    }

    private void styleActionButton(JButton button, UITheme.Action action) {
        UITheme.styleButton(button, action);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
    }

    private void exitGuestOrder() {
        if (!cart.isEmpty()) {
            int choice = NotificationDialog.showConfirmDialog(this,
                    "Giỏ hàng chưa gửi sẽ bị bỏ. Bạn có muốn thoát đặt món không?",
                    "Thoát đặt món", NotificationDialog.YES_NO_OPTION, NotificationDialog.WARNING_MESSAGE);
            if (choice != NotificationDialog.YES_OPTION) return;
        }
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) window.dispose();
    }

    private void loadDrinks() {
        try {
            drinks.clear();
            drinks.addAll(drinkService.getAll().stream()
                    .filter(drink -> drink.isActive() && drink.getQuantity() > 0)
                    .collect(Collectors.toList()));
            drinkGrid.removeAll();
            if (drinks.isEmpty()) {
                JLabel empty = new JLabel("Hiện chưa có đồ uống đang bán", SwingConstants.CENTER);
                empty.setForeground(UITheme.MUTED);
                drinkGrid.add(empty);
            } else {
                for (Drink drink : drinks) {
                    drinkGrid.add(createDrinkCard(drink));
                }
            }
            drinkGrid.revalidate();
            drinkGrid.repaint();
        } catch (Exception exception) {
            showError("Không tải được đồ uống", exception);
        }
    }

    private JPanel createDrinkCard(Drink drink) {
        JPanel card = new JPanel(new BorderLayout(6, 8));
        card.setBackground(UITheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        card.setPreferredSize(new Dimension(0, 235));

        DrinkVisual image = new DrinkVisual();
        image.setPreferredSize(new Dimension(0, 118));
        image.setDrink(drink);
        JLabel stock = new JLabel(drink.getQuantity() + "", SwingConstants.CENTER);
        stock.setOpaque(true);
        stock.setBackground(new Color(255, 243, 205));
        stock.setForeground(new Color(133, 100, 4));
        stock.setFont(new Font("Segoe UI", Font.BOLD, 10));
        stock.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));

        JPanel imageHolder = new JPanel(null) {
            @Override
            public void doLayout() {
                image.setBounds(0, 0, getWidth(), getHeight());
                stock.setBounds(Math.max(0, getWidth() - stock.getPreferredSize().width - 6),
                        6, stock.getPreferredSize().width, stock.getPreferredSize().height);
            }
        };
        imageHolder.setOpaque(false);
        imageHolder.setPreferredSize(new Dimension(0, 118));
        imageHolder.add(image);
        imageHolder.add(stock);
        imageHolder.setComponentZOrder(stock, 0);
        card.add(imageHolder, BorderLayout.NORTH);

        JPanel details = new JPanel(new GridLayout(0, 1, 2, 2));
        details.setOpaque(false);
        JLabel name = new JLabel("<html><div style='text-align:center'>"
                + escapeHtml(drink.getName()) + "</div></html>", SwingConstants.CENTER);
        name.setForeground(UITheme.TEXT);
        name.setFont(new Font("Segoe UI", Font.BOLD, 13));
        JLabel price = new JLabel(formatMoney(drink.getPrice()) + " đ", SwingConstants.CENTER);
        price.setForeground(UITheme.SUCCESS);
        price.setFont(new Font("Segoe UI", Font.BOLD, 14));
        details.add(name);
        details.add(price);
        card.add(details, BorderLayout.CENTER);

        JButton addButton = new JButton("Thêm vào giỏ");
        styleActionButton(addButton, UITheme.Action.CREATE);
        addButton.setFont(new Font("Segoe UI", Font.BOLD, 11));
        addButton.setBorder(BorderFactory.createEmptyBorder(7, 4, 7, 4));
        addButton.addActionListener(event -> addDrinkToCart(drink));
        card.add(addButton, BorderLayout.SOUTH);
        return card;
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void addDrinkToCart(Drink drink) {
        CartItem item = cart.stream().filter(entry -> entry.drink.getId() == drink.getId())
                .findFirst().orElse(null);
        if (item == null)
            cart.add(new CartItem(drink, 1));
        else if (item.quantity < drink.getQuantity())
            item.quantity++;
        else {
            showMessage("Số lượng trong giỏ không được vượt quá tồn kho.");
            return;
        }
        updateCart();
    }

    private void removeSelectedItem() {
        int row = cartTable.getSelectedRow();
        if (row >= 0)
            cart.remove(row);
        updateCart();
    }

    private void updateCart() {
        cartModel.setRowCount(0);
        for (CartItem item : cart) {
            cartModel.addRow(new Object[] { item.drink.getName(), item.quantity,
                    formatMoney(item.drink.getPrice()), formatMoney(item.subtotal()) });
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
            NotificationDialog.showMessageDialog(this, new JScrollPane(receiptArea),
                    "Hóa đơn thử", NotificationDialog.INFORMATION_MESSAGE);
        }
    }

    private void sendStaffOrder() {
        if (cart.isEmpty()) {
            showMessage("Giỏ hàng đang trống.");
            return;
        }
        try {
            if (!validateCartAvailability()) return;
            List<OrderDetail> details = cart.stream()
                    .map(item -> new OrderDetail(0, 0, item.drink.getId(), item.quantity, item.drink.getPrice()))
                    .collect(Collectors.toList());
            String customerName = staffTakeaway ? customerNameField.getText().trim() : user.getFullName();
            String customerPhone = staffTakeaway ? customerPhoneField.getText().trim() : null;
            if (customerName.isEmpty()) {
                showMessage("Hãy nhập tên khách hàng.");
                return;
            }

            var order = orderService.createOrder(user.getId(), null, customerName, customerPhone,
                    total(), "UNPAID", "TAKEAWAY", details);
            receiptArea.setText(buildReceipt("ĐÃ GỬI ORDER - CHỜ XÁC NHẬN - MÃ ĐƠN " + order.getId()));
            NotificationDialog.showMessageDialog(this,
                    "Đã gửi order. Đơn hàng đang chờ xác nhận và thanh toán.",
                    "Gửi order thành công", NotificationDialog.INFORMATION_MESSAGE);
            cart.clear();
            customerNameField.setText("");
            customerPhoneField.setText("");
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
            if (!validateCartAvailability()) return;
            List<OrderDetail> details = cart.stream()
                    .map(item -> new OrderDetail(0, 0, item.drink.getId(), item.quantity, item.drink.getPrice()))
                    .collect(Collectors.toList());
            var order = orderService.createOrder(0, tableId, customerName, customerPhone,
                    total(), "UNPAID", "DINE_IN", details);
            receiptArea.setText(buildReceipt("ĐÃ GỬI ORDER - CHỜ DUYỆT - MÃ ĐƠN " + order.getId()));
            NotificationDialog.showMessageDialog(this,
                    "Đã gửi order. Nhân viên sẽ xác nhận đơn hàng của bạn.",
                    "Gửi order thành công", NotificationDialog.INFORMATION_MESSAGE);
            cart.clear();

            updateCart();
            loadDrinks();
        } catch (Exception exception) {
            showError("Không thể gửi order", exception);
        }
    }

    private boolean validateCartAvailability() throws Exception {
        var currentDrinks = drinkService.getAll().stream()
                .collect(Collectors.toMap(Drink::getId, drink -> drink));
        for (CartItem item : cart) {
            Drink current = currentDrinks.get(item.drink.getId());
            if (current == null || !current.isActive()) {
                showMessage(item.drink.getName()
                        + " đã ngừng bán. Vui lòng xóa món này khỏi giỏ hàng.");
                loadDrinks();
                return false;
            }
            if (item.quantity > current.getQuantity()) {
                showMessage(item.drink.getName() + " chỉ còn " + current.getQuantity()
                        + " cốc trong kho. Vui lòng điều chỉnh số lượng.");
                loadDrinks();
                return false;
            }
        }
        return true;
    }

    private String buildReceipt(String status) {
        StringBuilder receipt = new StringBuilder();
        receipt.append("          STORE DRINK\n");
        receipt.append("          HÓA ĐƠN\n");
        receipt.append("--------------------------------\n");
        String receiptCustomer = guestOrder || staffTakeaway
                ? customerNameField.getText().trim()
                : user.getFullName();
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
            if (pageIndex > 0)
                return java.awt.print.Printable.NO_SUCH_PAGE;
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

    private String formatMoney(double value) {
        return currency.format(value);
    }

    private void showMessage(String message) {
        NotificationDialog.showMessageDialog(this, message);
    }

    private void showError(String message, Exception exception) {
        exception.printStackTrace();
        NotificationDialog.showMessageDialog(this, message + ": " + exception.getMessage(),
                "Lỗi", NotificationDialog.ERROR_MESSAGE);
    }

    private static class CartItem {
        private final Drink drink;
        private int quantity;

        private CartItem(Drink drink, int quantity) {
            this.drink = drink;
            this.quantity = quantity;
        }

        private double subtotal() {
            return drink.getPrice() * quantity;
        }
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
                        return javax.imageio.ImageIO.read(java.net.URI.create(drink.getImage()).toURL());
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

            Color background = drink == null ? new Color(232, 240, 254) : categoryColor(drink.getCategoryId());
            g.setPaint(new GradientPaint(0, 0, background, width, height,
                    new Color(248, 249, 250)));
            g.fillRoundRect(2, 2, width - 4, height - 4, 18, 18);

            if (drink != null) {
                if (productImage != null) {
                    drawProductImage(g, productImage, 8, 8, width - 16, height - 16);
                } else {
                    drawFallbackDrink(g, drink, width);
                }
            } else {
                g.setColor(UITheme.MUTED);
                g.setFont(new Font("Arial", Font.BOLD, 12));
                g.drawString("Chưa có hình", 12, height / 2);
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
            int cupWidth = Math.min(76, Math.max(48, width / 5));
            int cupHeight = Math.min(78, Math.max(48, getHeight() - 24));
            int cupX = width / 2 - cupWidth / 2;
            int cupY = getHeight() / 2 - cupHeight / 2;
            g.setColor(new Color(255, 255, 255, 210));
            g.fillRoundRect(cupX, cupY, cupWidth, cupHeight, 16, 16);
            g.setColor(new Color(255, 255, 255, 235));
            g.fillOval(cupX - 7, cupY + 30, 22, 35);
            g.setColor(liquidColor(drink.getCategoryId()));
            g.fillRoundRect(cupX + 7, cupY + 25, cupWidth - 14, cupHeight - 30, 10, 10);
            g.setColor(new Color(95, 62, 35));
            g.setStroke(new BasicStroke(5));
            g.drawLine(width / 2 + 10, cupY - 8, width / 2 + 23, cupY + 24);
        }

        private Color categoryColor(int categoryId) {
            return switch (categoryId) {
                case 1 -> new Color(245, 230, 218);
                case 2 -> new Color(220, 241, 237);
                case 3 -> new Color(255, 239, 214);
                case 4 -> new Color(250, 228, 237);
                default -> new Color(235, 230, 248);
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
