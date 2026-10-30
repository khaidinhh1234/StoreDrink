package view.layout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.Locale;

public final class UITheme {
    public static final Color PRIMARY = new Color(0x00, 0x7B, 0xFF);
    public static final Color PRIMARY_DARK = new Color(0x00, 0x66, 0xD6);
    public static final Color SUCCESS = new Color(0x28, 0xA7, 0x45);
    public static final Color WARNING = new Color(0xFF, 0xC1, 0x07);
    public static final Color DANGER = new Color(0xDC, 0x35, 0x45);
    public static final Color INFO = new Color(0x17, 0xA2, 0xB8);
    public static final Color SECONDARY = new Color(0x6C, 0x75, 0x7D);
    public static final Color SIDEBAR = new Color(0x34, 0x3A, 0x40);
    public static final Color BACKGROUND = new Color(0xF4, 0xF6, 0xF9);
    public static final Color CARD = Color.WHITE;
    public static final Color TEXT = new Color(0x34, 0x3A, 0x40);
    public static final Color MUTED = new Color(0x6C, 0x75, 0x7D);
    public static final Color BORDER = new Color(0xDE, 0xE2, 0xE6);
    public static final Color SURFACE_HOVER = new Color(0x49, 0x50, 0x57);
    public static final Color TABLE_SELECTION = new Color(0xE6, 0xF0, 0xFF);
    private static final Color FIELD = Color.WHITE;
    private static final Color EXPORT = new Color(0x1E, 0x7E, 0x34);

    public enum Action {
        CREATE(SUCCESS), CONFIRM(PRIMARY), EDIT(WARNING), DESTRUCTIVE(DANGER),
        TOGGLE(SECONDARY), NEUTRAL(SECONDARY), EXPORT(UITheme.EXPORT), PRINT(INFO), PAYMENT(SUCCESS);

        private final Color color;
        Action(Color color) { this.color = color; }
    }

    private UITheme() {
    }

    public static void install() {
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Panel.foreground", TEXT);
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.messageFont", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("Button.font", new Font("Segoe UI", Font.BOLD, 13));
        UIManager.put("Label.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("ComboBox.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("Table.font", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("TableHeader.font", new Font("Segoe UI", Font.BOLD, 13));
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("Table.selectionBackground", TABLE_SELECTION);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TextArea.background", FIELD);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", TEXT);
        UIManager.put("ScrollPane.background", CARD);
        UIManager.put("TextField.background", FIELD);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("PasswordField.background", FIELD);
        UIManager.put("PasswordField.foreground", TEXT);
        UIManager.put("ComboBox.background", FIELD);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", PRIMARY);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
    }

    public static void styleTree(Component component) {
        if (component instanceof JLabel label) {
            Color defaultForeground = UIManager.getColor("Label.foreground");
            if (label.getForeground() == null || label.getForeground().equals(defaultForeground)) {
                label.setForeground(TEXT);
            }
        }
        if (component instanceof JButton button
                && !Boolean.TRUE.equals(button.getClientProperty("uiTheme.preserveCustomStyle"))) {
            Color defaultBackground = UIManager.getColor("Button.background");
            Color currentBackground = button.getBackground();
            styleButton(button, currentBackground != null && !currentBackground.equals(defaultBackground)
                    ? currentBackground : PRIMARY);
        }
        if (component instanceof JTextField field) {
            field.setBackground(FIELD);
            field.setForeground(TEXT);
            field.setCaretColor(TEXT);
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    new EmptyBorder(7, 10, 7, 10)));
        }
        if (component instanceof JComboBox<?> combo
                && !Boolean.TRUE.equals(combo.getClientProperty("uiTheme.preserveCustomStyle"))) {
            combo.setBackground(FIELD);
            combo.setForeground(TEXT);
            combo.setBorder(BorderFactory.createLineBorder(BORDER));
        }
        if (component instanceof JScrollPane scrollPane) {
            scrollPane.setBackground(CARD);
            scrollPane.getViewport().setBackground(CARD);
        }
        if (component instanceof JTable table) {
            styleTable(table);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                styleTree(child);
            }
        }
    }

    public static void styleButton(JButton button) {
        styleButton(button, PRIMARY);
    }

    public static void styleButton(JButton button, Color background) {
        button.setBackground(background);
        button.setForeground(contrastText(background));
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(background.darker()),
                new EmptyBorder(7, 14, 7, 14)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public static void styleButton(JButton button, Action action) {
        Color normal = action.color;
        button.putClientProperty("uiTheme.actionColor", normal);
        styleButton(button, normal);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(normal.darker()),
                new EmptyBorder(7, 14, 7, 14)));
        if (!Boolean.TRUE.equals(button.getClientProperty("uiTheme.actionStyled"))) {
            button.putClientProperty("uiTheme.actionStyled", Boolean.TRUE);
            button.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) {
                    Color current = actionColor(button);
                    if (button.isEnabled()) button.setBackground(hoverColor(current));
                }
                @Override public void mouseExited(MouseEvent event) {
                    if (button.isEnabled()) button.setBackground(actionColor(button));
                }
            });
            button.addPropertyChangeListener("enabled", event -> {
                boolean enabled = Boolean.TRUE.equals(event.getNewValue());
                Color current = actionColor(button);
                button.setBackground(enabled ? current : new Color(0xE9, 0xEC, 0xEF));
                button.setForeground(enabled ? contrastText(current) : MUTED);
            });
        }
    }

    private static Color actionColor(JButton button) {
        Object color = button.getClientProperty("uiTheme.actionColor");
        return color instanceof Color value ? value : PRIMARY;
    }

    private static Color hoverColor(Color color) {
        return color.equals(WARNING) ? new Color(0xFF, 0xD3, 0x55) : color.brighter();
    }

    public static NumberFormat newVndFormatter() {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(0);
        format.setGroupingUsed(true);
        return format;
    }

    public static String formatVnd(double amount) {
        return newVndFormatter().format(amount) + " đ";
    }

    public static Color contrastText(Color background) {
        double luminance = (0.2126 * background.getRed() + 0.7152 * background.getGreen()
                + 0.0722 * background.getBlue()) / 255.0;
        return luminance > 0.68 ? TEXT : Color.WHITE;
    }

    public static void styleTable(JTable table) {
        table.setRowHeight(44);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setForeground(TEXT);
        table.setBackground(CARD);
        table.setSelectionForeground(TEXT);
        table.setBorder(BorderFactory.createEmptyBorder());
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(false);
        JTableHeader header = table.getTableHeader();
        header.setBackground(new Color(0xF8, 0xF9, 0xFA));
        header.setForeground(TEXT);
        header.setPreferredSize(new Dimension(header.getWidth(), 48));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            {
                setHorizontalAlignment(SwingConstants.CENTER);
                setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean selected, boolean focused, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focused,
                        row, column);
                setBackground(new Color(0xF8, 0xF9, 0xFA));
                setForeground(TEXT);
                return this;
            }
        });

        javax.swing.table.DefaultTableCellRenderer bodyRenderer =
                new javax.swing.table.DefaultTableCellRenderer() {
                    {
                        setHorizontalAlignment(SwingConstants.CENTER);
                        setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
                    }

                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object value,
                            boolean selected, boolean focused, int row, int column) {
                        super.getTableCellRendererComponent(table, value, selected, focused,
                                row, column);
                        int modelRow = table.convertRowIndexToModel(row);
                        boolean hovered = table.getClientProperty("hoverRow") instanceof Integer
                                && (Integer) table.getClientProperty("hoverRow") == modelRow;
                        setBackground(selected ? TABLE_SELECTION
                                : hovered ? new Color(0xF2, 0xF7, 0xFF)
                                        : row % 2 == 0 ? CARD : new Color(0xFB, 0xFC, 0xFD));
                        setForeground(TEXT);
                        return this;
                    }
                };
        for (int column = 0; column < table.getColumnCount(); column++) {
            if (isActionColumn(table, column)
                    && table.getColumnModel().getColumn(column).getCellRenderer() != null) {
                continue;
            }
            table.getColumnModel().getColumn(column).setCellRenderer(bodyRenderer);
            if (isStatusColumn(table, column)) {
                table.getColumnModel().getColumn(column).setCellRenderer(new StatusCellRenderer());
            }
        }
    }

    private static boolean isActionColumn(JTable table, int column) {
        Object headerValue = table.getColumnModel().getColumn(column).getHeaderValue();
        return headerValue != null && headerValue.toString().toLowerCase(java.util.Locale.ROOT)
                .contains("thao tác");
    }

    private static boolean isStatusColumn(JTable table, int column) {
        Object headerValue = table.getColumnModel().getColumn(column).getHeaderValue();
        if (headerValue != null && headerValue.toString().toLowerCase(java.util.Locale.ROOT)
                .contains("trạng thái")) {
            return true;
        }
        for (int row = 0; row < table.getRowCount(); row++) {
            Object value = table.getValueAt(row, column);
            if (value != null) {
                String text = value.toString().toLowerCase(java.util.Locale.ROOT);
                if (text.contains("đang hoạt động") || text.contains("đang bán")
                        || text.contains("ngừng bán") || text.contains("lỗi")
                        || text.contains("pending") || text.contains("confirmed")
                        || text.contains("complete") || text.contains("cancel")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class StatusCellRenderer extends JPanel
            implements javax.swing.table.TableCellRenderer {
        private final JLabel badge = new JLabel();

        private StatusCellRenderer() {
            setOpaque(true);
            setLayout(new FlowLayout(FlowLayout.CENTER, 14, 0));
            badge.setOpaque(true);
            badge.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            add(badge);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            String text = value == null ? "" : value.toString();
            String normalized = text.toLowerCase(java.util.Locale.ROOT);
            boolean completed = normalized.contains("đã thanh toán")
                    || normalized.contains("hoàn thành")
                    || normalized.contains("completed")
                    || normalized.equals("complete");
            boolean confirmed = normalized.contains("đã duyệt")
                    || normalized.contains("đang pha chế")
                    || normalized.contains("confirmed");
            boolean positive = normalized.contains("đang hoạt động")
                    || normalized.contains("đang bán")
                    || completed;
            badge.setText(text);
            Color statusColor = confirmed ? PRIMARY
                    : positive ? SUCCESS
                    : normalized.contains("hủy") || normalized.contains("lỗi")
                            ? DANGER : WARNING;
            badge.setForeground(statusColor.equals(WARNING) ? TEXT : Color.WHITE);
            badge.setBackground(statusColor);
            setBackground(selected ? TABLE_SELECTION : CARD);
            return this;
        }
    }
}
