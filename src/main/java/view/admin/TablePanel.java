package view.admin;

import model.StoreTable;
import service.TableService;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class TablePanel extends JPanel {
    private final TableService service = new TableService();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Mã bàn", "Tên bàn", "Mã QR", "Trạng thái", "Thao tác"}, 0);
    public TablePanel() {
        setLayout(new BorderLayout()); setBackground(new Color(245, 247, 250));
        JLabel title = new JLabel("Quản lý bàn"); title.setFont(new Font("Arial", Font.BOLD, 24));
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
            for (StoreTable t : service.getAll()) model.addRow(new Object[] {t.getId(), t.getName(), t.getQrCode(), t.getStatus(), ""});
        } catch (Exception e) { error("Không tải được danh sách bàn", e); }
    }
    private void toggleSelected(int row) {
        if (row < 0) { error("Hãy chọn bàn", null); return; }
        try {
            int id = (int) model.getValueAt(row, 0);
            StoreTable t = service.getAll().stream().filter(item -> item.getId() == id).findFirst().orElseThrow();
            t.setStatus("Đang hoạt động".equalsIgnoreCase(t.getStatus()) ? "Lỗi" : "Đang hoạt động");
            service.update(t); load();
        } catch (Exception e) { error("Không thể cập nhật trạng thái bàn", e); }
    }
    private void error(String message, Exception e) {
        JOptionPane.showMessageDialog(this, message + (e == null ? "" : ": " + e.getMessage()), "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
}
