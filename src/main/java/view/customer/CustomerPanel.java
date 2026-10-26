package view.customer;

import view.layout.UITheme;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CustomerPanel extends JPanel {
    private final CustomerTableModel model = new CustomerTableModel();
    private JTable table;

    public CustomerPanel() {
        setLayout(new BorderLayout());
        setBackground(UITheme.BACKGROUND);

        JLabel title = new JLabel("Quản lý khách hàng");
        title.setForeground(UITheme.TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        JButton addButton = new JButton("Thêm khách hàng");
        addButton.addActionListener(event -> addCustomer());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.BACKGROUND);
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        header.add(title, BorderLayout.WEST);
        header.add(addButton, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        table = new JTable(model);
        table.setRowHeight(48);
        table.getColumnModel().getColumn(4).setPreferredWidth(170);
        table.getColumnModel().getColumn(4).setCellRenderer(new ActionRenderer());
        table.getColumnModel().getColumn(4).setCellEditor(new ActionEditor());
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        add(scrollPane, BorderLayout.CENTER);
        UITheme.styleTree(this);
    }

    private void addCustomer() {
        JTextField name = new JTextField();
        JTextField phone = new JTextField();
        JTextField email = new JTextField();
        JPanel form = createForm(name, phone, email);
        if (JOptionPane.showConfirmDialog(this, form, "Thêm khách hàng",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        if (name.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Tên khách hàng không được để trống.");
            return;
        }
        model.addCustomer(name.getText().trim(), phone.getText().trim(), email.getText().trim());
    }

    private void editCustomer(int row) {
        Customer customer = model.getCustomer(row);
        JTextField name = new JTextField(customer.name);
        JTextField phone = new JTextField(customer.phone);
        JTextField email = new JTextField(customer.email);
        if (JOptionPane.showConfirmDialog(this, createForm(name, phone, email), "Sửa khách hàng",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        if (name.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Tên khách hàng không được để trống.");
            return;
        }
        model.updateCustomer(row, name.getText().trim(), phone.getText().trim(), email.getText().trim());
    }

    private JPanel createForm(JTextField name, JTextField phone, JTextField email) {
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Tên khách hàng:")); form.add(name);
        form.add(new JLabel("Số điện thoại:")); form.add(phone);
        form.add(new JLabel("Email:")); form.add(email);
        return form;
    }

    private void deleteCustomer(int row) {
        if (JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa khách hàng này?",
                "Xác nhận xóa", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            model.removeCustomer(row);
        }
    }

    private void styleButton(JButton button, Color color) {
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
    }

    private class ActionRenderer extends JPanel implements TableCellRenderer {
        ActionRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 6, 3));
            setOpaque(true);
            JButton edit = new JButton("Sửa");
            JButton delete = new JButton("Xóa");
            styleButton(edit, UITheme.PRIMARY);
            styleButton(delete, new Color(210, 63, 70));
            add(edit);
            add(delete);
        }

        @Override
        public Component getTableCellRendererComponent(JTable source, Object value,
                boolean selected, boolean focused, int row, int column) {
            setBackground(selected ? source.getSelectionBackground() : source.getBackground());
            return this;
        }
    }

    private class ActionEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 3));
        private int viewRow;

        ActionEditor() {
            JButton edit = new JButton("Sửa");
            JButton delete = new JButton("Xóa");
            styleButton(edit, UITheme.PRIMARY);
            styleButton(delete, new Color(210, 63, 70));
            edit.addActionListener(event -> {
                fireEditingStopped();
                editCustomer(table.convertRowIndexToModel(viewRow));
            });
            delete.addActionListener(event -> {
                fireEditingStopped();
                deleteCustomer(table.convertRowIndexToModel(viewRow));
            });
            actions.add(edit);
            actions.add(delete);
        }

        @Override
        public Component getTableCellEditorComponent(JTable source, Object value,
                boolean selected, int row, int column) {
            viewRow = row;
            return actions;
        }

        @Override public Object getCellEditorValue() { return ""; }
    }

    private static class CustomerTableModel extends AbstractTableModel {
        private final String[] columns = {"ID", "Tên khách hàng", "Số điện thoại", "Email", "Thao tác"};
        private final List<Customer> customers = new ArrayList<>(List.of(
                new Customer(1, "Nguyễn Văn An", "0987654321", "an@gmail.com"),
                new Customer(2, "Trần Thị Bích", "0912345678", "bich@gmail.com")));

        @Override public int getRowCount() { return customers.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Object getValueAt(int row, int column) {
            Customer customer = customers.get(row);
            return switch (column) {
                case 0 -> customer.id;
                case 1 -> customer.name;
                case 2 -> customer.phone;
                case 3 -> customer.email;
                default -> "";
            };
        }
        @Override public boolean isCellEditable(int row, int column) { return column == 4; }
        Customer getCustomer(int row) { return customers.get(row); }
        void addCustomer(String name, String phone, String email) {
            int id = customers.stream().mapToInt(customer -> customer.id).max().orElse(0) + 1;
            customers.add(new Customer(id, name, phone, email));
            fireTableRowsInserted(customers.size() - 1, customers.size() - 1);
        }
        void updateCustomer(int row, String name, String phone, String email) {
            Customer old = customers.get(row);
            customers.set(row, new Customer(old.id, name, phone, email));
            fireTableRowsUpdated(row, row);
        }
        void removeCustomer(int row) {
            customers.remove(row);
            fireTableRowsDeleted(row, row);
        }
    }

    private static class Customer {
        final int id;
        final String name;
        final String phone;
        final String email;
        Customer(int id, String name, String phone, String email) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.email = email;
        }
    }
}
