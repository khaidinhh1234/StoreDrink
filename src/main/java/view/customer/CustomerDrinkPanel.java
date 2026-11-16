package view.customer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.text.NumberFormat;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import model.Drink;
import model.OrderDetail;
import service.DrinkService;
import service.ToppingService;
import view.layout.DataSync;
import view.layout.NotificationDialog;
import view.layout.RefreshablePanel;
import view.layout.UITheme;
public class CustomerDrinkPanel extends JPanel implements RefreshablePanel {
    private final DrinkService service = new DrinkService();
    private final JPanel grid = new JPanel(new GridLayout(0, 4, 14, 14));
    private final NumberFormat currency = UITheme.newVndFormatter();
    private final JLabel cartCount = new JLabel();
    public CustomerDrinkPanel() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        setBackground(UITheme.BACKGROUND);

        JLabel title = new JLabel("SẢN PHẨM NỔI BẬT", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 26));
        title.setForeground(UITheme.TEXT);
        add(title, BorderLayout.NORTH);

        grid.setBackground(UITheme.BACKGROUND);
        add(new JScrollPane(grid), BorderLayout.CENTER);

        JButton refresh = new JButton("Làm mới");
        refresh.addActionListener(e -> loadDrinks());
        cartCount.setFont(new Font("Arial", Font.BOLD, 15));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);
        bottom.add(cartCount);
        bottom.add(refresh);
        add(bottom, BorderLayout.SOUTH);
        UITheme.styleButton(refresh, UITheme.Action.NEUTRAL);
        loadDrinks();
        DataSync.register(this, this);
    }

    @Override public void refreshData() { loadDrinks(); }

    private void loadDrinks() {
        try {
            List<Drink> drinks = service.getAll().stream()
                    .filter(drink -> drink.isActive() && drink.getQuantity() > 0).toList();
            grid.removeAll();
            for (Drink drink : drinks)
                grid.add(createCard(drink));
            updateCartCount();
            grid.revalidate();
            grid.repaint();
        } catch (Exception exception) {
            exception.printStackTrace();
            NotificationDialog.showMessageDialog(this, "Không tải được đồ uống: " + exception.getMessage());
        }
    }

    private JPanel createCard(Drink drink) {
        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBackground(UITheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                BorderFactory.createEmptyBorder(8, 8, 10, 8)));

        ImageLabel image = new ImageLabel(drink.getImage());
        image.setPreferredSize(new Dimension(0, 170));
        card.add(image, BorderLayout.CENTER);

        JLabel name = new JLabel(drink.getName(), SwingConstants.CENTER);
        name.setFont(new Font("Arial", Font.BOLD, 14));
        name.setForeground(UITheme.TEXT);
        JLabel price = new JLabel(currency.format(drink.getPrice()) + " đ", SwingConstants.CENTER);
        price.setForeground(UITheme.SUCCESS);
        price.setFont(new Font("Arial", Font.BOLD, 15));
        JButton add = new JButton("THÊM VÀO GIỎ");
        UITheme.styleButton(add, UITheme.Action.CREATE);
        add.addActionListener(e -> customizeAndAdd(drink));

        JPanel info = new JPanel(new GridLayout(0, 1, 2, 2));
        info.setOpaque(false);
        info.add(name);
        info.add(price);
        info.add(add);
        card.add(info, BorderLayout.SOUTH);
        return card;
    }

    private void customizeAndAdd(Drink drink) {
        try {
            List<Drink.SizeOption> sizes = drink.getAvailableSizes().stream().limit(3).toList();
            JComboBox<Drink.SizeOption> size = new JComboBox<>(sizes.toArray(Drink.SizeOption[]::new));
            List<model.Topping> available = new ToppingService().getForDrink(drink.getId()).stream().limit(3).toList();
            JPanel toppingPanel = new JPanel(new GridLayout(0, 1, 5, 5));
            List<JCheckBox> toppingChecks = new java.util.ArrayList<>();
            for (model.Topping topping : available) {
                JCheckBox check = new JCheckBox(topping.getName() + " · +" + currency.format(topping.getPrice()) + " đ");
                check.putClientProperty("topping", topping);
                toppingChecks.add(check);
                toppingPanel.add(check);
            }
            if (available.isEmpty()) toppingPanel.add(new JLabel("Món này chưa có topping."));
            JSpinner quantity = new JSpinner(new javax.swing.SpinnerNumberModel(1, 1,
                    Math.max(1, drink.getQuantity()), 1));
            JTextField note = new JTextField(22);
            JLabel total = new JLabel();
            total.setForeground(UITheme.SUCCESS);
            total.setFont(new Font("Segoe UI", Font.BOLD, 16));
            Runnable updateTotal = () -> {
                Drink.SizeOption selected = (Drink.SizeOption) size.getSelectedItem();
                double unitPrice = selected == null ? drink.getPrice() : selected.getPrice();
                for (JCheckBox check : toppingChecks) if (check.isSelected())
                    unitPrice += ((model.Topping) check.getClientProperty("topping")).getPrice();
                total.setText("Thành tiền: " + currency.format(unitPrice * (Integer) quantity.getValue()) + " đ");
            };
            size.addActionListener(event -> updateTotal.run());
            quantity.addChangeListener(event -> updateTotal.run());
            for (JCheckBox check : toppingChecks) check.addActionListener(event -> updateTotal.run());
            updateTotal.run();

            JPanel form = new JPanel(new GridLayout(0, 1, 8, 8));
            form.add(new JLabel("Chọn size · giá Size S: " + currency.format(drink.getPrice()) + " đ"));
            form.add(size);
            form.add(new JLabel("Topping (tối đa 3)"));
            form.add(new JScrollPane(toppingPanel));
            form.add(new JLabel("Số lượng"));
            form.add(quantity);
            form.add(new JLabel("Ghi chú món"));
            form.add(note);
            form.add(total);
            int choice = javax.swing.JOptionPane.showConfirmDialog(this, form,
                    "Tùy chỉnh " + drink.getName(), javax.swing.JOptionPane.OK_CANCEL_OPTION,
                    javax.swing.JOptionPane.PLAIN_MESSAGE);
            if (choice != javax.swing.JOptionPane.OK_OPTION) return;

            Drink.SizeOption selected = (Drink.SizeOption) size.getSelectedItem();
            CustomerCart.Item item = new CustomerCart.Item(drink, (Integer) quantity.getValue());
            item.size = selected == null ? "S" : selected.getName();
            item.sizeExtraPrice = (selected == null ? drink.getPrice() : selected.getPrice()) - drink.getPrice();
            item.note = note.getText().trim();
            for (JCheckBox check : toppingChecks) if (check.isSelected()) {
                model.Topping topping = (model.Topping) check.getClientProperty("topping");
                item.toppings.add(new OrderDetail.ToppingSnapshot(topping.getId(), topping.getName(), topping.getPrice()));
            }
            if (!CustomerCart.add(item)) {
                NotificationDialog.showMessageDialog(this, "Số lượng tổng của món vượt quá tồn kho còn lại.");
                return;
            }
            updateCartCount();
            NotificationDialog.showSuccess(this, "Đã thêm " + item.configurationLabel() + " vào giỏ.");
        } catch (Exception exception) {
            NotificationDialog.showMessageDialog(this, "Không thể tải tùy chọn món: " + exception.getMessage());
        }
    }

    private void updateCartCount() {
        int count = CustomerCart.items().stream().mapToInt(CustomerCart.Item::quantity).sum();
        cartCount.setText("Giỏ hàng: " + count + " món");
    }

    private static class ImageLabel extends JLabel {
        private BufferedImage image;
        private final String imageUrl;

        ImageLabel(String imageUrl) {
            this.imageUrl = imageUrl;
            setHorizontalAlignment(CENTER);
            setOpaque(true);
            setBackground(new Color(248, 248, 248));
            load();
        }

        private void load() {
            if (imageUrl == null || imageUrl.isBlank())
                return;
            new SwingWorker<BufferedImage, Void>() {
                protected BufferedImage doInBackground() throws Exception {
                    return javax.imageio.ImageIO.read(URI.create(imageUrl).toURL());
                }

                protected void done() {
                    try {
                        image = get();
                        repaint();
                    } catch (Exception ignored) {
                    }
                }
            }.execute();
        }

        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (image == null) {
                graphics.setColor(new Color(220, 225, 232));
                graphics.fillOval(getWidth() / 2 - 30, 35, 60, 95);
                return;
            }
            double scale = Math.min((double) getWidth() / image.getWidth(),
                    (double) getHeight() / image.getHeight());
            int width = (int) (image.getWidth() * scale);
            int height = (int) (image.getHeight() * scale);
            graphics.drawImage(image, (getWidth() - width) / 2, (getHeight() - height) / 2,
                    width, height, null);
        }
    }
}
