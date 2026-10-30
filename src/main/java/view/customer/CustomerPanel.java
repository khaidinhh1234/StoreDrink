package view.customer;

import view.layout.NotificationDialog;

import model.Customer;
import service.CustomerService;
import view.layout.DataSync;
import view.layout.RefreshablePanel;
import view.layout.UITheme;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class CustomerPanel extends JPanel implements RefreshablePanel {
    private final CustomerService service = new CustomerService();
    private final CustomerTableModel model = new CustomerTableModel();
    private final JButton addButton = new JButton("Thêm khách hàng");
    private final JButton refreshButton = new JButton("Làm mới");
    private JTable table;

    public CustomerPanel() {
        setLayout(new BorderLayout());
        setBackground(UITheme.BACKGROUND);

        JLabel title = new JLabel("Quản lý khách hàng");
        title.setForeground(UITheme.TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(UITheme.BACKGROUND);
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(refreshButton);
        actions.add(addButton);
        header.add(title, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
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
        UITheme.styleButton(addButton, UITheme.Action.CREATE);
        UITheme.styleButton(refreshButton, UITheme.Action.NEUTRAL);
        addButton.addActionListener(event -> addCustomer());
        refreshButton.addActionListener(event -> loadCustomers());
        loadCustomers();
        DataSync.register(this, this);
    }

    @Override public void refreshData() {
        if (refreshButton.isEnabled()) loadCustomers();
    }

    private void loadCustomers() {
        if (!refreshButton.isEnabled()) return;
        refreshButton.setEnabled(false);
        new SwingWorker<List<Customer>, Void>() {
            @Override protected List<Customer> doInBackground() throws Exception {
                return service.getAll();
            }

            @Override protected void done() {
                try {
                    model.setCustomers(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Đã dừng khi tải khách hàng", exception);
                } catch (ExecutionException exception) {
                    showError("Không tải được khách hàng", exception.getCause());
                } finally {
                    refreshButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void addCustomer() {
        JTextField name = new JTextField();
        JTextField phone = new JTextField();
        JTextField email = new JTextField();
        if (NotificationDialog.showConfirmDialog(this, createForm(name, phone, email), "Thêm khách hàng",
                NotificationDialog.OK_CANCEL_OPTION) != NotificationDialog.OK_OPTION) return;
        if (name.getText().trim().isEmpty()) {
            NotificationDialog.showMessageDialog(this, "Tên khách hàng không được để trống.");
            return;
        }
        Customer customer = new Customer(0, name.getText().trim(), phone.getText().trim(), email.getText().trim());
        runMutation("Không thể thêm khách hàng", () -> service.create(customer));
    }

    private void editCustomer(int row) {
        Customer customer = model.getCustomer(row);
        JTextField name = new JTextField(customer.getName());
        JTextField phone = new JTextField(customer.getPhone());
        JTextField email = new JTextField(customer.getEmail());
        if (NotificationDialog.showConfirmDialog(this, createForm(name, phone, email), "Sửa khách hàng",
                NotificationDialog.OK_CANCEL_OPTION) != NotificationDialog.OK_OPTION) return;
        if (name.getText().trim().isEmpty()) {
            NotificationDialog.showMessageDialog(this, "Tên khách hàng không được để trống.");
            return;
        }
        customer.setName(name.getText().trim());
        customer.setPhone(phone.getText().trim());
        customer.setEmail(email.getText().trim());
        runMutation("Không thể cập nhật khách hàng", () -> service.update(customer));
    }

    private JPanel createForm(JTextField name, JTextField phone, JTextField email) {
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Họ tên khách hàng:")); form.add(name);
        form.add(new JLabel("Số điện thoại:")); form.add(phone);
        form.add(new JLabel("Email:")); form.add(email);
        return form;
    }

    private void deleteCustomer(int row) {
        Customer customer = model.getCustomer(row);
        if (NotificationDialog.showConfirmDialog(this,
                "Bạn có chắc muốn xóa khách hàng " + customer.getName() + "?",
                "Xác nhận xóa", NotificationDialog.YES_NO_OPTION, NotificationDialog.WARNING_MESSAGE)
                != NotificationDialog.YES_OPTION) return;
        runMutation("Không thể xóa khách hàng", () -> service.delete(customer.getId()));
    }

    private void runMutation(String errorMessage, CustomerMutation mutation) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        addButton.setEnabled(false);
        refreshButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                mutation.run();
                return null;
            }

            @Override protected void done() {
                try {
                    get();
                    loadCustomers();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError(errorMessage, exception);
                } catch (ExecutionException exception) {
                    showError(errorMessage, exception.getCause());
                } finally {
                    addButton.setEnabled(true);
                    refreshButton.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        }.execute();
    }

    private void showError(String message, Throwable exception) {
        String detail = exception == null || exception.getMessage() == null
                ? "" : ": " + exception.getMessage();
        NotificationDialog.showMessageDialog(this, message + detail, "Lỗi", NotificationDialog.ERROR_MESSAGE);
    }

    @FunctionalInterface
    private interface CustomerMutation {
        void run() throws Exception;
    }

    private class ActionRenderer extends JPanel implements TableCellRenderer {
        ActionRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 6, 3));
            setOpaque(true);
            JButton edit = new JButton("Sửa");
            JButton delete = new JButton("Xóa");
            UITheme.styleButton(edit, UITheme.Action.EDIT);
            UITheme.styleButton(delete, UITheme.Action.DESTRUCTIVE);
            add(edit);
            add(delete);
        }

        @Override public Component getTableCellRendererComponent(JTable source, Object value,
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
            UITheme.styleButton(edit, UITheme.Action.EDIT);
            UITheme.styleButton(delete, UITheme.Action.DESTRUCTIVE);
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

        @Override public Component getTableCellEditorComponent(JTable source, Object value,
                boolean selected, int row, int column) {
            viewRow = row;
            return actions;
        }

        @Override public Object getCellEditorValue() { return ""; }
    }

    private static class CustomerTableModel extends AbstractTableModel {
        private final String[] columns = { "ID", "Tên khách hàng", "Số điện thoại", "Email", "Thao tác" };
        private final List<Customer> customers = new ArrayList<>();

        @Override public int getRowCount() { return customers.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Object getValueAt(int row, int column) {
            Customer customer = customers.get(row);
            return switch (column) {
                case 0 -> customer.getId();
                case 1 -> customer.getName();
                case 2 -> customer.getPhone();
                case 3 -> customer.getEmail();
                default -> "";
            };
        }
        @Override public boolean isCellEditable(int row, int column) { return column == 4; }
        Customer getCustomer(int row) { return customers.get(row); }
        void setCustomers(List<Customer> rows) {
            customers.clear();
            customers.addAll(rows);
            fireTableDataChanged();
        }
    }
}
