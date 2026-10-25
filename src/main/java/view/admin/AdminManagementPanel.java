package view.admin;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import view.layout.UITheme;

public class AdminManagementPanel extends JPanel {
    public AdminManagementPanel(String title, String addLabel, String[] columns, Object[][] rows) {
        setLayout(new BorderLayout());
        setBackground(UITheme.BACKGROUND);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.BACKGROUND);
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JLabel heading = new JLabel(title);
        heading.setForeground(UITheme.TEXT);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 24));
        JButton addButton = new JButton(addLabel);
        header.add(heading, BorderLayout.WEST);
        header.add(addButton, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(rows, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(44);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(table), BorderLayout.CENTER);
        UITheme.styleTree(this);
    }
}
