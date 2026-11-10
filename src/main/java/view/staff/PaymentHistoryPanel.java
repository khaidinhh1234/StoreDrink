package view.staff;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import model.Payment;
import model.User;
import service.PaymentService;
import service.UserService;
import view.layout.DataSync;
import view.layout.RefreshablePanel;
import view.layout.UITheme;

/** Payment history backed by the payments collection; STAFF sees only their own payments. */
public class PaymentHistoryPanel extends JPanel implements RefreshablePanel {
    private final User user;
    private final PaymentService paymentService = new PaymentService();
    private final UserService userService = new UserService();
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{
            "Mã đơn", "Khách hàng", "Loại đơn", "Nhân viên thanh toán", "Thời gian thanh toán",
            "Phương thức", "Tổng tiền", "Trạng thái"
    }, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
    private final JTable table = new JTable(model);
    private final JTextField search = new JTextField(16);
    private final JComboBox<String> period = new JComboBox<>(new String[]{"Tất cả", "Tháng này", "Tháng trước"});
    private final JComboBox<Employee> employee = new JComboBox<>();
    private final JLabel summary = new JLabel("Đang tải lịch sử thanh toán...");
    private List<Payment> loadedPayments = List.of();
    private Map<Integer,String> staffNames = Map.of();
    private boolean loading;
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public PaymentHistoryPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.BACKGROUND);
        JLabel title = new JLabel("Lịch sử thanh toán");
        title.setFont(new Font("Segoe UI", Font.BOLD, 25));
        title.setForeground(UITheme.TEXT);
        title.setBorder(BorderFactory.createEmptyBorder(22, 26, 8, 26));
        add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setBackground(UITheme.BACKGROUND);
        center.setBorder(BorderFactory.createEmptyBorder(0, 20, 18, 20));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        filters.setBackground(UITheme.CARD);
        filters.add(new JLabel("Kỳ:")); filters.add(period);
        if (isAdmin()) { filters.add(new JLabel("Nhân viên:")); filters.add(employee); }
        filters.add(new JLabel("Tìm kiếm:")); filters.add(search);
        JButton refresh = new JButton("Làm mới");
        UITheme.styleButton(refresh, UITheme.Action.NEUTRAL);
        filters.add(refresh);
        center.add(filters, BorderLayout.NORTH);
        UITheme.styleTree(table);
        table.setAutoCreateRowSorter(true);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));
        center.add(scroll, BorderLayout.CENTER);
        summary.setFont(new Font("Segoe UI", Font.BOLD, 15));
        summary.setForeground(UITheme.TEXT);
        center.add(summary, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        period.addActionListener(e -> render());
        employee.addActionListener(e -> { if (!loading) render(); });
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { render(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { render(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { render(); }
        });
        refresh.addActionListener(e -> refreshData());
        DataSync.register(this, this);
        refreshData();
    }

    private boolean isAdmin() { return user != null && "ADMIN".equalsIgnoreCase(user.getRole()); }

    @Override public void refreshData() {
        if (loading) return;
        loading = true;
        summary.setText("Đang tải lịch sử thanh toán...");
        new SwingWorker<HistoryData,Void>() {
            protected HistoryData doInBackground() throws Exception {
                List<Payment> payments = paymentService.getAll();
                List<User> users = isAdmin() ? userService.getAll() : List.of(user);
                return new HistoryData(payments, users);
            }
            protected void done() {
                try {
                    HistoryData data = get();
                    loadedPayments = data.payments;
                    staffNames = data.names;
                    if (isAdmin()) {
                        Employee selected = (Employee) employee.getSelectedItem();
                        loading = true;
                        employee.removeAllItems();
                        employee.addItem(new Employee(0, "Tất cả nhân viên"));
                        data.names.forEach((id, name) -> employee.addItem(new Employee(id, name)));
                        if (selected != null) for (int i=0;i<employee.getItemCount();i++)
                            if (employee.getItemAt(i).id == selected.id) employee.setSelectedIndex(i);
                    }
                    render();
                } catch (Exception ex) {
                    summary.setText("Không tải được lịch sử thanh toán: " + ex.getMessage());
                } finally { loading = false; }
            }
        }.execute();
    }

    private void render() {
        if (model == null || loading && loadedPayments.isEmpty()) return;
        String selectedPeriod = String.valueOf(period.getSelectedItem());
        Employee selectedEmployee = (Employee) employee.getSelectedItem();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        List<Payment> visible = loadedPayments.stream()
                .filter(p -> "SUCCESS".equalsIgnoreCase(p.getStatus()))
                .filter(p -> p.getPaidAt() != null && !p.getPaidAt().isBlank())
                .filter(p -> isAdmin() || p.getEmployeeId() == user.getId())
                .filter(p -> !isAdmin() || selectedEmployee == null || selectedEmployee.id == 0
                        || p.getEmployeeId() == selectedEmployee.id)
                .filter(p -> inPeriod(p.getPaidAt(), selectedPeriod, today))
                .filter(p -> query.isEmpty() || ("#"+p.getOrderId()).contains(query)
                        || safe(p.getCustomerName()).toLowerCase(Locale.ROOT).contains(query)
                        || staffNames.getOrDefault(p.getEmployeeId(), "").toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(Payment::getPaidAt).reversed()).toList();
        model.setRowCount(0);
        double total=0; int cash=0, transfer=0;
        for (Payment payment : visible) {
            total += payment.getAmount();
            if ("CASH".equalsIgnoreCase(payment.getPaymentMethod())) cash++;
            if ("TRANSFER".equalsIgnoreCase(payment.getPaymentMethod()) || "BANK".equalsIgnoreCase(payment.getPaymentMethod())) transfer++;
            String orderType = "TAKEAWAY".equalsIgnoreCase(payment.getOrderType()) ? "Mang đi" : "Tại bàn";
            model.addRow(new Object[]{"#"+payment.getOrderId(), safe(payment.getCustomerName()), orderType,
                    staffNames.getOrDefault(payment.getEmployeeId(), "Chưa ghi nhận"),
                    format(payment.getPaidAt()), paymentLabel(payment.getPaymentMethod()), UITheme.formatVnd(payment.getAmount()), "Đã thanh toán"});
        }
        summary.setText("Số giao dịch: " + visible.size() + "    Tiền mặt: " + cash
                + "    Chuyển khoản: " + transfer + "    Tổng doanh thu: " + UITheme.formatVnd(total));
    }

    private boolean inPeriod(String value, String selected, LocalDate today) {
        if ("Tất cả".equals(selected)) return true;
        try {
            LocalDate date = LocalDateTime.parse(value).toLocalDate();
            if ("Tháng này".equals(selected)) return date.getYear()==today.getYear() && date.getMonth()==today.getMonth();
            LocalDate prev=today.minusMonths(1); return date.getYear()==prev.getYear() && date.getMonth()==prev.getMonth();
        } catch (RuntimeException ex) { return false; }
    }
    private String format(String value) { try { return LocalDateTime.parse(value).format(DISPLAY); } catch(Exception e) { return value; } }
    private String paymentLabel(String value) { return "CASH".equalsIgnoreCase(value)?"Tiền mặt":"Chuyển khoản"; }
    private String safe(String v) { return v==null?"":v; }
    private record Employee(int id,String name) { @Override public String toString(){return name;} }
    private static final class HistoryData {
        final List<Payment> payments; final Map<Integer,String> names;
        HistoryData(List<Payment> payments,List<User> users){this.payments=payments==null?List.of():payments;this.names=users.stream()
            .filter(u->"STAFF".equalsIgnoreCase(u.getRole())).collect(Collectors.toMap(User::getId,User::getFullName,(a,b)->a));}
    }
}
