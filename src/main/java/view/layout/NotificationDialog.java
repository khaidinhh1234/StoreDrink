package view.layout;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;


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
