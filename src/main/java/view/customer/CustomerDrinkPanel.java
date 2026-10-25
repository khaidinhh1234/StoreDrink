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
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

import model.Drink;
import service.DrinkService;
import view.layout.UITheme;

public class CustomerDrinkPanel extends JPanel {
    private final DrinkService service = new DrinkService();
    private final JPanel grid = new JPanel(new GridLayout(0, 4, 14, 14));
    private final NumberFormat currency = NumberFormat.getInstance(Locale.of("vi", "VN"));
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
        loadDrinks();
    }

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
            JOptionPane.showMessageDialog(this, "Không tải được đồ uống: " + exception.getMessage());
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
        price.setForeground(new Color(64, 203, 176));
        price.setFont(new Font("Arial", Font.BOLD, 15));
        JButton add = new JButton("THÊM VÀO GIỎ");
        add.addActionListener(e -> {
            CustomerCart.add(drink);
            updateCartCount();
            JOptionPane.showMessageDialog(this, "Đã thêm " + drink.getName() + " vào giỏ.");
        });

        JPanel info = new JPanel(new GridLayout(0, 1, 2, 2));
        info.setOpaque(false);
        info.add(name);
        info.add(price);
        info.add(add);
        card.add(info, BorderLayout.SOUTH);
        return card;
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
