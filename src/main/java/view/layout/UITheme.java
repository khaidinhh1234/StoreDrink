package view.layout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.JTableHeader;
import java.awt.*;

public final class UITheme {
    public static final Color PRIMARY = new Color(8, 102, 213);
    public static final Color PRIMARY_DARK = new Color(6, 82, 176);
    public static final Color SIDEBAR = new Color(13, 13, 32);
    public static final Color BACKGROUND = new Color(15, 15, 35);
    public static final Color CARD = new Color(17, 17, 38);
    public static final Color TEXT = new Color(239, 241, 250);
    public static final Color MUTED = new Color(157, 161, 187);
    public static final Color BORDER = new Color(55, 56, 76);
    public static final Color SURFACE_HOVER = new Color(30, 31, 58);
    private static final Color FIELD = new Color(25, 26, 50);

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
        UIManager.put("Table.selectionBackground", new Color(39, 47, 86));
        UIManager.put("Table.selectionForeground", TEXT);
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
            label.setForeground(TEXT);
        }
        if (component instanceof JButton button) {
            styleButton(button);
        }
        if (component instanceof JTextField field) {
            field.setBackground(FIELD);
            field.setForeground(TEXT);
            field.setCaretColor(TEXT);
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    new EmptyBorder(7, 10, 7, 10)));
        }
        if (component instanceof JComboBox<?> combo) {
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
        button.setBackground(PRIMARY);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PRIMARY_DARK),
                new EmptyBorder(7, 14, 7, 14)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
        header.setBackground(new Color(17, 17, 38));
        header.setForeground(new Color(205, 210, 232));
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
                setBackground(new Color(17, 17, 38));
                setForeground(new Color(205, 210, 232));
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
                        setBackground(selected ? new Color(34, 43, 78)
                                : hovered ? SURFACE_HOVER : CARD);
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
            boolean positive = normalized.contains("đang hoạt động")
                    || normalized.contains("đang bán")
                    || normalized.contains("complete")
                    || normalized.contains("confirmed")
                    || normalized.contains("hoàn thành")
                    || normalized.contains("đã duyệt");
            badge.setText(text);
            badge.setForeground(Color.WHITE);
            Color statusColor = positive ? new Color(0, 166, 151)
                    : normalized.contains("hủy") || normalized.contains("lỗi")
                            ? new Color(239, 76, 69)
                            : new Color(245, 153, 35);
            badge.setBackground(statusColor);
            setBackground(selected ? new Color(39, 47, 86)
                    : new Color(16, 16, 36));
            return this;
        }
    }
}
