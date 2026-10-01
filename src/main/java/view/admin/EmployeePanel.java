package view.admin;

import model.User;
import service.UserService;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class EmployeePanel extends JPanel {
    private final UserService service = new UserService();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"ID", "Tài khoản", "Họ tên", "Vai trò", "Trạng thái", "Thao tác"}, 0);
    public EmployeePanel() {
        setLayout(new BorderLayout()); setBackground(new Color(245, 247, 250));
        JLabel title = new JLabel("Quản lý nhân viên"); title.setFont(new Font("Arial", Font.BOLD, 24));
        JPanel header = new JPanel(new BorderLayout()); header.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        header.add(title, BorderLayout.WEST); add(header, BorderLayout.NORTH);
        JTable table = new JTable(model); table.setRowHeight(32);
        JButton toggle = new JButton("Đổi trạng thái");
        toggle.addActionListener(e -> toggleSelected(table.getSelectedRow()));
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT)); footer.add(toggle);
        add(new JScrollPane(table), BorderLayout.CENTER); add(footer, BorderLayout.SOUTH); load();
    }
    private void load() {
        try {
            model.setRowCount(0);
            for (User u : service.getAll()) {
                if ("ADMIN".equalsIgnoreCase(u.getRole())) continue;
                model.addRow(new Object[] {u.getId(), u.getUsername(), u.getFullName(), u.getRole(),
                        u.isActive() ? "Đang hoạt động" : "Lỗi", ""});
            }
        } catch (Exception e) { error("Không tải được nhân viên", e); }
    }
    private void toggleSelected(int row) {
        if (row < 0) { error("Hãy chọn nhân viên", null); return; }
        try {
            int id = (int) model.getValueAt(row, 0);
            User u = service.getAll().stream().filter(item -> item.getId() == id).findFirst().orElseThrow();
            u.setActive(!u.isActive()); service.update(u); load();
        } catch (Exception e) { error("Không thể cập nhật trạng thái nhân viên", e); }
    }
    private void error(String message, Exception e) {
        JOptionPane.showMessageDialog(this, message + (e == null ? "" : ": " + e.getMessage()), "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
}
