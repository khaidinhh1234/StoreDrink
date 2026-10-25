package view.admin;

import model.StoreTable;
import service.TableService;
import view.layout.UITheme;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class TablePanel extends JPanel {
    private final TableService service = new TableService();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Mã bàn", "Tên bàn", "Mã QR", "Trạng thái", "Thao tác"}, 0);
    public TablePanel() {
        setLayout(new BorderLayout()); setBackground(UITheme.BACKGROUND);
        JLabel title = new JLabel("Quản lý bàn"); title.setForeground(UITheme.TEXT); title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        JPanel header = new JPanel(new BorderLayout()); header.setBackground(UITheme.BACKGROUND); header.setBorder(BorderFactory.createEmptyBorder(24, 28, 18, 28));
        JButton add = new JButton("Thêm bàn");
        add.addActionListener(e -> addTable());
        header.add(title, BorderLayout.WEST); header.add(add, BorderLayout.EAST); add(header, BorderLayout.NORTH);
        JTable table = new JTable(model);
        table.setRowHeight(54);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        table.getColumnModel().getColumn(3).setPreferredWidth(220);
        table.getColumnModel().getColumn(4).setPreferredWidth(180);
        table.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                int row = table.rowAtPoint(event.getPoint());
                int modelRow = row < 0 ? -1 : table.convertRowIndexToModel(row);
                if (!Integer.valueOf(modelRow).equals(table.getClientProperty("hoverRow"))) {
                    table.putClientProperty("hoverRow", modelRow);
                    table.repaint();
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent event) {
                table.putClientProperty("hoverRow", -1);
                table.repaint();
            }
        });
        JButton toggle = new JButton("Đổi trạng thái");
        toggle.addActionListener(e -> toggleSelected(table.getSelectedRow()));
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT)); footer.add(toggle);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(UITheme.CARD);
        add(scrollPane, BorderLayout.CENTER); add(footer, BorderLayout.SOUTH);
        UITheme.styleTree(this);
        load();
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
    private void addTable() {
        JTextField name = new JTextField();
        JTextField qrCode = new JTextField();
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Tên bàn:")); form.add(name);
        form.add(new JLabel("Mã QR:")); form.add(qrCode);
        if (JOptionPane.showConfirmDialog(this, form, "Thêm bàn",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            String tableName = name.getText().trim();
            if (tableName.isEmpty()) throw new IllegalArgumentException("Tên bàn không được để trống");
            StoreTable table = new StoreTable();
            table.setId(nextId(service.getAll()));
            table.setName(tableName);
            table.setQrCode(qrCode.getText().trim().isEmpty()
                    ? "TABLE-" + String.format("%02d", table.getId()) : qrCode.getText().trim());
            table.setStatus("Đang hoạt động");
            service.create(table);
            load();
        } catch (Exception e) { error("Không thể thêm bàn", e); }
    }
    private int nextId(java.util.List<StoreTable> tables) {
        return tables.stream().mapToInt(StoreTable::getId).max().orElse(0) + 1;
    }
    private void error(String message, Exception e) {
        JOptionPane.showMessageDialog(this, message + (e == null ? "" : ": " + e.getMessage()), "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
}
