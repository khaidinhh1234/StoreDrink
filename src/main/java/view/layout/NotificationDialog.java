package view.layout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.text.NumberFormat;

import model.Drink;
import model.Order;
import model.OrderDetail;
import model.User;

/**
 * Shared modal UI for application messages, confirmations, forms and choices.
 */
public final class NotificationDialog {
    public static final int YES_OPTION = 0;
    public static final int NO_OPTION = 1;
    public static final int OK_OPTION = 0;
    public static final int CANCEL_OPTION = 2;
    public static final int INFORMATION_MESSAGE = 1;
    public static final int WARNING_MESSAGE = 2;
    public static final int ERROR_MESSAGE = 0;
    public static final int QUESTION_MESSAGE = 3;
    public static final int PLAIN_MESSAGE = -1;
    public static final int OK_CANCEL_OPTION = 2;
    public static final int YES_NO_OPTION = 0;

    public enum Type {
        SUCCESS("✓", "Thao tác thành công", UITheme.SUCCESS),
        ERROR("!", "Có lỗi xảy ra", UITheme.DANGER),
        WARNING("!", "Cảnh báo", UITheme.WARNING),
        INFO("i", "Thông tin", UITheme.INFO),
        CONFIRM("?", "Xác nhận", UITheme.PRIMARY);

        private final String icon;
        private final String defaultTitle;
        private final Color color;

        Type(String icon, String defaultTitle, Color color) {
            this.icon = icon;
            this.defaultTitle = defaultTitle;
            this.color = color;
        }
    }

    private NotificationDialog() {
    }

    public static void showSuccess(Component owner, String message) {
        showMessage(owner, Type.SUCCESS.defaultTitle, message, Type.SUCCESS);
    }

    public static void showError(Component owner, String message) {
        showMessage(owner, Type.ERROR.defaultTitle, message, Type.ERROR);
    }

    public static void showWarning(Component owner, String message) {
        showMessage(owner, Type.WARNING.defaultTitle, message, Type.WARNING);
    }

    public static void showInfo(Component owner, String message) {
        showMessage(owner, Type.INFO.defaultTitle, message, Type.INFO);
    }

    public static void showMessage(Component owner, String title, String message, Type type) {
        showDialog(owner, title, messageComponent(message), type, "Đóng", null, null);
    }

    public static void showMessageDialog(Component owner, Object message) {
        showMessageDialog(owner, message, "Thông tin", INFORMATION_MESSAGE);
    }

    public static void showMessageDialog(Component owner, Object message, String title, int messageType) {
        showDialog(owner, title, componentFor(message), typeFor(messageType), "Đóng", null, null);
    }

    public static int showConfirmDialog(Component owner, Object message, String title,
                                        int optionType) {
        return showConfirmDialog(owner, message, title, optionType, QUESTION_MESSAGE);
    }

    public static int showConfirmDialog(Component owner, Object message, String title,
                                        int optionType, int messageType) {
        boolean accepted = showDialog(owner, title, componentFor(message), typeFor(messageType),
                optionType == YES_NO_OPTION ? "Có" : "Đồng ý",
                optionType == YES_NO_OPTION ? "Không" : "Hủy", null);
        return accepted ? YES_OPTION : (optionType == YES_NO_OPTION ? NO_OPTION : CANCEL_OPTION);
    }

    public static Object showInputDialog(Component owner, Object message, String title,
                                         int messageType, Icon icon, Object[] options,
                                         Object initialSelectionValue) {
        String[] choices = new String[options.length];
        for (int i = 0; i < options.length; i++) {
            choices[i] = String.valueOf(options[i]);
        }
        return showChoice(owner, title, String.valueOf(message), choices,
                String.valueOf(initialSelectionValue));
    }

    public static String showInputDialog(Component owner, Object message, String title,
                                         int messageType) {
        return showInput(owner, title, String.valueOf(message));
    }

    public static boolean confirm(Component owner, String title, String message) {
        return showDialog(owner, title, messageComponent(message), Type.CONFIRM,
                "Xác nhận", "Hủy", null);
    }

    public static boolean confirmDanger(Component owner, String title, String message) {
        return showDialog(owner, title, messageComponent(message), Type.ERROR,
                "Xác nhận", "Hủy", null);
    }

    public static boolean showForm(Component owner, String title, JComponent content,
                                   String confirmLabel) {
        return showDialog(owner, title, content, Type.CONFIRM, confirmLabel, "Hủy", null);
    }

    public static String showInput(Component owner, String title, String prompt) {
        JTextField field = new JTextField();
        JPanel content = labeledContent(prompt, field);
        if (!showDialog(owner, title, content, Type.INFO, "Xác nhận", "Hủy", null)) {
            return null;
        }
        return field.getText();
    }

    public static String showChoice(Component owner, String title, String prompt,
                                    String[] choices, String selected) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        JLabel label = new JLabel(prompt);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(label);
        content.add(Box.createVerticalStrut(10));
        ButtonGroup group = new ButtonGroup();
        JRadioButton[] radios = new JRadioButton[choices.length];
        for (int i = 0; i < choices.length; i++) {
            radios[i] = new JRadioButton(choices[i]);
            radios[i].setOpaque(false);
            radios[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            radios[i].setSelected(choices[i].equals(selected));
            group.add(radios[i]);
            content.add(radios[i]);
        }
        if (!showDialog(owner, title, content, Type.CONFIRM, "Xác nhận", "Hủy", null)) {
            return null;
        }
        for (JRadioButton radio : radios) {
            if (radio.isSelected()) {
                return radio.getText();
            }
        }
        return null;
    }

    public static String showPayment(Component owner, int orderId, String total) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        JLabel order = new JLabel("Mã đơn hàng: #" + orderId);
        JLabel amount = new JLabel("Tổng thanh toán: " + total);
        amount.setFont(amount.getFont().deriveFont(Font.BOLD, 18f));
        amount.setForeground(UITheme.SUCCESS);
        JLabel prompt = new JLabel("Chọn phương thức thanh toán");
        prompt.setBorder(new EmptyBorder(18, 0, 8, 0));
        content.add(order);
        content.add(Box.createVerticalStrut(8));
        content.add(amount);
        content.add(prompt);
        ButtonGroup group = new ButtonGroup();
        JRadioButton cash = new JRadioButton("Tiền mặt", true);
        JRadioButton transfer = new JRadioButton("Chuyển khoản ngân hàng");
        cash.setOpaque(false);
        transfer.setOpaque(false);
        group.add(cash);
        group.add(transfer);
        content.add(cash);
        content.add(transfer);
        if (!showDialog(owner, "Thanh toán đơn hàng", content, Type.SUCCESS,
                "Xác nhận thanh toán", "Hủy", null)) {
            return null;
        }
        return cash.isSelected() ? "Tiền mặt" : "Chuyển khoản";
    }

    public static String showPayment(Component owner, Order order, List<OrderDetail> details,
                                     Map<Integer, Drink> drinks, User cashier,
                                     Map<Integer, String> customerNames, NumberFormat currency) {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel heading = new JLabel("THANH TOÁN ĐƠN HÀNG");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 19));
        heading.setForeground(UITheme.TEXT);
        JLabel orderCode = new JLabel("#" + order.getId());
        orderCode.setForeground(Color.WHITE);
        orderCode.setOpaque(true);
        orderCode.setBackground(UITheme.PRIMARY);
        orderCode.setBorder(new EmptyBorder(5, 10, 5, 10));
        header.add(heading, BorderLayout.WEST);
        header.add(orderCode, BorderLayout.EAST);
        content.add(header);
        content.add(sectionDivider());

        JPanel info = new JPanel(new GridLayout(0, 2, 16, 10));
        info.setOpaque(false);
        String customer = order.getCustomerName();
        if ((customer == null || customer.isBlank()) && customerNames != null) {
            customer = customerNames.get(order.getUserId());
        }
        addInfo(info, "Khách hàng", customer == null || customer.isBlank() ? "Khách lẻ" : customer);
        String type = "TAKEAWAY".equalsIgnoreCase(order.getOrderType()) ? "Mang đi" : "Tại bàn";
        if (!"TAKEAWAY".equalsIgnoreCase(order.getOrderType()) && order.getTableId() != null) {
            type += " – Bàn " + String.format("%02d", order.getTableId());
        }
        addInfo(info, "Loại đơn", type);
        addInfo(info, "Thời gian đặt", formatTime(order.getCreatedAt()));
        addInfo(info, "Nhân viên thanh toán", cashier == null ? "Tài khoản đang đăng nhập"
                : cashier.getFullName());
        content.add(info);
        content.add(sectionDivider());

        JLabel detailsTitle = sectionTitle("CHI TIẾT ĐƠN HÀNG");
        content.add(detailsTitle);
        JPanel lines = new JPanel(new GridLayout(Math.max(1, details == null ? 0 : details.size()) + 1,
                3, 8, 4));
        lines.setOpaque(false);
        addTableHeader(lines, "Tên đồ uống", SwingConstants.LEFT);
        addTableHeader(lines, "SL", SwingConstants.CENTER);
        addTableHeader(lines, "Thành tiền", SwingConstants.RIGHT);
        int quantity = 0;
        if (details != null) for (OrderDetail detail : details) {
            Drink drink = drinks == null ? null : drinks.get(detail.getDrinkId());
            JLabel name = new JLabel(drink == null ? "Sản phẩm không tồn tại" : drink.getName());
            JLabel count = new JLabel(String.valueOf(detail.getQuantity()), SwingConstants.CENTER);
            JLabel subtotal = new JLabel(currency.format(detail.getSubtotal()) + " đ", SwingConstants.RIGHT);
            for (JLabel label : new JLabel[] {name, count, subtotal}) {
                label.setForeground(UITheme.TEXT);
                label.setBorder(new EmptyBorder(5, 0, 5, 0));
                lines.add(label);
            }
            quantity += detail.getQuantity();
        }
        JScrollPane scroll = new JScrollPane(lines);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));
        scroll.setPreferredSize(new Dimension(650, Math.min(170, 34 * Math.max(2, (details == null ? 0 : details.size()) + 1))));
        content.add(scroll);

        JPanel totalPanel = new JPanel(new BorderLayout(10, 8));
        totalPanel.setOpaque(false);
        JLabel count = new JLabel("Tổng số lượng: " + quantity + " món");
        count.setForeground(UITheme.MUTED);
        JLabel total = new JLabel("TỔNG THANH TOÁN: " + currency.format(order.getTotal()) + " đ");
        total.setFont(new Font("Segoe UI", Font.BOLD, 22));
        total.setForeground(UITheme.SUCCESS);
        totalPanel.add(count, BorderLayout.NORTH);
        totalPanel.add(total, BorderLayout.SOUTH);
        content.add(totalPanel);
        content.add(sectionDivider());

        JLabel methodTitle = sectionTitle("PHƯƠNG THỨC THANH TOÁN");
        content.add(methodTitle);
        ButtonGroup group = new ButtonGroup();
        JRadioButton cash = paymentCard("Tiền mặt", "Khách hàng thanh toán trực tiếp tại quầy", true);
        JRadioButton transfer = paymentCard("Chuyển khoản ngân hàng", "Xác nhận thanh toán sau khi nhận được tiền", false);
        group.add(cash);
        group.add(transfer);
        JPanel methods = new JPanel(new GridLayout(2, 1, 0, 8));
        methods.setOpaque(false);
        methods.add(cash);
        methods.add(transfer);
        content.add(methods);

        final String[] result = {null};
        showDialog(owner, "Thanh toán đơn hàng", content, Type.INFO,
                "Xác nhận thanh toán", "Hủy", () -> result[0] = cash.isSelected() ? "Tiền mặt" : "Chuyển khoản",
                new Dimension(720, 680));
        return result[0];
    }

    private static JSeparator sectionDivider() {
        JSeparator separator = new JSeparator();
        separator.setForeground(UITheme.BORDER);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 12));
        return separator;
    }

    private static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 14));
        label.setForeground(UITheme.TEXT);
        label.setBorder(new EmptyBorder(4, 0, 8, 0));
        return label;
    }

    private static void addInfo(JPanel panel, String label, String value) {
        JPanel cell = new JPanel();
        cell.setOpaque(false);
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        JLabel key = new JLabel(label);
        key.setForeground(UITheme.MUTED);
        key.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JLabel content = new JLabel(value);
        content.setForeground(UITheme.TEXT);
        content.setFont(new Font("Segoe UI", Font.BOLD, 14));
        cell.add(key);
        cell.add(Box.createVerticalStrut(3));
        cell.add(content);
        panel.add(cell);
    }

    private static void addTableHeader(JPanel panel, String text, int alignment) {
        JLabel label = new JLabel(text, alignment);
        label.setFont(new Font("Segoe UI", Font.BOLD, 11));
        label.setForeground(UITheme.MUTED);
        label.setBorder(new EmptyBorder(5, 0, 5, 0));
        panel.add(label);
    }

    private static JRadioButton paymentCard(String title, String description, boolean selected) {
        JRadioButton card = new JRadioButton("  " + title + "\n  " + description, selected);
        card.setFont(new Font("Segoe UI", Font.BOLD, 14));
        card.setForeground(UITheme.TEXT);
        card.setOpaque(true);
        card.setBackground(UITheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(selected ? UITheme.PRIMARY : UITheme.BORDER, selected ? 2 : 1),
                new EmptyBorder(9, 10, 9, 10)));
        card.addItemListener(event -> card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(card.isSelected() ? UITheme.PRIMARY : UITheme.BORDER,
                        card.isSelected() ? 2 : 1), new EmptyBorder(9, 10, 9, 10))));
        return card;
    }

    private static String formatTime(String value) {
        if (value == null || value.isBlank()) return "-";
        try {
            return LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    private static JPanel labeledContent(String prompt, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.add(new JLabel(prompt), BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private static JComponent messageComponent(String message) {
        JTextArea area = new JTextArea(message == null ? "" : message);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setForeground(UITheme.TEXT);
        area.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        area.setBorder(null);
        area.setPreferredSize(new Dimension(360, Math.min(180,
                Math.max(50, area.getPreferredSize().height))));
        return area;
    }

    private static JComponent componentFor(Object message) {
        return message instanceof JComponent component
                ? component : messageComponent(String.valueOf(message));
    }

    private static Type typeFor(int messageType) {
        return switch (messageType) {
            case ERROR_MESSAGE -> Type.ERROR;
            case WARNING_MESSAGE -> Type.WARNING;
            case QUESTION_MESSAGE -> Type.CONFIRM;
            default -> Type.INFO;
        };
    }

    private static boolean showDialog(Component owner, String title, JComponent content,
                                      Type type, String confirmLabel, String cancelLabel,
                                      Runnable onConfirm) {
        return showDialog(owner, title, content, type, confirmLabel, cancelLabel, onConfirm, null);
    }

    private static boolean showDialog(Component owner, String title, JComponent content,
                                      Type type, String confirmLabel, String cancelLabel,
                                      Runnable onConfirm, Dimension preferredSize) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), title,
                Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(16, 0));
        root.setBackground(UITheme.CARD);
        root.setBorder(new EmptyBorder(22, 24, 18, 24));

        JLabel icon = new JLabel(type.icon, SwingConstants.CENTER);
        icon.setOpaque(true);
        icon.setBackground(type.color);
        icon.setForeground(UITheme.contrastText(type.color));
        icon.setFont(new Font("Segoe UI", Font.BOLD, 22));
        icon.setPreferredSize(new Dimension(42, 42));
        root.add(icon, BorderLayout.WEST);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        JLabel heading = new JLabel(title == null || title.isBlank() ? type.defaultTitle : title);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 17));
        heading.setForeground(UITheme.TEXT);
        body.add(heading, BorderLayout.NORTH);
        UITheme.styleTree(content);
        body.add(content, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = cancelLabel == null ? null : new JButton(cancelLabel);
        JButton confirm = new JButton(confirmLabel);
        if (cancel != null) UITheme.styleButton(cancel, UITheme.Action.NEUTRAL);
        String normalizedTitle = title == null ? "" : title.toLowerCase();
        boolean destructive = type == Type.ERROR
                || normalizedTitle.contains("hủy")
                || normalizedTitle.contains("xóa");
        UITheme.styleButton(confirm, destructive ? UITheme.Action.DESTRUCTIVE
                : type == Type.SUCCESS ? UITheme.Action.PAYMENT : UITheme.Action.CONFIRM);
        if (cancel != null) buttons.add(cancel);
        buttons.add(confirm);
        body.add(buttons, BorderLayout.SOUTH);
        root.add(body, BorderLayout.CENTER);

        final boolean[] accepted = {false};
        if (cancel != null) cancel.addActionListener(event -> dialog.dispose());
        confirm.addActionListener(event -> {
            accepted[0] = true;
            if (onConfirm != null) {
                onConfirm.run();
            }
            dialog.dispose();
        });
        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(confirm);
        dialog.pack();
        if (preferredSize != null) dialog.setSize(preferredSize);
        else dialog.setMinimumSize(new Dimension(430, dialog.getHeight()));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return accepted[0];
    }
}
