package view.staff;

import model.Drink;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import service.DrinkService;
import service.CategoryService;
import model.Category;
import view.layout.UITheme;

import java.awt.*;
import java.awt.event.ActionEvent;

public class DrinkPanel extends JPanel {

    private JTable table;
    private DefaultTableModel model;
    private final DrinkService drinkService = new DrinkService();
    private final CategoryService categoryService = new CategoryService();
    private List<Category> categories = new ArrayList<>();

    public DrinkPanel() {

        setLayout(new BorderLayout());

        setBackground(UITheme.BACKGROUND);

        // =========================
        // HEADER
        // =========================

        JPanel header = new JPanel(
                new BorderLayout());

        header.setBackground(UITheme.BACKGROUND);

        JLabel title = new JLabel("Quản lý đồ uống");

        title.setForeground(UITheme.TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));

        JButton addButton = new JButton("Thêm đồ uống");

        header.setBorder(
                BorderFactory.createEmptyBorder(
                        20, 20, 20, 20));

        header.add(
                title,
                BorderLayout.WEST);

        header.add(
                addButton,
                BorderLayout.EAST);
        addButton.addActionListener(e -> addDrink());

        add(
                header,
                BorderLayout.NORTH);

        // =========================
        // TABLE
        // =========================

        String[] columns = {
                "ID",
                "Tên đồ uống",
                "Danh mục",
                "Giá",
                "Tồn kho",
                "Trạng thái",
                "Thao tác"
        };

        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 6;
            }
        };

        table = new JTable(model);
        table.getColumnModel().getColumn(6).setCellRenderer(new ActionRenderer());
        table.getColumnModel().getColumn(6).setCellEditor(new ActionEditor());
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(6).setMinWidth(150);
        table.getColumnModel().getColumn(6).setPreferredWidth(150);
        table.setRowHeight(44);

        JScrollPane tableScrollPane = new JScrollPane(table);
        tableScrollPane.setBorder(
                BorderFactory.createEmptyBorder(
                        16, 16, 16, 16));// border

        JButton toggle = new JButton("Đổi trạng thái");
        toggle.addActionListener(e -> toggleDrinkStatus(table.getSelectedRow()));
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(toggle);
        add(tableScrollPane, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        UITheme.styleTree(this);
        loadDrinks();
    }

    private void loadDrinks() {

        try {
            categories = categoryService.getAll();
            if (categories == null) {
                categories = new ArrayList<>();
            }
            List<Drink> drinks = drinkService.getAll();
            model.setRowCount(0);

            for (Drink drink : drinks) {

                model.addRow(new Object[] {
                        drink.getId(),
                        drink.getName(),
                        categoryName(drink.getCategoryId()),
                        drink.getPrice(),
                        drink.getQuantity(),
                        drink.isActive()
                                ? "Đang bán"
                                : "Ngừng bán",
                        ""

                });
            }

        } catch (Exception e) {

            e.printStackTrace();

            JOptionPane.showMessageDialog(
                    this,
                    "Không tải được đồ uống!");
        }
    }

    private String categoryName(int categoryId) {
        return categories.stream()
                .filter(category -> category.getId() == categoryId)
                .map(Category::getName).findFirst().orElse("Chưa phân loại");
    }

    private void addDrink() {
        JTextField name = new JTextField();
        JTextField price = new JTextField();
        JTextField quantity = new JTextField("0");
        JComboBox<Category> categoryCombo = new JComboBox<>(categories.toArray(new Category[0]));
        if (categories.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chưa có danh mục để chọn.", "Không thể thêm",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Tên đồ uống:")); form.add(name);
        form.add(new JLabel("Giá:")); form.add(price);
        form.add(new JLabel("Tồn kho:")); form.add(quantity);
        form.add(new JLabel("Danh mục:")); form.add(categoryCombo);
        if (JOptionPane.showConfirmDialog(this, form, "Thêm đồ uống",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            Drink drink = new Drink();
            drink.setName(name.getText().trim());
            drink.setPrice(Double.parseDouble(price.getText().trim()));
            drink.setQuantity(Integer.parseInt(quantity.getText().trim()));
            Category category = (Category) categoryCombo.getSelectedItem();
            if (category == null) {
                throw new IllegalArgumentException("Phải chọn danh mục");
            }
            drink.setCategoryId(category.getId());
            drink.setActive(true);
            if (drink.getName().isEmpty() || drink.getPrice() < 0 || drink.getQuantity() < 0) {
                throw new IllegalArgumentException("Dữ liệu không hợp lệ");
            }
            drinkService.create(drink);
            loadDrinks();
        } catch (Exception exception) {
            showApiError("Không thể thêm đồ uống", exception);
        }
    }

    private void editDrink(int row) {
        int id = (int) model.getValueAt(row, 0);
        JTextField nameField = new JTextField((String) model.getValueAt(row, 1));
        JTextField priceField = new JTextField(String.valueOf(model.getValueAt(row, 3)));
        JTextField quantityField = new JTextField(String.valueOf(model.getValueAt(row, 4)));

        Drink existingDrink;
        try {
            existingDrink = drinkService.getAll().stream()
                    .filter(drink -> drink.getId() == id)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy đồ uống"));
        } catch (Exception exception) {
            showApiError("Không thể tải đồ uống", exception);
            return;
        }

        JComboBox<Category> categoryCombo = new JComboBox<>(categories.toArray(new Category[0]));
        for (int index = 0; index < categoryCombo.getItemCount(); index++) {
            if (categoryCombo.getItemAt(index).getId() == existingDrink.getCategoryId()) {
                categoryCombo.setSelectedIndex(index);
                break;
            }
        }
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Tên đồ uống:"));
        form.add(nameField);
        form.add(new JLabel("Giá:"));
        form.add(priceField);
        form.add(new JLabel("Tồn kho:"));
        form.add(quantityField);
        form.add(new JLabel("Danh mục:"));
        form.add(categoryCombo);

        int result = JOptionPane.showConfirmDialog(
                this, form, "Sửa đồ uống", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            Drink drink = new Drink();
            drink.setId(id);
            Category category = (Category) categoryCombo.getSelectedItem();
            if (category == null) {
                throw new IllegalArgumentException("Phải chọn danh mục");
            }
            drink.setCategoryId(category.getId());
            drink.setName(nameField.getText().trim());
            drink.setPrice(Double.parseDouble(priceField.getText().trim()));
            drink.setQuantity(Integer.parseInt(quantityField.getText().trim()));
            drink.setActive("Đang bán".equals(model.getValueAt(row, 5).toString()));
            drink.setImage(existingDrink.getImage());

            if (drink.getName().isEmpty() || drink.getPrice() < 0 || drink.getQuantity() < 0) {
                throw new IllegalArgumentException("Dữ liệu không hợp lệ");
            }

            drinkService.update(drink);
            loadDrinks();
            JOptionPane.showMessageDialog(this, "Đã cập nhật đồ uống.");
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "Giá và tồn kho phải là số không âm.");
        } catch (Exception e) {
            showApiError("Không thể cập nhật đồ uống", e);
        }
    }

    private void toggleDrinkStatus(int row) {
        if (row < 0) {
            showApiError("Hãy chọn đồ uống", null);
            return;
        }
        int id = (int) model.getValueAt(row, 0);
        try {
            Drink drink = drinkService.getAll().stream()
                    .filter(item -> item.getId() == id)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy đồ uống"));
            drink.setActive(!drink.isActive());
            drinkService.update(drink);
            loadDrinks();
        } catch (Exception exception) {
            showApiError("Không thể đổi trạng thái đồ uống", exception);
        }
    }

    private void deleteDrink(int row) {
        int id = (int) model.getValueAt(row, 0);
        int result = JOptionPane.showConfirmDialog(
                this,
                "Bạn có chắc muốn xóa đồ uống này?",
                "Xác nhận xóa",
                JOptionPane.YES_NO_OPTION);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            drinkService.delete(id);
            loadDrinks();
            JOptionPane.showMessageDialog(this, "Đã xóa đồ uống.");
        } catch (Exception e) {
            showApiError("Không thể xóa đồ uống", e);
        }
    }

    private void showApiError(String message, Exception exception) {
        if (exception != null) {
            exception.printStackTrace();
        }
        JOptionPane.showMessageDialog(this, message
                + (exception == null ? "" : ": " + exception.getMessage()));
    }

    private class ActionRenderer extends JPanel implements javax.swing.table.TableCellRenderer {
        private final JButton editButton = new JButton("Sửa");
        private final JButton deleteButton = new JButton("Xóa");

        ActionRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 4, 2));
            setOpaque(true);
            add(editButton);
            add(deleteButton);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected, boolean focused,
                int row, int column) {
            setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            return this;
        }
    }

    private class ActionEditor extends AbstractCellEditor
            implements javax.swing.table.TableCellEditor {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        private int row;

        ActionEditor() {
            JButton editButton = new JButton("Sửa");
            JButton deleteButton = new JButton("Xóa");
            editButton.addActionListener((ActionEvent e) -> {
                fireEditingStopped();
                editDrink(table.convertRowIndexToModel(row));
            });
            deleteButton.addActionListener((ActionEvent e) -> {
                fireEditingStopped();
                deleteDrink(table.convertRowIndexToModel(row));
            });
            panel.add(editButton);
            panel.add(deleteButton);
        }

        @Override
        public Component getTableCellEditorComponent(
                JTable table, Object value, boolean selected, int row, int column) {
            this.row = row;
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return "";
        }
    }
}
