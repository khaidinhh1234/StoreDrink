package view.staff;

import view.layout.NotificationDialog;

import model.Drink;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import service.DrinkService;
import service.ToppingService;
import view.layout.DataSync;
import view.layout.RefreshablePanel;
import view.layout.UITheme;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Locale;
import javax.swing.filechooser.FileNameExtensionFilter;

public class DrinkPanel extends JPanel implements RefreshablePanel {

    private JTable table;
    private DefaultTableModel model;
    private final DrinkService drinkService = new DrinkService();

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
        JButton refreshButton = new JButton("Làm mới");

        header.setBorder(
                BorderFactory.createEmptyBorder(
                        20, 20, 20, 20));

        header.add(
                title,
                BorderLayout.WEST);

        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerActions.setOpaque(false);
        headerActions.add(refreshButton);
        headerActions.add(addButton);
        header.add(headerActions, BorderLayout.EAST);
        addButton.addActionListener(e -> addDrink());
        refreshButton.addActionListener(e -> loadDrinks());

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
        UITheme.styleButton(addButton, UITheme.Action.CREATE);
        UITheme.styleButton(refreshButton, UITheme.Action.NEUTRAL);
        table.getColumnModel().getColumn(2).setCellRenderer(new CurrencyRenderer());
        loadDrinks();
        DataSync.register(this, this);
    }

    @Override public void refreshData() {
        if (!table.isEditing()) loadDrinks();
    }

    private void loadDrinks() {

        try {
            List<Drink> drinks = drinkService.getAll();
            model.setRowCount(0);

            for (Drink drink : drinks) {

                model.addRow(new Object[] {
                        drink.getId(),
                        drink.getName(),
                        UITheme.formatVnd(drink.getPrice()),
                        drink.getQuantity(),
                        drink.isActive()
                                ? "Đang bán"
                                : "Ngừng bán",
                        ""

                });
            }

        } catch (Exception e) {

            e.printStackTrace();

            NotificationDialog.showMessageDialog(
                    this,
                    "Không tải được đồ uống!");
        }
    }

    private void addDrink() {
        try {
            DrinkForm editor=new DrinkForm(null);
            if(NotificationDialog.showConfirmDialog(this,editor.scrollPane(),"Thêm đồ uống",
                    NotificationDialog.OK_CANCEL_OPTION)!=NotificationDialog.OK_OPTION)return;
            Drink drink=editor.read(new Drink());
            validateDrink(drink);
            List<model.Topping> toppingOptions=editor.selectedToppings();
            Drink saved=drinkService.create(drink);
            try {
                new ToppingService().setForDrink(saved.getId(),toppingOptions);
            } catch (Exception toppingFailure) {
                loadDrinks();
                showApiError("Đồ uống #" + saved.getId()
                        + " đã được tạo nhưng chưa gắn được topping. Hãy mở Sửa đồ uống để thử lại.",
                        toppingFailure);
                return;
            }
            loadDrinks();
            NotificationDialog.showSuccess(this,"Đã thêm đồ uống và cấu hình size, topping.");
        }catch(Exception exception){showApiError("Không thể thêm đồ uống",exception);}
    }

    private void validateDrink(Drink drink) {
        if(drink.getName().isBlank()||!Double.isFinite(drink.getPrice())||drink.getPrice()<0
                ||drink.getQuantity()<0)
            throw new IllegalArgumentException("Vui lòng nhập tên, giá và tồn kho hợp lệ.");
        if (drink.getAvailableSizes().size() > 3) {
            throw new IllegalArgumentException("Mỗi món chỉ được cấu hình tối đa 3 size, tính cả Size S.");
        }
    }

    private final class DrinkForm {
        final JPanel panel=new JPanel(); final JTextField name=new JTextField(24),price=new JTextField(12),
                quantity=new JTextField(8),image=new JTextField(24);
        final JTextArea description=new JTextArea(3,24);
        final JPanel sizeRows=new JPanel(), toppingRows=new JPanel();
        final JButton addSizeButton=new JButton("+ Thêm size"), addToppingButton=new JButton("+ Tạo topping mới"),
                chooseToppingButton=new JButton("Chọn topping đã tạo");
        final List<SizeRow> sizes=new ArrayList<>();
        final List<ToppingRow> toppings=new ArrayList<>();
        final List<model.Topping> originalToppings;
        DrinkForm(Drink existing)throws Exception {
            panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));panel.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));
            sectionLabel("THÔNG TIN CƠ BẢN");
            row("Tên đồ uống",name);row("Giá Size S (đ)",price);row("Tồn kho",quantity);
            row("Mô tả",new JScrollPane(description));row("Hình ảnh",imageInput(image));
            sectionLabel("CẤU HÌNH SIZE");
            row("Size mặc định",new JLabel("S · giá nhập ở trên"));
            sizeRows.setLayout(new BoxLayout(sizeRows,BoxLayout.Y_AXIS));
            panel.add(sizeRows); addSizeButton.addActionListener(e->addSizeRow(null));
            panel.add(addSizeButton);
            sectionLabel("TOPPING ÁP DỤNG CHO MÓN (TÙY CHỌN)");
            toppingRows.setLayout(new BoxLayout(toppingRows,BoxLayout.Y_AXIS));
            panel.add(toppingRows); addToppingButton.addActionListener(e->addToppingRow(null));
            chooseToppingButton.addActionListener(e->chooseExistingTopping());
            JPanel toppingActions=new JPanel(new FlowLayout(FlowLayout.LEFT,6,2));
            toppingActions.setOpaque(false);toppingActions.add(addToppingButton);toppingActions.add(chooseToppingButton);
            panel.add(toppingActions);
            if(existing!=null){
                name.setText(existing.getName());price.setText(String.valueOf(existing.getPrice()));quantity.setText(String.valueOf(existing.getQuantity()));
                image.setText(existing.getImage()==null?"":existing.getImage());description.setText(existing.getDescription()==null?"":existing.getDescription());
                List<Drink.SizeOption> configuredSizes=existing.getAvailableSizes();
                for(int i=1;i<configuredSizes.size();i++)addSizeRow(configuredSizes.get(i));
                originalToppings=List.copyOf(new ToppingService().getForDrink(existing.getId()));
                for(model.Topping topping:originalToppings)addToppingRow(topping);
            }else{quantity.setText("0");originalToppings=List.of();}
            updateSizeLimit(); updateToppingLimit();
        }
        JScrollPane scrollPane(){
            JScrollPane scroll=new JScrollPane(panel);
            scroll.setPreferredSize(new Dimension(590,500));
            scroll.getVerticalScrollBar().setUnitIncrement(18);
            return scroll;
        }
        void sectionLabel(String text){JLabel label=new JLabel(text);label.setFont(new Font("Segoe UI",Font.BOLD,13));label.setForeground(UITheme.PRIMARY);label.setBorder(BorderFactory.createEmptyBorder(10,0,6,0));panel.add(label);}
        void row(String label,Component field){JPanel row=new JPanel(new BorderLayout(12,4));row.setOpaque(false);JLabel caption=new JLabel(label);caption.setPreferredSize(new Dimension(145,28));row.add(caption,BorderLayout.WEST);row.add(field,BorderLayout.CENTER);row.setBorder(BorderFactory.createEmptyBorder(2,0,4,0));panel.add(row);}
        void addSizeRow(Drink.SizeOption option){
            if(sizes.size()>=2)return;
            SizeRow size=new SizeRow(option);sizes.add(size);sizeRows.add(size.panel);updateSizeLimit();refreshFormLayout(size.panel);
        }
        void addToppingRow(model.Topping topping){
            if(toppings.size()>=3)return;
            ToppingRow row=new ToppingRow(topping);toppings.add(row);toppingRows.add(row.panel);updateToppingLimit();refreshFormLayout(row.panel);
        }
        void chooseExistingTopping(){
            if(toppings.size()>=3){
                NotificationDialog.showMessageDialog(panel,"Mỗi món chỉ được thêm tối đa 3 topping.","Đã đủ topping",NotificationDialog.INFORMATION_MESSAGE);
                return;
            }
            try{
                List<model.Topping> available=new ToppingService().getAll().stream()
                        .filter(option->toppings.stream().noneMatch(row->row.name.getText().trim().equalsIgnoreCase(option.getName())
                                && row.price.getText().trim().equals(String.valueOf(option.getPrice()))))
                        .toList();
                if(available.isEmpty()){
                    NotificationDialog.showMessageDialog(panel,"Không còn topping nào khác để chọn.","Danh sách topping",NotificationDialog.INFORMATION_MESSAGE);
                    return;
                }
                List<String> labels=available.stream().map(option->option.getName()+" · "+UITheme.formatVnd(option.getPrice())
                        +(option.isActive()?"":" (đang ẩn, sẽ bật lại khi lưu)")).toList();
                Object selected=JOptionPane.showInputDialog(panel,"Chọn topping đã có trong hệ thống:","Chọn topping",
                        JOptionPane.PLAIN_MESSAGE,null,labels.toArray(),labels.get(0));
                int index=labels.indexOf(selected);
                if(index>=0)addToppingRow(available.get(index));
            }catch(Exception exception){showApiError("Không tải được danh sách topping",exception);}
        }
        void refreshFormLayout(JComponent addedRow){
            sizeRows.revalidate();sizeRows.repaint();toppingRows.revalidate();toppingRows.repaint();
            panel.revalidate();panel.repaint();
            JScrollPane scroll=(JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class,panel);
            if(scroll==null)return;
            scroll.revalidate();scroll.getViewport().revalidate();
            SwingUtilities.invokeLater(()->{
                scroll.revalidate();scroll.doLayout();scroll.getViewport().doLayout();
                panel.revalidate();panel.doLayout();
                Rectangle bounds=SwingUtilities.convertRectangle(addedRow.getParent(),addedRow.getBounds(),panel);
                panel.scrollRectToVisible(bounds);
                Window window=SwingUtilities.getWindowAncestor(scroll);
                if(window!=null)window.validate();
            });
        }
        void updateSizeLimit(){addSizeButton.setEnabled(sizes.size()<2);addSizeButton.setToolTipText(sizes.size()>=2?"Tối đa 3 size, tính cả Size S.":null);}
        void updateToppingLimit(){addToppingButton.setEnabled(toppings.size()<3);addToppingButton.setToolTipText(toppings.size()>=3?"Mỗi món tối đa 3 topping.":null);}
        Drink read(Drink d){
            d.setName(name.getText().trim());d.setPrice(Double.parseDouble(price.getText().trim()));
            d.setQuantity(Integer.parseInt(quantity.getText().trim()));d.setImage(image.getText().trim());d.setDescription(description.getText().trim());
            List<Drink.SizeOption> configured=new ArrayList<>();configured.add(new Drink.SizeOption("S",d.getPrice()));
            java.util.Set<String> names=new java.util.HashSet<>();names.add("s");
            for(SizeRow row:sizes){String sizeName=row.name.getText().trim();String extraText=row.price.getText().trim();
                if(sizeName.isBlank()&&extraText.isBlank())continue;
                if(sizeName.isBlank()||extraText.isBlank())throw new IllegalArgumentException("Hãy nhập đủ tên size và phụ thu, hoặc xóa dòng size còn trống.");
                double extra=Double.parseDouble(extraText);
                if(!Double.isFinite(extra)||extra<0||!names.add(sizeName.toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Tên size phải riêng biệt và phụ thu phải là số không âm.");
                configured.add(new Drink.SizeOption(sizeName,extra));}
            d.setSizes(configured);Drink.SizeOption legacyLarge=configured.stream().filter(s->"l".equalsIgnoreCase(s.getName())).findFirst().orElse(null);
            d.setHasLargeSize(legacyLarge!=null);d.setLargeExtraPrice(legacyLarge==null?0:legacyLarge.getPrice());
            return d;
        }
        List<model.Topping> selectedToppings(){
            if(toppings.size()>3)throw new IllegalArgumentException("Mỗi món chỉ được thêm tối đa 3 topping.");
            List<model.Topping> selected=new ArrayList<>();java.util.Set<String> names=new java.util.HashSet<>();
            for(ToppingRow row:toppings){String toppingName=row.name.getText().trim();String priceText=row.price.getText().trim();
                if(toppingName.isBlank()&&priceText.isBlank())continue;
                if(toppingName.isBlank()||priceText.isBlank())throw new IllegalArgumentException("Hãy nhập đủ tên topping và giá, hoặc xóa dòng topping còn trống.");
                double toppingPrice=Double.parseDouble(priceText);
                if(!Double.isFinite(toppingPrice)||toppingPrice<0||!names.add(toppingName.toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Tên topping phải riêng biệt và giá phải hợp lệ.");
                selected.add(new model.Topping(toppingName,toppingPrice));}
            return selected;
        }
        final class SizeRow {
            final JPanel panel=new JPanel(new FlowLayout(FlowLayout.LEFT,6,2));
            final JTextField name=new JTextField(8),price=new JTextField(9);
            SizeRow(Drink.SizeOption option){
                name.setToolTipText("Tên size, ví dụ M hoặc L");price.setToolTipText("Khoản cộng thêm trên giá Size S");
                if(option!=null){name.setText(option.getName());price.setText(String.valueOf(Math.max(0,option.getPrice()-DrinkForm.this.basePrice())));}
                panel.add(new JLabel("Tên"));panel.add(name);panel.add(new JLabel("Phụ thu (đ)"));panel.add(price);
                JButton remove=new JButton("Xóa");remove.addActionListener(e->{sizes.remove(this);sizeRows.remove(panel);updateSizeLimit();refreshFormLayout(DrinkForm.this.addSizeButton);});panel.add(remove);
            }
        }
        double basePrice(){try{return Double.parseDouble(price.getText().trim());}catch(NumberFormatException ignored){return 0;}}
        final class ToppingRow {
            final JPanel panel=new JPanel(new FlowLayout(FlowLayout.LEFT,6,2));
            final JTextField name=new JTextField(10),price=new JTextField(9);
            ToppingRow(model.Topping topping){
                name.setToolTipText("Tên topping");price.setToolTipText("Giá topping");
                if(topping!=null){name.setText(topping.getName());price.setText(String.valueOf(topping.getPrice()));}
                panel.add(new JLabel("Tên"));panel.add(name);panel.add(new JLabel("Giá (đ)"));panel.add(price);
                JButton remove=new JButton("Xóa");remove.addActionListener(e->{toppings.remove(this);toppingRows.remove(panel);updateToppingLimit();refreshFormLayout(DrinkForm.this.addToppingButton);});panel.add(remove);
            }
        }
    }

    private JPanel imageInput(JTextField image) {
        JPanel input = new JPanel(new BorderLayout(8, 0));
        input.add(image, BorderLayout.CENTER);
        JButton browse = new JButton("Chọn ảnh");
        UITheme.styleButton(browse, UITheme.Action.NEUTRAL);
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
        try {
            String originalSnapshot = new com.google.gson.Gson().toJson(existingDrink);
            DrinkForm editor=new DrinkForm(existingDrink);
            int result=NotificationDialog.showConfirmDialog(this,editor.scrollPane(),"Sửa đồ uống",NotificationDialog.OK_CANCEL_OPTION);
            if(result!=NotificationDialog.OK_OPTION)return;
            Drink originalDrink = new com.google.gson.Gson().fromJson(originalSnapshot, Drink.class);
            Drink drink=editor.read(existingDrink);
            drink.setId(id);drink.setCategoryId(existingDrink.getCategoryId());drink.setActive(existingDrink.isActive());
            validateDrink(drink);
            List<model.Topping> toppingOptions=editor.selectedToppings();
            boolean drinkChanged=!sameDrink(originalDrink,drink);
            boolean toppingsChanged=!sameToppings(editor.originalToppings,toppingOptions);
            if(!drinkChanged&&!toppingsChanged){
                NotificationDialog.showMessageDialog(this,"Bạn chưa thay đổi thông tin đồ uống.","Không có thay đổi",NotificationDialog.INFORMATION_MESSAGE);
                return;
            }
            try {
                if(drinkChanged)drinkService.update(drink);
                if(toppingsChanged)new ToppingService().setForDrink(id,toppingOptions);
            } catch (Exception updateFailure) {
                if(drinkChanged){
                    try { drinkService.update(originalDrink); }
                    catch (Exception rollbackFailure) { updateFailure.addSuppressed(rollbackFailure); }
                }
                throw updateFailure;
            }
            loadDrinks();
            NotificationDialog.showSuccess(this, "Đã cập nhật đồ uống, size và topping.");
        } catch (IllegalArgumentException e) {
            NotificationDialog.showError(this,
                    "Giá, tồn kho hoặc cấu hình topping không hợp lệ: " + e.getMessage());
        } catch (Exception e) {
            showApiError("Không thể cập nhật đồ uống", e);
        }
    }

    private boolean sameDrink(Drink left,Drink right){
        if(left==null||right==null||!java.util.Objects.equals(left.getName(),right.getName())
                ||Double.compare(left.getPrice(),right.getPrice())!=0||left.getQuantity()!=right.getQuantity()
                ||left.getCategoryId()!=right.getCategoryId()||left.isActive()!=right.isActive()
                ||!java.util.Objects.equals(left.getImage(),right.getImage())
                ||!java.util.Objects.equals(left.getDescription(),right.getDescription()))return false;
        List<Drink.SizeOption> a=left.getAvailableSizes(),b=right.getAvailableSizes();
        if(a.size()!=b.size())return false;
        for(int i=0;i<a.size();i++)if(!java.util.Objects.equals(a.get(i).getName(),b.get(i).getName())
                ||Math.abs(a.get(i).getPrice()-b.get(i).getPrice())>0.001)return false;
        return true;
    }

    private boolean sameToppings(List<model.Topping> left,List<model.Topping> right){
        if(left.size()!=right.size())return false;
        java.util.function.Function<model.Topping,String> key=topping->topping.getName().trim().toLowerCase(Locale.ROOT)
                +"|"+java.math.BigDecimal.valueOf(topping.getPrice()).stripTrailingZeros().toPlainString();
        return left.stream().map(key).sorted().toList().equals(right.stream().map(key).sorted().toList());
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
        NotificationDialog.showMessageDialog(this, message
                + (exception == null ? "" : ": " + exception.getMessage()));
    }

    private void styleActionButton(JButton button, UITheme.Action action) {
        UITheme.styleButton(button, action);
        button.setFont(new Font("Segoe UI", Font.BOLD, 11));
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
            setText(value instanceof Number number
                    ? UITheme.formatVnd(number.doubleValue()) : String.valueOf(value));
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
            styleActionButton(editButton, UITheme.Action.EDIT);
            styleActionButton(toggleButton, UITheme.Action.TOGGLE);
            setPreferredSize(new Dimension(195, 42));
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
            styleActionButton(editButton, UITheme.Action.EDIT);
            styleActionButton(toggleButton, UITheme.Action.TOGGLE);
            editButton.setPreferredSize(new Dimension(56, 32));
            toggleButton.setPreferredSize(new Dimension(112, 32));
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
            for (Component component : panel.getComponents()) component.setPreferredSize(
                    component == panel.getComponent(0) ? new Dimension(56, 32) : new Dimension(112, 32));
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return "";
        }
    }
}
