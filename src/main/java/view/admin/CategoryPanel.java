package view.admin;

import model.Category;
import service.CategoryService;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class CategoryPanel extends JPanel {
    private final CategoryService service = new CategoryService();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"ID", "Tên danh mục", "Mô tả"}, 0);
    public CategoryPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(245, 247, 250));
        JLabel title = new JLabel("Quản lý danh mục");
        title.setFont(new Font("Arial", Font.BOLD, 24));
        JButton add = new JButton("Thêm danh mục");
        add.addActionListener(e -> addCategory());
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        header.add(title, BorderLayout.WEST); header.add(add, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        load();
    }
    private void load() {
        try {
            model.setRowCount(0);
            for (Category c : service.getAll()) model.addRow(new Object[] {c.getId(), c.getName(), c.getDescription()});
        } catch (Exception e) { showError("Không tải được danh mục", e); }
    }
    private void addCategory() {
        JTextField name = new JTextField(); JTextField description = new JTextField();
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Tên:")); form.add(name); form.add(new JLabel("Mô tả:")); form.add(description);
        if (JOptionPane.showConfirmDialog(this, form, "Thêm danh mục", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            if (name.getText().trim().isEmpty()) throw new IllegalArgumentException("Tên không được để trống");
            Category c = new Category(); c.setName(name.getText().trim()); c.setDescription(description.getText().trim());
            service.create(c); load();
        } catch (Exception e) { showError("Không thể thêm danh mục", e); }
    }
    private void showError(String message, Exception e) {
        JOptionPane.showMessageDialog(this, message + ": " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
}
