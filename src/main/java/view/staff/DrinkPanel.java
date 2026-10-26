package view.staff;

import model.Drink;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import service.DrinkService;
import view.layout.UITheme;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.NumberFormat;
import java.util.Locale;
import javax.swing.filechooser.FileNameExtensionFilter;

public class DrinkPanel extends JPanel {

    private JTable table;
    private DefaultTableModel model;
    private final DrinkService drinkService = new DrinkService();
    private final NumberFormat currency = createVndFormat();

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
                "Giá",
                "Tồn kho",
                "Trạng thái",
                "Thao tác"
        };

        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 5;
            }
        };

        table = new JTable(model);
        table.getColumnModel().getColumn(5).setCellRenderer(new ActionRenderer());
        table.getColumnModel().getColumn(5).setCellEditor(new ActionEditor());
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.getColumnModel().getColumn(5).setMinWidth(195);
        table.getColumnModel().getColumn(5).setPreferredWidth(195);
        table.setRowHeight(44);

        JScrollPane tableScrollPane = new JScrollPane(table);
        tableScrollPane.setBorder(
                BorderFactory.createEmptyBorder(
                        16, 16, 16, 16));// border

        add(tableScrollPane, BorderLayout.CENTER);
        UITheme.styleTree(this);
        table.getColumnModel().getColumn(2).setCellRenderer(new CurrencyRenderer());
        loadDrinks();
    }

    private void loadDrinks() {

        try {
            List<Drink> drinks = drinkService.getAll();
            model.setRowCount(0);

            for (Drink drink : drinks) {

                model.addRow(new Object[] {
                        drink.getId(),
                        drink.getName(),
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

    private void addDrink() {
        JTextField name = new JTextField();
        JTextField price = new JTextField();
        JTextField quantity = new JTextField("0");
        JTextField image = new JTextField();
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Tên đồ uống:")); form.add(name);
        form.add(new JLabel("Giá:")); form.add(price);
        form.add(new JLabel("Tồn kho:")); form.add(quantity);
        form.add(new JLabel("Ảnh (URL hoặc tệp):")); form.add(imageInput(image));
        if (JOptionPane.showConfirmDialog(this, form, "Thêm đồ uống",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            Drink drink = new Drink();
            drink.setName(name.getText().trim());
            drink.setPrice(Double.parseDouble(price.getText().trim()));
            drink.setQuantity(Integer.parseInt(quantity.getText().trim()));
            drink.setImage(image.getText().trim());
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

    private JPanel imageInput(JTextField image) {
        JPanel input = new JPanel(new BorderLayout(8, 0));
        input.add(image, BorderLayout.CENTER);
        JButton browse = new JButton("Chọn ảnh");
        browse.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Chọn ảnh đồ uống");
            chooser.setFileFilter(new FileNameExtensionFilter(
                    "Ảnh (PNG, JPG, JPEG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                image.setText(chooser.getSelectedFile().toURI().toString());
            }
        });
        input.add(browse, BorderLayout.EAST);
        return input;
    }

    private void editDrink(int row) {
        int id = (int) model.getValueAt(row, 0);
        JTextField nameField = new JTextField((String) model.getValueAt(row, 1));
        JTextField priceField = new JTextField(String.valueOf(model.getValueAt(row, 2)));
        JTextField quantityField = new JTextField(String.valueOf(model.getValueAt(row, 3)));

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
        JTextField imageField = new JTextField(existingDrink.getImage() == null ? "" : existingDrink.getImage());

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Tên đồ uống:"));
        form.add(nameField);
        form.add(new JLabel("Giá:"));
        form.add(priceField);
        form.add(new JLabel("Tồn kho:"));
        form.add(quantityField);
        form.add(new JLabel("Ảnh (URL hoặc tệp):"));
        form.add(imageInput(imageField));

        int result = JOptionPane.showConfirmDialog(
                this, form, "Sửa đồ uống", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            Drink drink = new Drink();
            drink.setId(id);
            drink.setCategoryId(existingDrink.getCategoryId());
            drink.setName(nameField.getText().trim());
            drink.setPrice(Double.parseDouble(priceField.getText().trim()));
            drink.setQuantity(Integer.parseInt(quantityField.getText().trim()));
            drink.setActive("Đang bán".equals(model.getValueAt(row, 4).toString()));
            drink.setImage(imageField.getText().trim());

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

    private void showApiError(String message, Exception exception) {
        if (exception != null) {
            exception.printStackTrace();
        }
        JOptionPane.showMessageDialog(this, message
                + (exception == null ? "" : ": " + exception.getMessage()));
    }

    private void styleActionButton(JButton button, Color color) {
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 11));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    }

    private static NumberFormat createVndFormat() {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(0);
        return format;
    }

    private class CurrencyRenderer extends javax.swing.table.DefaultTableCellRenderer {
        CurrencyRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 14));
        }

        @Override
        public Component getTableCellRendererComponent(JTable source, Object value,
                boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(source, value, selected, focused, row, column);
            double amount = value instanceof Number number ? number.doubleValue()
                    : Double.parseDouble(String.valueOf(value));
            setText(currency.format(amount) + " đ");
            setForeground(UITheme.TEXT);
            setBackground(selected ? source.getSelectionBackground() : source.getBackground());
            return this;
        }
    }

    private class ActionRenderer extends JPanel implements javax.swing.table.TableCellRenderer {
        private final JButton editButton = new JButton("Sửa");
        private final JButton toggleButton = new JButton("Bật / tắt bán");

        ActionRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 4, 2));
            setOpaque(true);
            styleActionButton(editButton, UITheme.PRIMARY);
            styleActionButton(toggleButton, new Color(48, 58, 92));
            add(editButton);
            add(toggleButton);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected, boolean focused,
                int row, int column) {
            setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            revalidate();
            doLayout();
            return this;
        }
    }

    private class ActionEditor extends AbstractCellEditor
            implements javax.swing.table.TableCellEditor {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        private int row;

        ActionEditor() {
            JButton editButton = new JButton("Sửa");
            JButton toggleButton = new JButton("Bật / tắt bán");
            styleActionButton(editButton, UITheme.PRIMARY);
            styleActionButton(toggleButton, new Color(48, 58, 92));
            editButton.addActionListener((ActionEvent e) -> {
                fireEditingStopped();
                editDrink(table.convertRowIndexToModel(row));
            });
            toggleButton.addActionListener((ActionEvent e) -> {
                fireEditingStopped();
                toggleDrinkStatus(table.convertRowIndexToModel(row));
            });
            panel.add(editButton);
            panel.add(toggleButton);
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
