package view.admin;

import view.layout.NotificationDialog;

import model.StoreTable;
import service.TableService;
import view.layout.DataSync;
import view.layout.RefreshablePanel;
import view.layout.UITheme;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class TablePanel extends JPanel implements RefreshablePanel {
    private final TableService service = new TableService();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Mã bàn", "Tên bàn", "Mã QR", "Trạng thái", "Thao tác"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 4;
        }
    };
    private JTable table;
    public TablePanel() {
        setLayout(new BorderLayout()); setBackground(UITheme.BACKGROUND);
        JLabel title = new JLabel("Quản lý bàn"); title.setForeground(UITheme.TEXT); title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        JPanel header = new JPanel(new BorderLayout()); header.setBackground(UITheme.BACKGROUND); header.setBorder(BorderFactory.createEmptyBorder(24, 28, 18, 28));
        JButton add = new JButton("Thêm bàn");
        add.addActionListener(e -> addTable());
        header.add(title, BorderLayout.WEST); header.add(add, BorderLayout.EAST); add(header, BorderLayout.NORTH);
        table = new JTable(model);
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
        table.getColumnModel().getColumn(4).setCellRenderer(new TableActionRenderer());
        table.getColumnModel().getColumn(4).setCellEditor(new TableActionEditor());
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(UITheme.CARD);
        add(scrollPane, BorderLayout.CENTER);
        UITheme.styleTree(this);
        UITheme.styleButton(add, UITheme.Action.CREATE);
        load();
        DataSync.register(this, this);
    }

    @Override public void refreshData() { if (!table.isEditing()) load(); }
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
            t.setStatus("Đang hoạt động".equalsIgnoreCase(t.getStatus())
                    ? "Ngừng hoạt động" : "Đang hoạt động");
            service.update(t); load();
        } catch (Exception e) { error("Không thể cập nhật trạng thái bàn", e); }
    }
    private void addTable() {
        JTextField name = new JTextField();
        JTextField qrCode = new JTextField();
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Tên bàn:")); form.add(name);
        form.add(new JLabel("Mã QR:")); form.add(qrCode);
        if (NotificationDialog.showConfirmDialog(this, form, "Thêm bàn",
                NotificationDialog.OK_CANCEL_OPTION) != NotificationDialog.OK_OPTION) return;
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

    private class TableActionRenderer extends JButton implements TableCellRenderer {
        TableActionRenderer() {
            setFocusPainted(false);
            setForeground(Color.WHITE);
            UITheme.styleButton(this, UITheme.Action.TOGGLE);
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable source, Object value,
                boolean selected, boolean focused, int row, int column) {
            boolean active = "Đang hoạt động".equalsIgnoreCase(String.valueOf(source.getValueAt(row, 3)));
            setText(active ? "Ngừng hoạt động" : "Kích hoạt");
            UITheme.styleButton(this, active ? UITheme.Action.TOGGLE : UITheme.Action.CREATE);
            return this;
        }
    }

    private class TableActionEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button = new JButton();
        private int viewRow;

        TableActionEditor() {
            UITheme.styleButton(button, UITheme.Action.TOGGLE);
            button.setFont(new Font("Segoe UI", Font.BOLD, 12));
            button.addActionListener(event -> {
                fireEditingStopped();
                toggleSelected(table.convertRowIndexToModel(viewRow));
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable source, Object value,
                boolean selected, int row, int column) {
            viewRow = row;
            boolean active = "Đang hoạt động".equalsIgnoreCase(String.valueOf(source.getValueAt(row, 3)));
            button.setText(active ? "Ngừng hoạt động" : "Kích hoạt");
            UITheme.styleButton(button, active ? UITheme.Action.TOGGLE : UITheme.Action.CREATE);
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return "";
        }
    }

    private void error(String message, Exception e) {
        NotificationDialog.showMessageDialog(this, message + (e == null ? "" : ": " + e.getMessage()), "Lỗi", NotificationDialog.ERROR_MESSAGE);
    }
}
