package view.admin;

import model.User;
import service.UserService;
import view.layout.UITheme;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class EmployeePanel extends JPanel {
    private final UserService service = new UserService();
    private final User currentUser;
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"ID", "Tài khoản", "Họ tên", "Vai trò", "Trạng thái", "Thao tác"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 5;
        }
    };
    private JTable table;

    public EmployeePanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout());
        setBackground(UITheme.BACKGROUND);

        JLabel title = new JLabel("Quản lý nhân viên");
        title.setForeground(UITheme.TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.BACKGROUND);
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JButton add = new JButton("Thêm nhân viên");
        add.addActionListener(e -> addEmployee());
        header.add(title, BorderLayout.WEST);
        header.add(add, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        table = new JTable(model);
        table.setRowHeight(44);
        table.getColumnModel().getColumn(5).setPreferredWidth(220);
        table.getColumnModel().getColumn(5).setCellRenderer(new EmployeeActionRenderer());
        table.getColumnModel().getColumn(5).setCellEditor(new EmployeeActionEditor());
        add(new JScrollPane(table), BorderLayout.CENTER);
        UITheme.styleTree(this);
        load();
    }

    private void load() {
        try {
            model.setRowCount(0);
            for (User user : service.getAll()) {
                model.addRow(new Object[] {user.getId(), user.getUsername(), user.getFullName(),
                        user.getRole(), user.isActive() ? "Đang hoạt động" : "Ngừng hoạt động", ""});
            }
        } catch (Exception exception) {
            error("Không tải được nhân viên", exception);
        }
    }

    private void toggleSelected(int row) {
        if (row < 0) {
            error("Hãy chọn nhân viên", null);
            return;
        }
        try {
            int id = (int) model.getValueAt(row, 0);
            if (id == currentUser.getId()) {
                error("Không thể ngừng hoạt động tài khoản đang đăng nhập", null);
                return;
            }
            User user = findUser(id);
            user.setActive(!user.isActive());
            service.update(user);
            load();
        } catch (Exception exception) {
            error("Không thể cập nhật trạng thái nhân viên", exception);
        }
    }

    private void addEmployee() {
        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();
        JTextField fullName = new JTextField();
        JComboBox<String> role = new JComboBox<>(new String[] {"STAFF", "ADMIN"});
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Tài khoản:")); form.add(username);
        form.add(new JLabel("Mật khẩu:")); form.add(password);
        form.add(new JLabel("Họ tên:")); form.add(fullName);
        form.add(new JLabel("Vai trò:")); form.add(role);
        if (JOptionPane.showConfirmDialog(this, form, "Thêm nhân viên",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            if (username.getText().trim().isEmpty() || password.getPassword().length == 0
                    || fullName.getText().trim().isEmpty()) {
                throw new IllegalArgumentException("Vui lòng nhập đầy đủ thông tin");
            }
            User employee = new User();
            employee.setId(nextId(service.getAll()));
            employee.setUsername(username.getText().trim());
            employee.setPassword(new String(password.getPassword()));
            employee.setFullName(fullName.getText().trim());
            employee.setRole((String) role.getSelectedItem());
            employee.setActive(true);
            service.create(employee);
            load();
        } catch (Exception exception) {
            error("Không thể thêm nhân viên", exception);
        }
    }

    private void editSelected(int row) {
        if (row < 0) {
            error("Hãy chọn tài khoản", null);
            return;
        }
        try {
            User employee = findUser((int) model.getValueAt(row, 0));
            JTextField username = new JTextField(employee.getUsername());
            JPasswordField password = new JPasswordField();
            JTextField fullName = new JTextField(employee.getFullName());
            JComboBox<String> role = new JComboBox<>(new String[] {"STAFF", "ADMIN"});
            role.setSelectedItem(employee.getRole());
            boolean editingCurrentUser = employee.getId() == currentUser.getId();
            role.setEnabled(!editingCurrentUser);
            if (editingCurrentUser) {
                role.setToolTipText("Không thể đổi vai trò của tài khoản đang đăng nhập");
            }
            JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
            form.add(new JLabel("Tài khoản:")); form.add(username);
            form.add(new JLabel("Mật khẩu mới:")); form.add(password);
            form.add(new JLabel("Họ tên:")); form.add(fullName);
            form.add(new JLabel("Vai trò:")); form.add(role);
            form.add(new JLabel("")); form.add(new JLabel("Để trống mật khẩu nếu không đổi"));

            if (JOptionPane.showConfirmDialog(this, form, "Cập nhật tài khoản",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            if (username.getText().trim().isEmpty() || fullName.getText().trim().isEmpty()) {
                throw new IllegalArgumentException("Tài khoản và họ tên không được để trống");
            }
            employee.setUsername(username.getText().trim());
            employee.setFullName(fullName.getText().trim());
            if (!editingCurrentUser) {
                employee.setRole((String) role.getSelectedItem());
            }
            if (password.getPassword().length > 0) {
                employee.setPassword(new String(password.getPassword()));
            }
            if (employee.getId() == currentUser.getId()) {
                employee.setActive(true);
            }
            service.update(employee);
            currentUser.setUsername(employee.getUsername());
            currentUser.setFullName(employee.getFullName());
            currentUser.setRole(employee.getRole());
            currentUser.setActive(employee.isActive());
            load();
        } catch (Exception exception) {
            error("Không thể cập nhật tài khoản", exception);
        }
    }

    private User findUser(int id) throws Exception {
        return service.getAll().stream()
                .filter(user -> user.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản"));
    }

    private int nextId(java.util.List<User> users) {
        return users.stream().mapToInt(User::getId).max().orElse(0) + 1;
    }

    private class EmployeeActionRenderer extends JPanel implements TableCellRenderer {
        private final JButton edit = new JButton("Sửa");
        private final JButton toggle = new JButton("Đổi trạng thái");

        EmployeeActionRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 5, 2));
            setOpaque(true);
            styleActionButton(edit, UITheme.PRIMARY);
            styleActionButton(toggle, new Color(48, 58, 92));
            add(edit);
            add(toggle);
        }

        @Override
        public Component getTableCellRendererComponent(JTable source, Object value,
                boolean selected, boolean focused, int row, int column) {
            setBackground(selected ? source.getSelectionBackground() : source.getBackground());
            boolean current = ((Number) source.getValueAt(row, 0)).intValue() == currentUser.getId();
            toggle.setEnabled(!current);
            toggle.setText(current ? "Tài khoản hiện tại" : "Đổi trạng thái");
            toggle.setToolTipText(current ? "Không thể đổi trạng thái tài khoản của bạn" : null);
            return this;
        }
    }

    private class EmployeeActionEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
        private int viewRow;

        EmployeeActionEditor() {
            JButton edit = new JButton("Sửa");
            JButton toggle = new JButton("Đổi trạng thái");
            styleActionButton(edit, UITheme.PRIMARY);
            styleActionButton(toggle, new Color(48, 58, 92));
            edit.addActionListener(event -> {
                fireEditingStopped();
                editSelected(table.convertRowIndexToModel(viewRow));
            });
            toggle.addActionListener(event -> {
                fireEditingStopped();
                toggleSelected(table.convertRowIndexToModel(viewRow));
            });
            toggle.setDisabledIcon(null);
            panel.add(edit);
            panel.add(toggle);
        }

        @Override
        public Component getTableCellEditorComponent(JTable source, Object value,
                boolean selected, int row, int column) {
            viewRow = row;
            boolean current = ((Number) source.getValueAt(row, 0)).intValue() == currentUser.getId();
            Component toggle = panel.getComponent(1);
            toggle.setEnabled(!current);
            ((JButton) toggle).setText(current ? "Tài khoản hiện tại" : "Đổi trạng thái");
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return "";
        }
    }

    private void styleActionButton(JButton button, Color color) {
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 9));
    }

    private void error(String message, Exception exception) {
        JOptionPane.showMessageDialog(this, message
                + (exception == null ? "" : ": " + exception.getMessage()),
                "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
}
