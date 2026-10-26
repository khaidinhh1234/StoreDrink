package view.staff;

import model.Drink;
import model.Order;
import model.OrderDetail;
import model.User;
import service.DrinkService;
import service.OrderService;
import service.UserService;
import view.layout.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.text.NumberFormat;

public class DashboardPanel extends JPanel {
    private final User user;
    private final OrderService orderService = new OrderService();
    private final DrinkService drinkService = new DrinkService();
    private final UserService userService = new UserService();
    private final NumberFormat currency = NumberFormat.getInstance(Locale.of("vi", "VN"));
    private final JPanel content = new JPanel(new BorderLayout(0, 14));
    private final JComboBox<String> periodFilter = new JComboBox<>(new String[] { "Hôm nay", "Tuần này" });
    private final JComboBox<MonthFilter> monthFilter = new JComboBox<>();
    private final JComboBox<EmployeeFilter> employeeFilter = new JComboBox<>();
    private final JButton exportButton = new JButton("Xuất Excel");
    private final JButton refreshButton = new JButton("Làm mới");
    private ReportData reportData;
    private boolean updatingFilters;

    public DashboardPanel() {
        this("Tổng quan", null);
    }

    public DashboardPanel(String titleText) {
        this(titleText, null);
    }

    public DashboardPanel(String titleText, User user) {
        this.user = user;
        setBackground(UITheme.BACKGROUND);
        setLayout(new BorderLayout());

        JLabel title = new JLabel(titleText);
        title.setForeground(UITheme.TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setBorder(new EmptyBorder(25, 30, 20, 30));
        add(title, BorderLayout.NORTH);

        content.setBackground(UITheme.BACKGROUND);
        content.setBorder(new EmptyBorder(0, 22, 22, 22));
        add(content, BorderLayout.CENTER);

        if (user != null && "CUSTOMER".equalsIgnoreCase(user.getRole())) {
            showCustomerWelcome();
            return;
        }

        if (isAdmin()) {
            periodFilter.addItem("Theo tháng");
            for (int offset = 0; offset < 24; offset++) {
                monthFilter.addItem(new MonthFilter(YearMonth.now().minusMonths(offset)));
            }
            monthFilter.setSelectedIndex(0);
            monthFilter.setVisible(false);
        }
        buildFilterBar();
        loadStatistics();
    }

    private void showCustomerWelcome() {
        JLabel message = new JLabel("Chào " + user.getFullName() + "! Hãy chọn đồ uống để bắt đầu đặt món.",
                SwingConstants.CENTER);
        message.setForeground(UITheme.TEXT);
        message.setFont(new Font("Segoe UI", Font.BOLD, 18));
        content.add(message, BorderLayout.CENTER);
    }

    private void buildFilterBar() {
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filters.setBackground(UITheme.CARD);

        JLabel periodLabel = new JLabel("Thời gian:");
        periodLabel.setForeground(UITheme.MUTED);
        filters.add(periodLabel);
        filters.add(periodFilter);

        if (isAdmin()) {
            filters.add(monthFilter);
            JLabel employeeLabel = new JLabel("Nhân viên:");
            employeeLabel.setForeground(UITheme.MUTED);
            filters.add(employeeLabel);
            employeeFilter.setPreferredSize(new Dimension(210, 32));
            filters.add(employeeFilter);
        }

        styleToolbarButton(exportButton, UITheme.PRIMARY);
        styleToolbarButton(refreshButton, new Color(48, 58, 92));
        filters.add(exportButton);
        filters.add(refreshButton);
        content.add(filters, BorderLayout.NORTH);

        periodFilter.addActionListener(event -> {
            monthFilter.setVisible(isAdmin() && "Theo tháng".equals(periodFilter.getSelectedItem()));
            content.revalidate();
            if (!updatingFilters) renderReport();
        });
        monthFilter.addActionListener(event -> {
            if (!updatingFilters) renderReport();
        });
        employeeFilter.addActionListener(event -> {
            if (!updatingFilters) renderReport();
        });
        refreshButton.addActionListener(event -> loadStatistics());
        exportButton.addActionListener(event -> exportExcel());
    }

    private boolean isAdmin() {
        return user == null || "ADMIN".equalsIgnoreCase(user.getRole());
    }

    private void styleToolbarButton(JButton button, Color color) {
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color.darker()),
                new EmptyBorder(7, 12, 7, 12)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void loadStatistics() {
        JLabel loading = new JLabel("Đang tải báo cáo...", SwingConstants.CENTER);
        loading.setForeground(UITheme.MUTED);
        content.add(loading, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();

        exportButton.setEnabled(false);
        refreshButton.setEnabled(false);
        new SwingWorker<ReportData, Void>() {
            @Override
            protected ReportData doInBackground() throws Exception {
                List<Order> orders = orderService.getAll();
                List<Drink> drinks = drinkService.getAll();
                List<User> employees = userService.getAll();
                Map<Integer, List<OrderDetail>> detailsByOrder = new HashMap<>();
                for (Order order : orders) {
                    if ("COMPLETED".equalsIgnoreCase(order.getStatus())) {
                        detailsByOrder.put(order.getId(), orderService.getDetails(order.getId()));
                    }
                }
                return new ReportData(orders, drinks, employees, detailsByOrder);
            }

            @Override
            protected void done() {
                try {
                    reportData = get();
                    populateEmployees();
                    renderReport();
                    exportButton.setEnabled(true);
                } catch (Exception exception) {
                    showLoadError(exception);
                } finally {
                    refreshButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void populateEmployees() {
        if (!isAdmin() || reportData == null) {
            return;
        }
        EmployeeFilter current = (EmployeeFilter) employeeFilter.getSelectedItem();
        int currentId = current == null ? 0 : current.id;
        updatingFilters = true;
        try {
            employeeFilter.removeAllItems();
            employeeFilter.addItem(new EmployeeFilter(0, "Tất cả nhân viên"));
            for (User employee : reportData.employees) {
                if (employee.isActive() && ("STAFF".equalsIgnoreCase(employee.getRole())
                        || "ADMIN".equalsIgnoreCase(employee.getRole()))) {
                    employeeFilter.addItem(new EmployeeFilter(employee.getId(), employee.getFullName()));
                }
            }
            for (int i = 0; i < employeeFilter.getItemCount(); i++) {
                if (employeeFilter.getItemAt(i).id == currentId) {
                    employeeFilter.setSelectedIndex(i);
                    return;
                }
            }
            employeeFilter.setSelectedIndex(0);
        } finally {
            updatingFilters = false;
        }
    }

    private void renderReport() {
        if (reportData == null || (user != null && "CUSTOMER".equalsIgnoreCase(user.getRole()))) {
            return;
        }
        Stats stats = calculate(reportData);
        content.removeAll();
        content.add(buildFilterBarComponent(), BorderLayout.NORTH);
        content.add(createStatisticsView(stats), BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private JPanel buildFilterBarComponent() {
        // Keep the filter widgets in one persistent toolbar while the report body refreshes.
        for (Component component : content.getComponents()) {
            if (component instanceof JPanel panel && panel.getComponentCount() > 0
                    && (panel.getComponent(0) == periodFilter || panel.getComponent(0) instanceof JLabel)) {
                return panel;
            }
        }
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filters.setBackground(UITheme.CARD);
        JLabel periodLabel = new JLabel("Thời gian:");
        periodLabel.setForeground(UITheme.MUTED);
        filters.add(periodLabel);
        filters.add(periodFilter);
        if (isAdmin()) {
            monthFilter.setVisible("Theo tháng".equals(periodFilter.getSelectedItem()));
            filters.add(monthFilter);
            JLabel employeeLabel = new JLabel("Nhân viên:");
            employeeLabel.setForeground(UITheme.MUTED);
            filters.add(employeeLabel);
            employeeFilter.setPreferredSize(new Dimension(210, 32));
            filters.add(employeeFilter);
        }
        filters.add(exportButton);
        filters.add(refreshButton);
        return filters;
    }

    private Stats calculate(ReportData data) {
        DateRange range = currentDateRange();
        EmployeeFilter selectedEmployee = (EmployeeFilter) employeeFilter.getSelectedItem();
        int employeeId = selectedEmployee == null ? 0 : selectedEmployee.id;
        Map<Integer, String> drinkNames = new HashMap<>();
        Map<Integer, String> employeeNames = new HashMap<>();
        for (Drink drink : data.drinks) drinkNames.put(drink.getId(), drink.getName());
        for (User employee : data.employees) employeeNames.put(employee.getId(), employee.getFullName());

        Map<Integer, Integer> top = new HashMap<>();
        Map<String, CustomerPurchase> customerPurchases = new HashMap<>();
        Stats stats = new Stats();
        for (Order order : data.orders) {
            if (!"COMPLETED".equalsIgnoreCase(order.getStatus())) continue;
            LocalDate date = parseDate(order.getCreatedAt());
            if (date == null || date.isBefore(range.start) || date.isAfter(range.end)) continue;
            if (user != null && "STAFF".equalsIgnoreCase(user.getRole())
                    && processedBy(order) != user.getId()) continue;
            if (isAdmin() && employeeId != 0 && processedBy(order) != employeeId) continue;

            stats.completedOrders++;
            stats.revenue += order.getTotal();
            if ("CASH".equalsIgnoreCase(order.getPaymentMethod())) stats.cash += order.getTotal();
            if (isTransfer(order.getPaymentMethod())) stats.transfer += order.getTotal();
            String rawCustomerName = order.getCustomerName();
            if ((rawCustomerName == null || rawCustomerName.isBlank()) && order.getUserId() > 0) {
                rawCustomerName = employeeNames.get(order.getUserId());
            }
            String customerName = rawCustomerName == null ? ""
                    : rawCustomerName.trim().replaceAll("\\s+", " ");
            if (!customerName.isEmpty()) {
                String key = customerName.toLowerCase(Locale.ROOT);
                CustomerPurchase purchase = customerPurchases.computeIfAbsent(key,
                        ignored -> new CustomerPurchase(customerName));
                purchase.completedOrders++;
                purchase.totalSpent += order.getTotal();
            }
            for (OrderDetail detail : data.detailsByOrder.getOrDefault(order.getId(), List.of())) {
                top.merge(detail.getDrinkId(), detail.getQuantity(), Integer::sum);
            }
        }
        stats.topDrinks = top.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(10)
                .map(entry -> drinkNames.getOrDefault(entry.getKey(), "Đồ uống #" + entry.getKey())
                        + " - " + entry.getValue() + " ly")
                .toList();
        if (stats.topDrinks.isEmpty()) stats.topDrinks = List.of("Chưa có dữ liệu");
        stats.periodLabel = range.label;
        stats.employeeLabel = isAdmin() && employeeId != 0
                ? employeeNames.getOrDefault(employeeId, selectedEmployee.toString())
                : user != null && "STAFF".equalsIgnoreCase(user.getRole()) ? user.getFullName()
                        : "Toàn hệ thống";
        stats.topCustomers = customerPurchases.values().stream()
                .sorted(java.util.Comparator.comparingInt((CustomerPurchase purchase) -> purchase.completedOrders)
                        .reversed().thenComparing(java.util.Comparator.comparingDouble(
                                (CustomerPurchase purchase) -> purchase.totalSpent).reversed()))
                .limit(8)
                .map(purchase -> purchase.name + "  •  " + purchase.completedOrders + " đơn  •  "
                        + money(purchase.totalSpent))
                .toList();
        if (stats.topCustomers.isEmpty()) stats.topCustomers = List.of("Chưa có dữ liệu");
        return stats;
    }

    private DateRange currentDateRange() {
        LocalDate today = LocalDate.now();
        String selected = String.valueOf(periodFilter.getSelectedItem());
        if ("Tuần này".equals(selected)) {
            return new DateRange(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today, "Tuần này");
        }
        if ("Theo tháng".equals(selected)) {
            MonthFilter month = (MonthFilter) monthFilter.getSelectedItem();
            YearMonth selectedMonth = month == null ? YearMonth.from(today) : month.month;
            return new DateRange(selectedMonth.atDay(1), selectedMonth.atEndOfMonth(),
                    month == null ? selectedMonth.toString() : month.toString());
        }
        return new DateRange(today, today, "Hôm nay");
    }

    private int processedBy(Order order) {
        if (order.getProcessedByUserId() > 0) return order.getProcessedByUserId();
        if (order.getCreatedByUserId() > 0) return order.getCreatedByUserId();
        return order.getUserId();
    }

    private JPanel createStatisticsView(Stats stats) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 16));
        wrapper.setBackground(UITheme.BACKGROUND);
        JPanel summary = new JPanel(new BorderLayout(0, 12));
        summary.setOpaque(false);
        JLabel subtitle = new JLabel(stats.periodLabel + "  •  " + stats.employeeLabel);
        subtitle.setForeground(UITheme.MUTED);
        subtitle.setBorder(new EmptyBorder(2, 4, 0, 4));
        summary.add(subtitle, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(1, 4, 14, 14));
        cards.setBackground(UITheme.BACKGROUND);
        cards.add(createCard("Doanh thu", money(stats.revenue)));
        cards.add(createCard("Đơn hoàn thành", String.valueOf(stats.completedOrders)));
        cards.add(createCard("Tiền mặt", money(stats.cash)));
        cards.add(createCard("Chuyển khoản", money(stats.transfer)));
        summary.add(cards, BorderLayout.CENTER);
        wrapper.add(summary, BorderLayout.NORTH);

        if (isAdmin()) {
            JPanel rankings = new JPanel(new GridLayout(1, 2, 14, 0));
            rankings.setOpaque(false);
            rankings.add(createRanking("Đồ uống bán chạy trong kỳ", stats.topDrinks));
            rankings.add(createRanking("Khách quen mua nhiều nhất · gộp theo tên", stats.topCustomers));
            wrapper.add(rankings, BorderLayout.CENTER);
        } else {
            wrapper.add(createRanking("Đồ uống bán chạy trong kỳ", stats.topDrinks), BorderLayout.CENTER);
        }
        return wrapper;
    }

    private JPanel createRanking(String title, List<String> rows) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UITheme.CARD);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UITheme.BORDER), title,
                javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14), UITheme.MUTED));
        JList<String> list = new JList<>(rows.toArray(String[]::new));
        list.setBackground(UITheme.CARD);
        list.setForeground(UITheme.TEXT);
        list.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        list.setBorder(new EmptyBorder(8, 10, 8, 10));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCard(String title, String value) {
        JPanel panel = new JPanel();
        panel.setBackground(UITheme.CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                new EmptyBorder(16, 18, 16, 18)));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(UITheme.MUTED);
        JLabel valueLabel = new JLabel(value);
        valueLabel.setForeground(UITheme.TEXT);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        panel.add(Box.createVerticalStrut(12));
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(valueLabel);
        return panel;
    }

    private List<Order> filteredCompletedOrders() {
        if (reportData == null) return List.of();
        DateRange range = currentDateRange();
        EmployeeFilter selected = (EmployeeFilter) employeeFilter.getSelectedItem();
        int employeeId = selected == null ? 0 : selected.id;
        return reportData.orders.stream()
                .filter(order -> "COMPLETED".equalsIgnoreCase(order.getStatus()))
                .filter(order -> {
                    LocalDate date = parseDate(order.getCreatedAt());
                    return date != null && !date.isBefore(range.start) && !date.isAfter(range.end);
                })
                .filter(order -> user == null || !"STAFF".equalsIgnoreCase(user.getRole())
                        || processedBy(order) == user.getId())
                .filter(order -> !isAdmin() || employeeId == 0 || processedBy(order) == employeeId)
                .toList();
    }

    private void exportExcel() {
        if (reportData == null) return;
        List<Order> orders = filteredCompletedOrders();
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Xuất báo cáo Excel");
        chooser.setSelectedFile(new java.io.File("bao-cao-doanh-thu-" + LocalDate.now() + ".xlsx"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File file = chooser.getSelectedFile();
        String path = file.getAbsolutePath().toLowerCase(Locale.ROOT).endsWith(".xlsx")
                ? file.getAbsolutePath() : file.getAbsolutePath() + ".xlsx";
        try {
            writeWorkbook(java.nio.file.Path.of(path), orders);
            JOptionPane.showMessageDialog(this, "Đã xuất báo cáo: " + path,
                    "Xuất Excel thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Không thể xuất Excel: " + exception.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void writeWorkbook(java.nio.file.Path path, List<Order> orders) throws IOException {
        EmployeeFilter selected = (EmployeeFilter) employeeFilter.getSelectedItem();
        String scope = isAdmin() && selected != null && selected.id != 0
                ? selected.toString() : user != null && "STAFF".equalsIgnoreCase(user.getRole())
                        ? user.getFullName() : "Tất cả nhân viên";
        DateRange range = currentDateRange();
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("BÁO CÁO DOANH THU STORE DRINK"));
        rows.add(List.of("Kỳ báo cáo", range.label));
        rows.add(List.of("Phạm vi", scope));
        rows.add(List.of("", "", "", "", "", "", "", ""));
        rows.add(List.of("Mã đơn", "Nhân viên xử lý", "Khách hàng", "Loại đơn",
                "Ngày tạo", "Trạng thái", "Thanh toán", "Tổng tiền"));
        for (Order order : orders) {
            int employeeId = processedBy(order);
            rows.add(List.of(String.valueOf(order.getId()),
                    reportData.employeeNames.getOrDefault(employeeId, "Nhân viên #" + employeeId),
                    valueOrDash(customerName(order)), valueOrDash(order.getOrderType()),
                    valueOrDash(order.getCreatedAt()), statusLabel(order.getStatus()),
                    paymentLabel(order.getPaymentMethod()), String.valueOf(order.getTotal())));
        }
        double totalRevenue = orders.stream().mapToDouble(Order::getTotal).sum();
        double cashRevenue = orders.stream()
                .filter(order -> "CASH".equalsIgnoreCase(order.getPaymentMethod()))
                .mapToDouble(Order::getTotal).sum();
        double transferRevenue = orders.stream()
                .filter(order -> isTransfer(order.getPaymentMethod()))
                .mapToDouble(Order::getTotal).sum();
        rows.add(List.of("", "", "", "", "", "", "", ""));
        rows.add(List.of("Tổng doanh thu", "", "", "", "", "", "", String.valueOf(totalRevenue)));
        rows.add(List.of("Số đơn hoàn thành", String.valueOf(orders.size())));
        rows.add(List.of("Tiền mặt", "", "", "", "", "", "", String.valueOf(cashRevenue)));
        rows.add(List.of("Chuyển khoản", "", "", "", "", "", "", String.valueOf(transferRevenue)));
        if (isAdmin()) {
            rows.add(List.of("", "", "", "", "", "", "", ""));
            rows.add(List.of("Khách quen mua nhiều nhất"));
            for (String customer : calculate(reportData).topCustomers) {
                rows.add(List.of(customer));
            }
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            addZipEntry(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "</Types>");
            addZipEntry(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            addZipEntry(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                    + "<sheets><sheet name=\"Báo cáo\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            addZipEntry(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "</Relationships>");
            addZipEntry(zip, "xl/worksheets/sheet1.xml", sheetXml(rows));
        }
    }

    private String sheetXml(List<List<String>> rows) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            xml.append("<row r=\"").append(rowIndex + 1).append("\">");
            for (int column = 0; column < row.size(); column++) {
                String reference = columnName(column + 1) + (rowIndex + 1);
                String value = row.get(column);
                if (rowIndex >= 5 && column == 7) {
                    xml.append("<c r=\"").append(reference).append("\"><v>")
                            .append(xmlEscape(value)).append("</v></c>");
                } else {
                    xml.append("<c r=\"").append(reference).append("\" t=\"inlineStr\"><is><t>")
                            .append(xmlEscape(value)).append("</t></is></c>");
                }
            }
            xml.append("</row>");
        }
        return xml.append("</sheetData></worksheet>").toString();
    }

    private String columnName(int index) {
        StringBuilder name = new StringBuilder();
        while (index > 0) {
            int remainder = (index - 1) % 26;
            name.insert(0, (char) ('A' + remainder));
            index = (index - 1) / 26;
        }
        return name.toString();
    }

    private String xmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String customerName(Order order) {
        if (order.getCustomerName() != null && !order.getCustomerName().isBlank()) {
            return order.getCustomerName();
        }
        return reportData.employeeNames.get(order.getUserId());
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "Chờ duyệt";
            case "CONFIRMED" -> "Đã duyệt";
            case "COMPLETED" -> "Hoàn thành";
            case "CANCELLED" -> "Đã hủy";
            default -> valueOrDash(status);
        };
    }

    private String paymentLabel(String payment) {
        if ("CASH".equalsIgnoreCase(payment)) return "Tiền mặt";
        if (isTransfer(payment)) return "Chuyển khoản";
        return valueOrDash(payment);
    }

    private void showLoadError(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        content.removeAll();
        JLabel error = new JLabel("Không tải được báo cáo: " + cause.getMessage());
        error.setForeground(new Color(255, 128, 105));
        error.setBorder(new EmptyBorder(24, 18, 24, 18));
        content.add(buildFilterBarComponent(), BorderLayout.NORTH);
        content.add(error, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private LocalDate parseDate(String value) {
        try { return value == null ? null : LocalDateTime.parse(value).toLocalDate(); }
        catch (Exception exception) { return null; }
    }

    private boolean isTransfer(String method) {
        return "TRANSFER".equalsIgnoreCase(method) || "BANK".equalsIgnoreCase(method)
                || "CK".equalsIgnoreCase(method);
    }

    private String money(double value) {
        return currency.format(value) + " đ";
    }

    private record EmployeeFilter(int id, String name) {
        @Override public String toString() { return name; }
    }

    private record MonthFilter(YearMonth month) {
        @Override public String toString() {
            return month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("vi-VN")));
        }
    }

    private record DateRange(LocalDate start, LocalDate end, String label) { }

    private static class CustomerPurchase {
        final String name;
        int completedOrders;
        double totalSpent;

        CustomerPurchase(String name) {
            this.name = name;
        }
    }

    private static class ReportData {
        final List<Order> orders;
        final List<Drink> drinks;
        final List<User> employees;
        final Map<Integer, List<OrderDetail>> detailsByOrder;
        final Map<Integer, String> employeeNames = new HashMap<>();

        ReportData(List<Order> orders, List<Drink> drinks, List<User> employees,
                Map<Integer, List<OrderDetail>> detailsByOrder) {
            this.orders = orders;
            this.drinks = drinks;
            this.employees = employees == null ? List.of() : employees;
            this.detailsByOrder = detailsByOrder;
            for (User employee : this.employees) employeeNames.put(employee.getId(), employee.getFullName());
        }
    }

    private static class Stats {
        int completedOrders;
        double revenue;
        double cash;
        double transfer;
        String periodLabel;
        String employeeLabel;
        List<String> topDrinks = List.of("Chưa có dữ liệu");
        List<String> topCustomers = List.of("Chưa có dữ liệu");
    }
}
