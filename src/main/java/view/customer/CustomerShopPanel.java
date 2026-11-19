package view.customer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.JOptionPane;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import model.Drink;
import model.OrderDetail;
import model.User;
import service.DrinkService;
import service.OrderService;
import service.CashierRequestService;
import service.InventoryService;
import service.ToppingService;
import view.layout.DataSync;
import view.layout.NotificationDialog;
import view.layout.RefreshablePanel;
import view.layout.UITheme;
public class CustomerShopPanel extends JPanel implements RefreshablePanel {
    private final User user;
    private final boolean staffTakeaway;
    private final boolean guestOrder;
    private final Integer tableId;
    private final DrinkService drinkService = new DrinkService();
    private final OrderService orderService = new OrderService();
    private final InventoryService inventoryService = new InventoryService();
    private final List<Drink> drinks = new ArrayList<>();
    private Map<Integer, Integer> availableQuantities = Map.of();
    private final List<CartItem> cart = new ArrayList<>();
    private final DefaultTableModel cartModel;
    private final JTable cartTable;
    private final JPanel drinkGrid = new JPanel(new GridLayout(0, 4, 10, 10));
    private final JLabel totalLabel = new JLabel();
    private final NumberFormat currency = UITheme.newVndFormatter();
    private final JTextArea receiptArea = new JTextArea();
    private JTextField customerNameField;
    private JTextField customerPhoneField;

    public CustomerShopPanel(User user, boolean staffTakeaway) {
        this(user, staffTakeaway, false, null);
    }

    public CustomerShopPanel(Integer tableId) {
        this(null, false, true, tableId);
    }

    private CustomerShopPanel(User user, boolean staffTakeaway, boolean guestOrder, Integer tableId) {
        this.user = user;
        this.staffTakeaway = staffTakeaway;
        this.guestOrder = guestOrder;
        this.tableId = tableId;
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(UITheme.BACKGROUND);

        JLabel title = new JLabel(guestOrder
                ? "Đặt món tại bàn " + String.format("%02d", tableId)
                : staffTakeaway ? "Tạo đơn cho khách hàng" : "Chọn đồ uống và đặt hàng");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(UITheme.TEXT);
        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        heading.add(title, BorderLayout.CENTER);
        if (guestOrder) {
            JButton exitButton = new JButton("Thoát đặt món");
            styleActionButton(exitButton, UITheme.Action.NEUTRAL);
            exitButton.addActionListener(event -> exitGuestOrder());
            heading.add(exitButton, BorderLayout.EAST);
        }
        add(heading, BorderLayout.NORTH);

        if (staffTakeaway || guestOrder) {
            JPanel customerInfo = new JPanel(new GridLayout(1, 4, 8, 0));
            customerInfo.setBorder(panelBorder("Thông tin khách hàng"));
            customerInfo.setBackground(UITheme.CARD);
            customerNameField = new JTextField();
            customerPhoneField = new JTextField();
            customerInfo.add(new JLabel("Tên khách:"));
            customerInfo.add(customerNameField);
            customerInfo.add(new JLabel("Số điện thoại (không bắt buộc):"));
            customerInfo.add(customerPhoneField);
            UITheme.styleTree(customerInfo);
            add(customerInfo, BorderLayout.SOUTH);
        }

        JButton reloadButton = new JButton("Làm mới");
        reloadButton.addActionListener(e -> loadDrinks());
        drinkGrid.setBackground(UITheme.CARD);
        JScrollPane drinkScroll = new JScrollPane(drinkGrid);
        drinkScroll.setBorder(BorderFactory.createEmptyBorder());
        drinkScroll.getViewport().setBackground(UITheme.CARD);
        drinkScroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel drinkPanel = section("Đồ uống đang bán", drinkScroll, reloadButton);

        cartModel = tableModel(new Object[] { "Tên đồ uống", "SL", "Đơn giá", "Thành tiền" });
        cartTable = new JTable(cartModel);
        cartTable.setRowHeight(42);
        JButton removeButton = new JButton("Xóa khỏi giỏ");
        removeButton.addActionListener(e -> removeSelectedItem());
        JButton testReceiptButton = new JButton("In hóa đơn thử");
        testReceiptButton.addActionListener(e -> showReceipt());
        JButton payButton = new JButton("Gửi order");
        payButton.addActionListener(e -> {
            if (guestOrder) {
                sendGuestOrder();
            } else {
                sendStaffOrder();
            }
        });
        totalLabel.setFont(new Font("Arial", Font.BOLD, 16));
        totalLabel.setForeground(UITheme.TEXT);
        JPanel cartActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        cartActions.setOpaque(false);
        cartActions.add(totalLabel);
        cartActions.add(removeButton);
        cartActions.add(testReceiptButton);
        cartActions.add(payButton);
        JPanel cartPanel = section("Giỏ hàng", new JScrollPane(cartTable), cartActions);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, drinkPanel, cartPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setOpaque(false);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setDividerSize(8);
        splitPane.setBackground(UITheme.BACKGROUND);
        add(splitPane, BorderLayout.CENTER);
        UITheme.styleTree(this);
        styleActionButton(reloadButton, UITheme.Action.NEUTRAL);
        styleActionButton(removeButton, UITheme.Action.DESTRUCTIVE);
        styleActionButton(testReceiptButton, UITheme.Action.PRINT);
        styleActionButton(payButton, UITheme.Action.CREATE);
        loadDrinks();
        updateCart();
        DataSync.register(this, this);
    }

    @Override public void refreshData() { loadDrinks(); }

    private DefaultTableModel tableModel(Object[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private JPanel section(String title, JComponent content, Component... actions) {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(UITheme.CARD);
        panel.setBorder(panelBorder(title));
        if (content instanceof JScrollPane scrollPane) {
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.getViewport().setBackground(UITheme.CARD);
        }
        panel.add(content, BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        for (Component action : actions)
            footer.add(action);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private TitledBorder panelBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14),
                UITheme.MUTED);
    }

    private void styleActionButton(JButton button, UITheme.Action action) {
        UITheme.styleButton(button, action);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
    }

    private void exitGuestOrder() {
        if (!cart.isEmpty()) {
            int choice = NotificationDialog.showConfirmDialog(this,
                    "Giỏ hàng chưa gửi sẽ bị bỏ. Bạn có muốn thoát đặt món không?",
                    "Thoát đặt món", NotificationDialog.YES_NO_OPTION, NotificationDialog.WARNING_MESSAGE);
            if (choice != NotificationDialog.YES_OPTION) return;
        }
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) window.dispose();
    }

    private void loadDrinks() {
        try {
            drinks.clear();
            availableQuantities = inventoryService.getAllAvailable();
            drinks.addAll(drinkService.getAll().stream()
                    .filter(drink -> drink.isActive() && availableQuantities.getOrDefault(drink.getId(), 0) > 0)
                    .collect(Collectors.toList()));
            drinkGrid.removeAll();
            if (drinks.isEmpty()) {
                JLabel empty = new JLabel("Hiện chưa có đồ uống đang bán", SwingConstants.CENTER);
                empty.setForeground(UITheme.MUTED);
                drinkGrid.add(empty);
            } else {
                for (Drink drink : drinks) {
                    drinkGrid.add(createDrinkCard(drink));
                }
            }
            drinkGrid.revalidate();
            drinkGrid.repaint();
        } catch (Exception exception) {
            showError("Không tải được đồ uống", exception);
        }
    }

    private JPanel createDrinkCard(Drink drink) {
        JPanel card = new JPanel(new BorderLayout(6, 8));
        card.setBackground(UITheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        card.setPreferredSize(new Dimension(0, 235));

        DrinkVisual image = new DrinkVisual();
        image.setPreferredSize(new Dimension(0, 118));
        image.setDrink(drink);
        JLabel stock = new JLabel(availableQuantities.getOrDefault(drink.getId(), 0) + "", SwingConstants.CENTER);
        stock.setOpaque(true);
        stock.setBackground(new Color(255, 243, 205));
        stock.setForeground(new Color(133, 100, 4));
        stock.setFont(new Font("Segoe UI", Font.BOLD, 10));
        stock.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));

        JPanel imageHolder = new JPanel(null) {
            @Override
            public void doLayout() {
                image.setBounds(0, 0, getWidth(), getHeight());
                stock.setBounds(Math.max(0, getWidth() - stock.getPreferredSize().width - 6),
                        6, stock.getPreferredSize().width, stock.getPreferredSize().height);
            }
        };
        imageHolder.setOpaque(false);
        imageHolder.setPreferredSize(new Dimension(0, 118));
        imageHolder.add(image);
        imageHolder.add(stock);
        imageHolder.setComponentZOrder(stock, 0);
        card.add(imageHolder, BorderLayout.NORTH);

        JPanel details = new JPanel(new GridLayout(0, 1, 2, 2));
        details.setOpaque(false);
        JLabel name = new JLabel("<html><div style='text-align:center'>"
                + escapeHtml(drink.getName()) + "</div></html>", SwingConstants.CENTER);
        name.setForeground(UITheme.TEXT);
        name.setFont(new Font("Segoe UI", Font.BOLD, 13));
        JLabel price = new JLabel(formatMoney(drink.getPrice()) + " đ", SwingConstants.CENTER);
        price.setForeground(UITheme.SUCCESS);
        price.setFont(new Font("Segoe UI", Font.BOLD, 14));
        details.add(name);
        details.add(price);
        card.add(details, BorderLayout.CENTER);

        JButton addButton = new JButton("Thêm vào giỏ");
        styleActionButton(addButton, UITheme.Action.CREATE);
        addButton.setFont(new Font("Segoe UI", Font.BOLD, 11));
        addButton.addActionListener(event -> addDrinkToCart(drink));
        card.add(addButton, BorderLayout.SOUTH);
        return card;
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void addDrinkToCart(Drink drink) {
        try {
            int alreadyInCart = cart.stream().filter(item -> item.drink.getId() == drink.getId())
                    .mapToInt(item -> item.quantity).sum();
            int initialAvailable = inventoryService.getAvailable(drink.getId());
            int remaining = initialAvailable - alreadyInCart;
            if (remaining <= 0) {
                showMessage(drink.getName() + " hiện chỉ còn " + initialAvailable
                        + " cốc có thể đặt; giỏ hàng hiện đã có " + alreadyInCart + " cốc.");
                loadDrinks();
                return;
            }
            List<model.Topping> available=new ToppingService().getForDrink(drink.getId()).stream().limit(3).toList();
            List<JCheckBox> boxes=new ArrayList<>();
            JPanel toppingsPanel=new JPanel(new GridLayout(0,2,8,8)); toppingsPanel.setOpaque(false);
            if(available.isEmpty()){JLabel none=new JLabel("Món này chưa có topping.");none.setForeground(UITheme.MUTED);toppingsPanel.add(none);}
            for(var topping:available){JCheckBox box=new JCheckBox(" "+topping.getName()+"  ·  +"+formatMoney(topping.getPrice())+" đ");box.setOpaque(true);box.setBackground(UITheme.CARD);box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UITheme.BORDER),BorderFactory.createEmptyBorder(8,8,8,8)));box.putClientProperty("topping",topping);boxes.add(box);toppingsPanel.add(box);}
            List<Drink.SizeOption> sizes=drink.getAvailableSizes().stream().limit(3).toList();
            JComboBox<Drink.SizeOption> size=new JComboBox<>(sizes.toArray(Drink.SizeOption[]::new));
            JTextField note=new JTextField(); note.setPreferredSize(new Dimension(260,34)); JSpinner qty=new JSpinner(new javax.swing.SpinnerNumberModel(1,1,remaining,1)); qty.setPreferredSize(new Dimension(80,34));
            JLabel amount=new JLabel(); amount.setFont(new Font("Segoe UI",Font.BOLD,20));amount.setForeground(UITheme.SUCCESS);
            Runnable update=()->{Drink.SizeOption selected=(Drink.SizeOption)size.getSelectedItem();double unit=selected==null?drink.getPrice():selected.getPrice();for(var b:boxes)if(b.isSelected())unit+=((model.Topping)b.getClientProperty("topping")).getPrice();amount.setText(formatMoney(unit*(Integer)qty.getValue())+" đ");};
            size.addActionListener(e->update.run());qty.addChangeListener(e->update.run());for(var b:boxes)b.addActionListener(e->update.run());update.run();
            JPanel form=new JPanel(new BorderLayout(0,12));form.setBorder(BorderFactory.createEmptyBorder(16,18,12,18));form.setBackground(UITheme.BACKGROUND);form.setPreferredSize(new Dimension(430,430));
            JPanel content=new JPanel();content.setOpaque(false);content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));
            JLabel title=new JLabel(drink.getName());title.setFont(new Font("Segoe UI",Font.BOLD,20));title.setForeground(UITheme.TEXT);content.add(title);
            JLabel base=new JLabel("Giá Size S: "+formatMoney(drink.getPrice())+" đ  ·  Còn có thể đặt "+initialAvailable+" ly");base.setForeground(UITheme.MUTED);content.add(base);content.add(Box.createVerticalStrut(14));
            content.add(new JLabel("CHỌN SIZE"));content.add(size);content.add(Box.createVerticalStrut(12));content.add(new JLabel("TOPPING · TỐI ĐA 3"));content.add(new JScrollPane(toppingsPanel));content.add(Box.createVerticalStrut(8));
            content.add(new JLabel("GHI CHÚ RIÊNG CHO MÓN"));note.setToolTipText("Ví dụ: ít đá, ít đường, không đá");content.add(note);
            JPanel footer=new JPanel(new BorderLayout());footer.setOpaque(false);JPanel q=new JPanel(new FlowLayout(FlowLayout.LEFT));q.setOpaque(false);q.add(new JLabel("Số lượng"));q.add(qty);footer.add(q,BorderLayout.WEST);footer.add(amount,BorderLayout.EAST);form.add(content,BorderLayout.CENTER);form.add(footer,BorderLayout.SOUTH);
            if(JOptionPane.showConfirmDialog(this,form,"Tùy chỉnh món",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
            int latestAvailable = inventoryService.getAvailable(drink.getId());
            if (alreadyInCart + (Integer) qty.getValue() > latestAvailable) {
                showMessage(drink.getName() + " chỉ còn " + latestAvailable
                        + " cốc có thể đặt. Giỏ hiện có " + alreadyInCart
                        + " cốc; vui lòng giảm số lượng rồi thử lại.");
                loadDrinks();
                return;
            }
            Drink.SizeOption selectedSize=(Drink.SizeOption)size.getSelectedItem();
            CartItem item=new CartItem(drink,(Integer)qty.getValue());item.size=selectedSize==null?"S":selectedSize.getName();item.sizeExtraPrice=(selectedSize==null?drink.getPrice():selectedSize.getPrice())-drink.getPrice();item.note=note.getText().trim();
            for(var b:boxes)if(b.isSelected()){var t=(model.Topping)b.getClientProperty("topping");item.toppings.add(new OrderDetail.ToppingSnapshot(t.getId(),t.getName(),t.getPrice()));}
            CartItem same=cart.stream().filter(existing->sameConfiguration(existing,item)).findFirst().orElse(null);
            if(same==null) cart.add(item);
            else same.quantity+=item.quantity;
        } catch(Exception ex){showError("Không tải được topping",ex);return;}
        updateCart();
    }

    private boolean sameConfiguration(CartItem a, CartItem b) {
        if(a.drink.getId()!=b.drink.getId() || !a.size.equals(b.size)
                || !a.note.trim().equals(b.note.trim()) || a.toppings.size()!=b.toppings.size()) return false;
        java.util.Map<Integer,Long> first=a.toppings.stream().collect(Collectors.groupingBy(t->t.toppingId,Collectors.counting()));
        java.util.Map<Integer,Long> second=b.toppings.stream().collect(Collectors.groupingBy(t->t.toppingId,Collectors.counting()));
        return first.equals(second);
    }

    private void removeSelectedItem() {
        int row = cartTable.getSelectedRow();
        if (row >= 0)
            cart.remove(row);
        updateCart();
    }

    private void updateCart() {
        cartModel.setRowCount(0);
        for (CartItem item : cart) {
            cartModel.addRow(new Object[] { item.drink.getName()+" / "+item.size+ (item.toppings.isEmpty()?"":" / "+item.toppings.stream().map(t->t.name).collect(Collectors.joining(", ")))+(item.note.isBlank()?"":" / "+item.note), item.quantity,
                    formatMoney(item.unitPrice()), formatMoney(item.subtotal()) });
        }
        totalLabel.setText("Tổng: " + formatMoney(total()) + " đ");
    }

    private double total() {
        return cart.stream().mapToDouble(CartItem::subtotal).sum();
    }

    private void showReceipt() {
        if (cart.isEmpty()) {
            showMessage("Giỏ hàng đang trống.");
            return;
        }
        receiptArea.setText(buildReceipt("CHƯA THANH TOÁN"));
        receiptArea.setCaretPosition(0);
        NotificationDialog.showMessageDialog(this, new JScrollPane(receiptArea),
                "Hóa đơn thử", NotificationDialog.INFORMATION_MESSAGE);
    }

    private void sendStaffOrder() {
        if (cart.isEmpty()) {
            showMessage("Giỏ hàng đang trống.");
            return;
        }
        try {
            if (!validateCartAvailability()) return;
            List<OrderDetail> details = cart.stream()
                    .map(this::toOrderDetail)
                    .collect(Collectors.toList());
            String customerName = staffTakeaway ? customerNameField.getText().trim() : user.getFullName();
            String customerPhone = staffTakeaway ? customerPhoneField.getText().trim() : null;
            if (customerName.isEmpty()) {
                showMessage("Hãy nhập tên khách hàng.");
                return;
            }
            Object[] methods={"Tiền mặt","Chuyển khoản","Hủy"};
            int methodChoice=JOptionPane.showOptionDialog(this,"Đơn mang về cho "+customerName+"\nTổng tiền: "+formatMoney(total())+" đ\nChọn phương thức khách thanh toán:","Thanh toán tại quầy",JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,null,methods,methods[0]);
            if(methodChoice<0||methodChoice==2)return;
            String method=methodChoice==0?"CASH":"TRANSFER";
            String verifyMessage=method.equals("CASH")
                    ?"Chỉ chọn thành công sau khi đã nhận đủ tiền mặt từ khách."
                    :"Chỉ chọn thành công sau khi đã kiểm tra tiền chuyển khoản thực tế đã vào tài khoản.";
            Object[] outcomes={"Thanh toán thành công","Thanh toán thất bại","Hủy"};
            int outcome=JOptionPane.showOptionDialog(this,verifyMessage,"Xác nhận thanh toán",JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,outcomes,outcomes[0]);
            if(outcome!=0){cart.clear();customerNameField.setText("");customerPhoneField.setText("");updateCart();showMessage(outcome==1?"Thanh toán thất bại; order nháp đã bị hủy, chưa tạo order chính thức và không trừ kho.":"Đã hủy thanh toán; order nháp đã bị hủy, chưa tạo order chính thức và không trừ kho.");return;}
            var order=orderService.createStaffCounterPaidOrder(customerName,customerPhone,total(),method,details,user);
            receiptArea.setText(buildReceipt("ĐÃ THANH TOÁN · ĐƠN #"+order.getId()+" · ĐANG PHA CHẾ"));
            NotificationDialog.showMessageDialog(this,"Thanh toán thành công. Đơn #"+order.getId()+" đã được tạo, xác nhận và chuyển pha chế.","Tạo đơn thành công",NotificationDialog.INFORMATION_MESSAGE);
            if(JOptionPane.showConfirmDialog(this,"In bill giao khách?","In bill",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION) {
                utils.ReceiptPrinter.printAsync(order, () -> orderService.getDetails(order.getId()),
                        exception -> showError("Không thể in bill", exception), () -> {});
            }
            cart.clear();customerNameField.setText("");customerPhoneField.setText("");updateCart();loadDrinks();
        } catch (Exception exception) {
            showError("Thanh toán thất bại", exception);
        }
    }

    private void sendGuestOrder() {
        if (cart.isEmpty()) {
            showMessage("Giỏ hàng đang trống.");
            return;
        }
        String customerName = customerNameField.getText().trim();
        String customerPhone = customerPhoneField.getText().trim();
        if (customerName.isEmpty()) {
            showMessage("Hãy nhập tên khách hàng.");
            return;
        }
        try {
            // Different sizes of one drink still share a single inventory count.
            if (!validateCartAvailability()) return;
        } catch (Exception exception) {
            showError("Không thể kiểm tra tồn kho", exception);
            return;
        }
        int paymentChoice = javax.swing.JOptionPane.showOptionDialog(this,
                "Tổng thanh toán: " + formatMoney(total()) + " đ\nChọn cách thanh toán:",
                "Thanh toán mô phỏng", javax.swing.JOptionPane.DEFAULT_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE, null,
                new Object[] {"Tiền mặt tại quầy", "Chuyển khoản QR", "Hủy"}, "Tiền mặt tại quầy");
        if (paymentChoice < 0 || paymentChoice == 2) {
            showMessage("Đã hủy thanh toán. Chưa tạo yêu cầu, chưa tạo đơn hàng và không trừ kho.");
            return;
        }
        try {
            List<OrderDetail> details = cart.stream()
                    .map(this::toOrderDetail)
                    .collect(Collectors.toList());
            if(paymentChoice==1){
                JPanel qrPanel=new JPanel(new BorderLayout(8,8));qrPanel.setBorder(BorderFactory.createEmptyBorder(14,18,14,18));
                JLabel qrPlaceholder=new JLabel("<html><div style='text-align:center;padding:24px;border:2px dashed #9aa6b2;width:260px;height:170px'>MÃ QR NGÂN HÀNG<br><br>Chưa cấu hình QR cửa hàng</div></html>",SwingConstants.CENTER);
                JLabel amount=new JLabel("Số tiền cần thanh toán: "+formatMoney(total())+" đ",SwingConstants.CENTER);amount.setFont(new Font("Segoe UI",Font.BOLD,18));amount.setForeground(UITheme.SUCCESS);
                JLabel simulation=new JLabel("Thanh toán mô phỏng · Chưa kết nối ngân hàng",SwingConstants.CENTER);simulation.setForeground(UITheme.MUTED);
                qrPanel.add(qrPlaceholder,BorderLayout.CENTER);JPanel labels=new JPanel(new GridLayout(0,1,4,4));labels.add(amount);labels.add(simulation);qrPanel.add(labels,BorderLayout.SOUTH);
                Object[] results={"Tôi đã chuyển khoản","Thanh toán thất bại","Hủy"};
                int result=JOptionPane.showOptionDialog(this,qrPanel,"Thanh toán bằng QR",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,results,results[0]);
                if(result!=0){showMessage(result==1?"Thanh toán thất bại. Không tạo đơn và không trừ kho; bạn có thể thử lại.":"Đã hủy thanh toán. Không tạo đơn và không trừ kho.");return;}
                int requestId=new CashierRequestService().create(new com.google.gson.Gson().toJson(
                        new CashierRequestService.Request(customerName,customerPhone,tableId,total(),"DINE_IN",details,
                                "WAITING_CONFIRMATION",java.time.LocalDateTime.now().toString(),"TRANSFER")));
                receiptArea.setText(buildReceipt("CHỜ NHÂN VIÊN XÁC NHẬN CHUYỂN KHOẢN · TMP"+requestId));
                NotificationDialog.showMessageDialog(this,"Kết quả QR mô phỏng thành công đã được gửi đến nhân viên để kiểm tra và xác nhận. Chưa tạo đơn chính thức, chưa trừ kho. Mã yêu cầu TMP"+requestId,
                        "Chờ xác nhận thanh toán",NotificationDialog.INFORMATION_MESSAGE);
                cart.clear();updateCart();loadDrinks();return;
            }
            if(JOptionPane.showConfirmDialog(this,"Vui lòng đến quầy thu ngân để thanh toán. Gửi yêu cầu cho nhân viên?","Thanh toán tiền mặt",JOptionPane.YES_NO_OPTION,JOptionPane.INFORMATION_MESSAGE)!=JOptionPane.YES_OPTION)return;
            String paymentMethod = paymentChoice == 0 ? "CASH" : "TRANSFER";
            String paymentStatus = paymentChoice == 0 ? "WAITING_CASHIER" : "WAITING_CONFIRMATION";
            String requestJson = new com.google.gson.Gson().toJson(new CashierRequestService.Request(
                    customerName, customerPhone, tableId, total(), "DINE_IN", details,
                    paymentStatus, java.time.LocalDateTime.now().toString(), paymentMethod));
            int requestId = new CashierRequestService().create(requestJson);
            receiptArea.setText(buildReceipt("CHỜ NHÂN VIÊN XÁC NHẬN THANH TOÁN - MÃ TMP" + requestId));
            NotificationDialog.showMessageDialog(this,
                    "Đã gửi yêu cầu TMP" + requestId + " (" + (paymentMethod.equals("CASH") ? "tiền mặt" : "chuyển khoản")
                            + "). Nhân viên sẽ xác nhận sau khi nhận tiền hoặc kiểm tra giao dịch vào tài khoản.",
                    "Chờ nhân viên xác nhận", NotificationDialog.INFORMATION_MESSAGE);
            cart.clear();

            updateCart();
            loadDrinks();
        } catch (Exception exception) {
            showError("Không thể gửi order", exception);
        }
    }

    private boolean validateCartAvailability() throws Exception {
        var currentDrinks = drinkService.getAll().stream()
                .collect(Collectors.toMap(Drink::getId, drink -> drink));
        Map<Integer, Integer> requestedQuantities = cart.stream().collect(Collectors.groupingBy(
                item -> item.drink.getId(), Collectors.summingInt(item -> item.quantity)));
        for (Map.Entry<Integer, Integer> requested : requestedQuantities.entrySet()) {
            Drink current = currentDrinks.get(requested.getKey());
            if (current == null || !current.isActive()) {
                String name = cart.stream().filter(item -> item.drink.getId() == requested.getKey())
                        .map(item -> item.drink.getName()).findFirst().orElse("Đồ uống");
                showMessage(name
                        + " đã ngừng bán. Vui lòng xóa món này khỏi giỏ hàng.");
                loadDrinks();
                return false;
            }
            int available = inventoryService.getAvailable(current.getId());
            if (requested.getValue() > available) {
                showMessage("Bạn đang chọn tổng " + requested.getValue() + " cốc " + current.getName()
                        + " ở các size khác nhau, nhưng hiện chỉ còn " + available
                        + " cốc có thể đặt. Vui lòng giảm số lượng trước khi thanh toán.");
                loadDrinks();
                return false;
            }
        }
        return true;
    }

    private OrderDetail toOrderDetail(CartItem item) {
        OrderDetail detail = new OrderDetail(0, 0, item.drink.getId(), item.quantity, item.unitPrice());
        detail.setDrinkName(item.drink.getName());
        detail.setSize(item.size);
        detail.setSizeExtraPrice(item.sizeExtraPrice);
        detail.setToppings(item.toppings);
        detail.setToppingTotal(item.toppings.stream().mapToDouble(t -> t.price).sum());
        detail.setNote(item.note);
        detail.setSubtotal(item.unitPrice() * item.quantity);
        return detail;
    }

    private String buildReceipt(String status) {
        StringBuilder receipt = new StringBuilder();
        receipt.append("          STORE DRINK\n");
        receipt.append("          HÓA ĐƠN\n");
        receipt.append("--------------------------------\n");
        String receiptCustomer = guestOrder || staffTakeaway
                ? customerNameField.getText().trim()
                : user.getFullName();
        receipt.append("Khách hàng: ").append(receiptCustomer).append("\n");
        if (tableId != null) {
            receipt.append("Bàn: ").append(String.format("%02d", tableId)).append("\n");
        }
        receipt.append("Trạng thái: ").append(status).append("\n");
        receipt.append("--------------------------------\n");
        for (CartItem item : cart) {
            receipt.append(item.drink.getName()).append("\n")
                    .append("  ").append(item.quantity).append(" x ")
                    .append(formatMoney(item.unitPrice())).append(" = ")
                    .append(formatMoney(item.subtotal())).append(" đ\n");
        }
        receipt.append("--------------------------------\n");
        receipt.append("TỔNG CỘNG: ").append(formatMoney(total())).append(" đ\n");
        return receipt.toString();
    }

    private String formatMoney(double value) {
        return currency.format(value);
    }

    private void showMessage(String message) {
        NotificationDialog.showMessageDialog(this, message);
    }

    private void showError(String message, Exception exception) {
        exception.printStackTrace();
        NotificationDialog.showMessageDialog(this, message + ": " + exception.getMessage(),
                "Lỗi", NotificationDialog.ERROR_MESSAGE);
    }

    private static class CartItem {
        private final Drink drink;
        private int quantity;
        private String size="S", note=""; private double sizeExtraPrice; private final List<OrderDetail.ToppingSnapshot> toppings=new ArrayList<>();

        private CartItem(Drink drink, int quantity) {
            this.drink = drink;
            this.quantity = quantity;
        }

        private double subtotal() {
            return unitPrice() * quantity;
        }
        private double unitPrice(){return drink.getPrice()+sizeExtraPrice+toppings.stream().mapToDouble(t->t.price).sum();}
    }

    private static class DrinkVisual extends JPanel {
        private Drink drink;
        private BufferedImage productImage;
        private long imageRequest;

        private DrinkVisual() {
            setPreferredSize(new Dimension(0, 150));
            setOpaque(false);
        }

        private void setDrink(Drink drink) {
            this.drink = drink;
            this.productImage = null;
            long request = ++imageRequest;
            if (drink != null && drink.getImage() != null && !drink.getImage().isBlank()) {
                new SwingWorker<BufferedImage, Void>() {
                    @Override
                    protected BufferedImage doInBackground() throws IOException {
                        return javax.imageio.ImageIO.read(java.net.URI.create(drink.getImage()).toURL());
                    }

                    @Override
                    protected void done() {
                        if (request != imageRequest) {
                            return;
                        }
                        try {
                            productImage = get();
                        } catch (Exception exception) {
                            productImage = null;
                        }
                        repaint();
                    }
                }.execute();
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            int width = getWidth();
            int height = getHeight();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color background = drink == null ? new Color(232, 240, 254) : categoryColor(drink.getCategoryId());
            g.setPaint(new GradientPaint(0, 0, background, width, height,
                    new Color(248, 249, 250)));
            g.fillRoundRect(2, 2, width - 4, height - 4, 18, 18);

            if (drink != null) {
                if (productImage != null) {
                    drawProductImage(g, productImage, 8, 8, width - 16, height - 16);
                } else {
                    drawFallbackDrink(g, drink, width);
                }
            } else {
                g.setColor(UITheme.MUTED);
                g.setFont(new Font("Arial", Font.BOLD, 12));
                g.drawString("Chưa có hình", 12, height / 2);
            }
            g.dispose();
        }

        private void drawProductImage(Graphics2D g, BufferedImage image,
                int x, int y, int targetWidth, int targetHeight) {
            double scale = Math.min((double) targetWidth / image.getWidth(),
                    (double) targetHeight / image.getHeight());
            int imageWidth = (int) (image.getWidth() * scale);
            int imageHeight = (int) (image.getHeight() * scale);
            int imageX = x + (targetWidth - imageWidth) / 2;
            int imageY = y + (targetHeight - imageHeight) / 2;
            Shape oldClip = g.getClip();
            g.setClip(new RoundRectangle2D.Double(x, y, targetWidth, targetHeight, 16, 16));
            g.drawImage(image, imageX, imageY, imageWidth, imageHeight, null);
            g.setClip(oldClip);
        }

        private void drawFallbackDrink(Graphics2D g, Drink drink, int width) {
            int cupWidth = Math.min(76, Math.max(48, width / 5));
            int cupHeight = Math.min(78, Math.max(48, getHeight() - 24));
            int cupX = width / 2 - cupWidth / 2;
            int cupY = getHeight() / 2 - cupHeight / 2;
            g.setColor(new Color(255, 255, 255, 210));
            g.fillRoundRect(cupX, cupY, cupWidth, cupHeight, 16, 16);
            g.setColor(new Color(255, 255, 255, 235));
            g.fillOval(cupX - 7, cupY + 30, 22, 35);
            g.setColor(liquidColor(drink.getCategoryId()));
            g.fillRoundRect(cupX + 7, cupY + 25, cupWidth - 14, cupHeight - 30, 10, 10);
            g.setColor(new Color(95, 62, 35));
            g.setStroke(new BasicStroke(5));
            g.drawLine(width / 2 + 10, cupY - 8, width / 2 + 23, cupY + 24);
        }

        private Color categoryColor(int categoryId) {
            return switch (categoryId) {
                case 1 -> new Color(245, 230, 218);
                case 2 -> new Color(220, 241, 237);
                case 3 -> new Color(255, 239, 214);
                case 4 -> new Color(250, 228, 237);
                default -> new Color(235, 230, 248);
            };
        }

        private Color liquidColor(int categoryId) {
            return switch (categoryId) {
                case 1 -> new Color(105, 56, 30);
                case 2 -> new Color(226, 220, 157);
                case 3 -> new Color(247, 139, 39);
                case 4 -> new Color(238, 148, 184);
                default -> new Color(116, 91, 170);
            };
        }
    }
}
