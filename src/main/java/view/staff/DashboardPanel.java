package view.staff;

import view.layout.NotificationDialog;

import model.Order;
import model.User;
import service.DashboardReportService;
import service.DashboardReportService.ChartPoint;
import service.DashboardReportService.CustomerPurchase;
import service.DashboardReportService.EmployeeRevenue;
import service.DashboardReportService.ReportData;
import service.DashboardReportService.Stats;
import view.layout.DataSync;
import view.layout.RefreshablePanel;
import view.layout.UITheme;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DashboardPanel extends JPanel implements RefreshablePanel {
    private final User user;
    private final DashboardReportService reportService = new DashboardReportService();
    private final JPanel content = new JPanel(new BorderLayout(0, 14));
    private final JComboBox<String> periodFilter = new JComboBox<>();
    private final JComboBox<PeriodSelection> periodValueFilter = new JComboBox<>();
    private final JLabel periodValueLabel = new JLabel("Kỳ:");
    private final JComboBox<EmployeeFilter> employeeFilter = new JComboBox<>();
    private final JButton exportButton = new ToolbarButton("Xuất Excel", UITheme.PRIMARY);
    private final JButton refreshButton = new ToolbarButton("Làm mới", UITheme.SECONDARY);
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
            periodFilter.addItem("Theo ngày");
            periodFilter.addItem("Theo tuần");
            periodFilter.addItem("Theo tháng");
            periodFilter.addItem("Theo năm");
            periodValueFilter.setPreferredSize(new Dimension(210, 36));
            updatePeriodValues();
        } else {
            periodFilter.addItem("Hôm nay");
            periodFilter.addItem("Tuần này");
            periodValueFilter.setVisible(false);
            periodValueLabel.setVisible(false);
        }
        buildFilterBar();
        loadStatistics();
        DataSync.register(this, this);
    }

    private void showCustomerWelcome() {
        JLabel message = new JLabel("Chào " + user.getFullName() + "! Hãy chọn đồ uống để bắt đầu đặt món.",
                SwingConstants.CENTER);
        message.setForeground(UITheme.TEXT);
        message.setFont(new Font("Segoe UI", Font.BOLD, 18));
        content.add(message, BorderLayout.CENTER);
    }

    private void buildFilterBar() {
        JPanel filters = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 8));
        filters.setBackground(UITheme.CARD);

        JLabel periodLabel = new JLabel("Thời gian:");
        styleFilterLabel(periodLabel);
        styleFilterCombo(periodFilter, 142);
        filters.add(filterGroup(periodLabel, periodFilter));

        if (isAdmin()) {
            styleFilterLabel(periodValueLabel);
            styleFilterCombo(periodValueFilter, 210);
            filters.add(filterGroup(periodValueLabel, periodValueFilter));
            JLabel employeeLabel = new JLabel("Nhân viên:");
            styleFilterLabel(employeeLabel);
            styleFilterCombo(employeeFilter, 190);
            filters.add(filterGroup(employeeLabel, employeeFilter));
        }

        styleToolbarButton(exportButton);
        styleToolbarButton(refreshButton);
        filters.add(filterActions());
        filters.putClientProperty("dashboard.filterToolbar", Boolean.TRUE);
        content.add(filters, BorderLayout.NORTH);

        periodFilter.addActionListener(event -> {
            if (isAdmin()) updatePeriodValues();
            content.revalidate();
            if (!updatingFilters) renderReport();
        });
        periodValueFilter.addActionListener(event -> {
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

    private void styleToolbarButton(JButton button) {
        // Toolbar actions use the AdminLTE blue/gray palette, regardless of global button defaults.
        button.putClientProperty("uiTheme.preserveCustomStyle", Boolean.TRUE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
    }

    private void styleFilterLabel(JLabel label) {
        label.setForeground(UITheme.TEXT);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setPreferredSize(new Dimension(label.getPreferredSize().width, 36));
        label.setVerticalAlignment(SwingConstants.CENTER);
    }

    private JPanel filterGroup(JLabel label, JComboBox<?> combo) {
        JPanel group = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        group.setOpaque(false);
        group.add(label);
        group.add(combo);
        return group;
    }

    private JPanel filterActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(exportButton);
        actions.add(refreshButton);
        return actions;
    }

    private void styleFilterCombo(JComboBox<?> combo, int width) {
        combo.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                BasicArrowButton arrow = new BasicArrowButton(SwingConstants.SOUTH,
                        UITheme.CARD, UITheme.BORDER, UITheme.MUTED, UITheme.CARD);
                arrow.setBorder(BorderFactory.createEmptyBorder());
                arrow.setOpaque(true);
                arrow.putClientProperty("uiTheme.preserveCustomStyle", Boolean.TRUE);
                return arrow;
            }
        });
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setFont(new Font("Segoe UI", Font.PLAIN, 13));
                setForeground(selected ? Color.WHITE : UITheme.TEXT);
                setBackground(selected ? UITheme.SUCCESS : UITheme.CARD);
                setBorder(new EmptyBorder(6, 9, 6, 9));
                return this;
            }
        });
        combo.setBackground(UITheme.CARD);
        combo.setForeground(UITheme.TEXT);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        combo.setOpaque(false);
        combo.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(UITheme.BORDER, 6), new EmptyBorder(0, 9, 0, 5)));
        combo.setPreferredSize(new Dimension(width, 36));
        combo.setMinimumSize(new Dimension(112, 36));
        combo.setMaximumSize(new Dimension(width, 36));
        combo.putClientProperty("uiTheme.preserveCustomStyle", Boolean.TRUE);
    }

    private void loadStatistics() {
        if (!refreshButton.isEnabled()) return;
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
                Integer staffId = user != null && "STAFF".equalsIgnoreCase(user.getRole())
                        ? user.getId() : null;
                return reportService.load(staffId, isAdmin(), user);
            }

            @Override
            protected void done() {
                try {
                    reportData = get();
                    updatePeriodValues();
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

    @Override public void refreshData() { loadStatistics(); }

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
                if (employee.isActive() && "STAFF".equalsIgnoreCase(employee.getRole())) {
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
        Stats stats = calculateCurrentStats();
        content.removeAll();
        content.add(buildFilterBarComponent(), BorderLayout.NORTH);
        content.add(createStatisticsView(stats), BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private Stats calculateCurrentStats() {
        DateRange range = currentDateRange();
        EmployeeFilter employee = (EmployeeFilter) employeeFilter.getSelectedItem();
        return reportService.calculate(reportData, range.start, range.end,
                String.valueOf(periodFilter.getSelectedItem()), employee == null ? 0 : employee.id,
                user != null && "STAFF".equalsIgnoreCase(user.getRole()) ? user.getId() : null,
                isAdmin(), user == null ? "" : user.getFullName());
    }

    private JPanel buildFilterBarComponent() {
        // Keep the filter widgets in one persistent toolbar while the report body refreshes.
        for (Component component : content.getComponents()) {
            if (component instanceof JPanel panel
                    && Boolean.TRUE.equals(panel.getClientProperty("dashboard.filterToolbar"))) {
                return panel;
            }
        }
        JPanel filters = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 8));
        filters.setBackground(UITheme.CARD);
        JLabel periodLabel = new JLabel("Thời gian:");
        styleFilterLabel(periodLabel);
        filters.add(filterGroup(periodLabel, periodFilter));
        if (isAdmin()) {
            styleFilterLabel(periodValueLabel);
            filters.add(filterGroup(periodValueLabel, periodValueFilter));
            JLabel employeeLabel = new JLabel("Nhân viên:");
            styleFilterLabel(employeeLabel);
            filters.add(filterGroup(employeeLabel, employeeFilter));
        }
        filters.add(filterActions());
        filters.putClientProperty("dashboard.filterToolbar", Boolean.TRUE);
        return filters;
    }

    private DateRange currentDateRange() {
        LocalDate today = LocalDate.now();
        String selected = String.valueOf(periodFilter.getSelectedItem());
        if (isAdmin()) {
            PeriodSelection selectedRange = (PeriodSelection) periodValueFilter.getSelectedItem();
            if (selectedRange != null) return new DateRange(selectedRange.start, selectedRange.end, selectedRange.label);
        }
        if ("Tuần này".equals(selected)) {
            return new DateRange(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today, "Tuần này");
        }
        return new DateRange(today, today, "Hôm nay");
    }

    private String periodNoun() {
        String selected = String.valueOf(periodFilter.getSelectedItem());
        if (selected.contains("tuần")) return "tuần";
        if (selected.contains("tháng")) return "tháng";
        if (selected.contains("năm")) return "năm";
        return "ngày";
    }

    private void updatePeriodValues() {
        if (!isAdmin()) return;
        String selected = String.valueOf(periodFilter.getSelectedItem());
        PeriodSelection previous = (PeriodSelection) periodValueFilter.getSelectedItem();
        updatingFilters = true;
        try {
            periodValueFilter.removeAllItems();
            LocalDate today = LocalDate.now();
            if ("Theo tuần".equals(selected)) {
                Set<LocalDate> weeks = new TreeSet<>(Comparator.reverseOrder());
                weeks.add(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
                for (Order order : reportData == null ? List.<Order>of() : reportData.orders) {
                    LocalDate date = orderDate(order.getCompletedAt());
                    if (date == null) date = orderDate(order.getCreatedAt());
                    if (date != null) weeks.add(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
                }
                for (LocalDate start : weeks) {
                    LocalDate end = start.plusDays(6);
                    periodValueFilter.addItem(new PeriodSelection(start, end,
                            start.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " – "
                                    + end.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
                }
            } else if ("Theo tháng".equals(selected)) {
                Set<YearMonth> months = new TreeSet<>(Comparator.reverseOrder());
                months.add(YearMonth.from(today));
                for (Order order : reportData == null ? List.<Order>of() : reportData.orders) {
                    LocalDate date = orderDate(order.getCompletedAt());
                    if (date == null) date = orderDate(order.getCreatedAt());
                    if (date != null) months.add(YearMonth.from(date));
                }
                for (YearMonth month : months) {
                    periodValueFilter.addItem(new PeriodSelection(month.atDay(1), month.atEndOfMonth(),
                            month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("vi-VN")))));
                }
            } else if ("Theo năm".equals(selected)) {
                Set<Integer> years = new TreeSet<>(Comparator.reverseOrder());
                years.add(today.getYear());
                for (Order order : reportData == null ? List.<Order>of() : reportData.orders) {
                    LocalDate date = orderDate(order.getCompletedAt());
                    if (date == null) date = orderDate(order.getCreatedAt());
                    if (date != null) years.add(date.getYear());
                }
                for (int year : years) {
                    periodValueFilter.addItem(new PeriodSelection(LocalDate.of(year, 1, 1),
                            LocalDate.of(year, 12, 31), String.valueOf(year)));
                }
            } else {
                Set<LocalDate> days = new TreeSet<>(Comparator.reverseOrder());
                days.add(today);
                for (Order order : reportData == null ? List.<Order>of() : reportData.orders) {
                    LocalDate date = orderDate(order.getCompletedAt());
                    if (date != null) days.add(date);
                    date = orderDate(order.getCreatedAt());
                    if (date != null) days.add(date);
                }
                for (LocalDate date : days) {
                    periodValueFilter.addItem(new PeriodSelection(date, date,
                            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
                }
            }
            if (previous != null) {
                for (int i = 0; i < periodValueFilter.getItemCount(); i++) {
                    PeriodSelection option = periodValueFilter.getItemAt(i);
                    if (option.start.equals(previous.start) && option.end.equals(previous.end)) {
                        periodValueFilter.setSelectedIndex(i);
                        break;
                    }
                }
            }
            periodValueFilter.setVisible(true);
            periodValueLabel.setVisible(true);
        } finally {
            updatingFilters = false;
        }
    }

    private LocalDate orderDate(String value) {
        if (value == null || value.length() < 10) return null;
        try { return LocalDate.parse(value.substring(0, 10)); }
        catch (Exception ignored) { return null; }
    }

    private JPanel createStatisticsView(Stats stats) {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(UITheme.BACKGROUND);
        JPanel summary = new JPanel(new BorderLayout(0, 12));
        summary.setOpaque(false);
        JLabel subtitle = new JLabel(stats.periodLabel + "  •  " + stats.employeeLabel);
        subtitle.setForeground(UITheme.MUTED);
        subtitle.setBorder(new EmptyBorder(2, 4, 0, 4));
        JPanel summaryHeading = new JPanel();
        summaryHeading.setLayout(new BoxLayout(summaryHeading, BoxLayout.Y_AXIS));
        summaryHeading.setOpaque(false);
        summaryHeading.add(subtitle);
        if (stats.untrackedCompletedOrders > 0) {
            JLabel warning = new JLabel(stats.untrackedCompletedOrders
                    + " đơn hoàn thành cũ thiếu thời điểm thanh toán; không được tính vào báo cáo theo kỳ.");
            warning.setForeground(new Color(0x85, 0x64, 0x04));
            warning.setBorder(new EmptyBorder(5, 4, 3, 4));
            summaryHeading.add(warning);
        }
        if (stats.unattributedCompletedOrders > 0) {
            JLabel warning = new JLabel(stats.unattributedCompletedOrders
                    + " đơn hoàn thành chưa có nhân viên xử lý được ghi nhận; không xuất hiện trong biểu đồ nhân viên.");
            warning.setForeground(UITheme.DANGER);
            warning.setBorder(new EmptyBorder(3, 4, 3, 4));
            summaryHeading.add(warning);
        }
        summary.add(summaryHeading, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(0, 3, 14, 14));
        cards.setBackground(UITheme.BACKGROUND);
        String periodName = periodNoun();
        cards.add(createCard("Doanh thu " + periodName, money(stats.revenue), UITheme.PRIMARY));
        cards.add(createCard("Đơn hàng trong " + periodName, String.valueOf(stats.metrics.ordersInPeriod), UITheme.INFO));
        if (isAdmin()) {
            cards.add(createCard("Đơn hoàn thành trong " + periodName,
                    String.valueOf(stats.metrics.completedInPeriod), UITheme.SECONDARY));
            cards.add(createCard("Đơn chờ duyệt", String.valueOf(stats.metrics.pendingOrders), UITheme.WARNING));
            if (stats.metrics.hasDrinkInventory) {
                cards.add(createCard("Đồ uống sắp hết (≤ " + DashboardReportService.LOW_STOCK_THRESHOLD + ")",
                String.valueOf(stats.metrics.lowStockDrinks), UITheme.DANGER));
            }
        } else {
            cards.add(createCard("Đơn hoàn thành trong " + periodName,
                    String.valueOf(stats.metrics.completedInPeriod), UITheme.SUCCESS));
            if (stats.metrics.completedInPeriod > 0) {
                cards.add(createCard("Tiền mặt", money(stats.metrics.cash), UITheme.SECONDARY));
                cards.add(createCard("Chuyển khoản", money(stats.metrics.transfer), UITheme.INFO));
            }
        }
        summary.add(cards, BorderLayout.CENTER);
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(summary);
        wrapper.add(Box.createVerticalStrut(16));

        JPanel revenueCard = createChartCard("Doanh thu theo thời gian", new RevenueChart(stats.revenueSeries));
        revenueCard.setPreferredSize(new Dimension(900, 255));
        revenueCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 255));
        revenueCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(revenueCard);
        wrapper.add(Box.createVerticalStrut(16));

        if (isAdmin()) {
            JPanel employeeCard = createChartCard("Doanh thu theo nhân viên · số đơn hoàn thành",
                    new EmployeeRevenueChart(stats.employeeRevenue));
            employeeCard.setPreferredSize(new Dimension(900, 270));
            employeeCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 270));
            employeeCard.setAlignmentX(Component.LEFT_ALIGNMENT);
            wrapper.add(employeeCard);
            wrapper.add(Box.createVerticalStrut(16));

            JPanel charts = new JPanel(new GridLayout(1, 2, 16, 0));
            charts.setOpaque(false);
            charts.add(createChartCard("Đồ uống bán chạy trong kỳ",
                    new VerticalBarChart(stats.topDrinks, "ly", UITheme.INFO)));
            charts.add(createCustomerRanking(stats.topCustomers));
            charts.setPreferredSize(new Dimension(900, 255));
            charts.setMaximumSize(new Dimension(Integer.MAX_VALUE, 255));
            charts.setAlignmentX(Component.LEFT_ALIGNMENT);
            wrapper.add(charts);
        } else {
            JPanel drinksCard = createChartCard("Đồ uống bán chạy trong kỳ",
                    new VerticalBarChart(stats.topDrinks, "ly", UITheme.INFO));
            drinksCard.setPreferredSize(new Dimension(900, 255));
            drinksCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 255));
            drinksCard.setAlignmentX(Component.LEFT_ALIGNMENT);
            wrapper.add(drinksCard);
        }
        wrapper.setBorder(new EmptyBorder(2, 2, 18, 2));
        JScrollPane scrollPane = new JScrollPane(wrapper);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(UITheme.BACKGROUND);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return createScrollHost(scrollPane);
    }

    private JPanel createScrollHost(JScrollPane scrollPane) {
        JPanel host = new JPanel(new BorderLayout());
        host.setBackground(UITheme.BACKGROUND);
        host.add(scrollPane, BorderLayout.CENTER);
        return host;
    }

    private JPanel createChartCard(String title, JComponent chart) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(UITheme.CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                new EmptyBorder(12, 14, 12, 14)));
        JLabel heading = new JLabel(title);
        heading.setForeground(UITheme.TEXT);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 14));
        panel.add(heading, BorderLayout.NORTH);
        panel.add(chart, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCard(String title, String value, Color accent) {
        JPanel panel = new JPanel();
        panel.setBackground(UITheme.CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                        BorderFactory.createLineBorder(UITheme.BORDER)),
                new EmptyBorder(14, 18, 14, 18)));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(UITheme.MUTED);
        JLabel valueLabel = new JLabel(value);
        valueLabel.setForeground(UITheme.TEXT);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        panel.add(Box.createVerticalStrut(8));
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(valueLabel);
        return panel;
    }

    private JPanel createCustomerRanking(List<CustomerPurchase> customers) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(UITheme.CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER), new EmptyBorder(12, 14, 12, 14)));
        JLabel title = new JLabel("Khách quen mua nhiều nhất · theo tên");
        title.setForeground(UITheme.TEXT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        panel.add(title, BorderLayout.NORTH);
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(
                new Object[] { "Khách hàng", "Đơn hoàn thành", "Tổng chi" }, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        for (CustomerPurchase purchase : customers) {
            model.addRow(new Object[] { purchase.name, purchase.completedOrders, money(purchase.totalSpent) });
        }
        if (customers.isEmpty()) {
            JLabel empty = new JLabel("Chưa có khách hoàn thành đơn trong kỳ này", SwingConstants.CENTER);
            empty.setForeground(UITheme.MUTED);
            panel.add(empty, BorderLayout.CENTER);
            return panel;
        }
        JTable table = new JTable(model);
        UITheme.styleTable(table);
        table.setRowHeight(32);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UITheme.CARD);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private final class RevenueChart extends JPanel {
        private final List<ChartPoint> points;

        RevenueChart(List<ChartPoint> points) {
            this.points = points;
            setOpaque(false);
            setPreferredSize(new Dimension(760, 190));
            setToolTipText("Doanh thu trong kỳ đã chọn");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int left = 74;
            int right = 18;
            int top = 12;
            int bottom = 34;
            int plotWidth = Math.max(1, getWidth() - left - right);
            int plotHeight = Math.max(1, getHeight() - top - bottom);
            double max = points.stream().mapToDouble(ChartPoint::value).max().orElse(0);
            if (max <= 0) max = 1;
            max = Math.ceil(max / 4.0) * 4.0;

            g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            for (int tick = 0; tick <= 4; tick++) {
                int y = top + plotHeight - (plotHeight * tick / 4);
                g.setColor(new Color(0xE9, 0xED, 0xF1));
                g.drawLine(left, y, left + plotWidth, y);
                g.setColor(UITheme.MUTED);
                g.drawString(axisMoney(max * tick / 4), 2, y + 4);
            }
            if (points.isEmpty()) {
                g.setColor(UITheme.MUTED);
                g.drawString("Chưa có dữ liệu doanh thu trong kỳ này", left + 12, top + plotHeight / 2);
                g.dispose();
                return;
            }

            Path2D line = new Path2D.Double();
            Path2D fill = new Path2D.Double();
            double step = points.size() <= 1 ? plotWidth : (double) plotWidth / (points.size() - 1);
            for (int i = 0; i < points.size(); i++) {
                double x = left + step * i;
                double y = top + plotHeight - points.get(i).value() / max * plotHeight;
                if (i == 0) {
                    line.moveTo(x, y);
                    fill.moveTo(x, top + plotHeight);
                    fill.lineTo(x, y);
                } else {
                    line.lineTo(x, y);
                    fill.lineTo(x, y);
                }
            }
            double lastX = left + step * (points.size() - 1);
            fill.lineTo(lastX, top + plotHeight);
            fill.closePath();
            g.setColor(new Color(UITheme.PRIMARY.getRed(), UITheme.PRIMARY.getGreen(),
                    UITheme.PRIMARY.getBlue(), 28));
            g.fill(fill);
            g.setColor(UITheme.PRIMARY);
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(line);

            int labelStep = Math.max(1, (int) Math.ceil(points.size() / 8.0));
            for (int i = 0; i < points.size(); i++) {
                double x = left + step * i;
                double y = top + plotHeight - points.get(i).value() / max * plotHeight;
                g.setColor(Color.WHITE);
                g.fillOval((int) x - 4, (int) y - 4, 8, 8);
                g.setColor(UITheme.PRIMARY);
                g.setStroke(new BasicStroke(2f));
                g.drawOval((int) x - 4, (int) y - 4, 8, 8);
                if (i % labelStep == 0 || i == points.size() - 1) {
                    String label = points.get(i).label();
                    int labelWidth = g.getFontMetrics().stringWidth(label);
                    g.setColor(UITheme.MUTED);
                    g.drawString(label, (int) x - labelWidth / 2, getHeight() - 8);
                }
            }
            g.dispose();
        }
    }

    private final class VerticalBarChart extends JPanel {
        private final List<ChartPoint> points;
        private final String unit;
        private final Color barColor;

        VerticalBarChart(List<ChartPoint> points, String unit, Color barColor) {
            this.points = points.stream().filter(point -> point.value() > 0).limit(5).toList();
            this.unit = unit;
            this.barColor = barColor;
            setOpaque(false);
            setPreferredSize(new Dimension(390, 205));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (points.isEmpty()) {
                g.setColor(UITheme.MUTED);
                g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                g.drawString("Chưa có dữ liệu trong kỳ này", 12, Math.max(24, getHeight() / 2));
                g.dispose();
                return;
            }
            int left = 34, right = 10, top = 20, bottom = 56;
            int plotWidth = Math.max(1, getWidth() - left - right);
            int plotHeight = Math.max(1, getHeight() - top - bottom);
            double max = points.stream().mapToDouble(ChartPoint::value).max().orElse(1);
            int slots = points.size();
            int slotWidth = plotWidth / slots;
            int barWidth = Math.max(18, Math.min(48, slotWidth / 2));
            g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            for (int tick = 0; tick <= 4; tick++) {
                int y = top + plotHeight - plotHeight * tick / 4;
                g.setColor(new Color(0xE9, 0xED, 0xF1));
                g.drawLine(left, y, left + plotWidth, y);
                g.setColor(UITheme.MUTED);
                g.drawString(String.format(Locale.ROOT, "%.0f", max * tick / 4), 2, y + 4);
            }
            for (int i = 0; i < points.size(); i++) {
                ChartPoint point = points.get(i);
                int centerX = left + slotWidth * i + slotWidth / 2;
                int barHeight = Math.max(2, (int) Math.round(plotHeight * point.value() / max));
                int barX = centerX - barWidth / 2;
                int barY = top + plotHeight - barHeight;
                g.setColor(barColor);
                g.fillRoundRect(barX, barY, barWidth, barHeight, 8, 8);
                g.setFont(new Font("Segoe UI", Font.BOLD, 10));
                g.setColor(UITheme.TEXT);
                String amount = String.format(Locale.ROOT, "%.0f %s", point.value(), unit);
                g.drawString(amount, centerX - g.getFontMetrics().stringWidth(amount) / 2, Math.max(12, barY - 5));
                g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                FontMetrics metrics = g.getFontMetrics();
                String label = ellipsize(point.label(), metrics, Math.max(32, slotWidth - 4));
                g.setColor(UITheme.MUTED);
                g.drawString(label, centerX - metrics.stringWidth(label) / 2, top + plotHeight + 18);
            }
            g.dispose();
        }
    }

    private final class EmployeeRevenueChart extends JPanel {
        private final List<EmployeeRevenue> employees;

        EmployeeRevenueChart(List<EmployeeRevenue> employees) {
            this.employees = employees.stream().limit(8).toList();
            setOpaque(false);
            setPreferredSize(new Dimension(760, 210));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (employees.isEmpty()) {
                g.setColor(UITheme.MUTED);
                g.drawString("Chưa có đơn hoàn thành trong kỳ này", 12, Math.max(24, getHeight() / 2));
                g.dispose();
                return;
            }
            int left = 78, right = 18, top = 18, bottom = 58;
            int plotWidth = Math.max(1, getWidth() - left - right);
            int plotHeight = Math.max(1, getHeight() - top - bottom);
            double max = employees.stream().mapToDouble(item -> item.revenue).max().orElse(1);
            int slotWidth = Math.max(1, plotWidth / employees.size());
            int barWidth = Math.max(18, Math.min(58, slotWidth / 2));
            for (int tick = 0; tick <= 4; tick++) {
                int y = top + plotHeight - plotHeight * tick / 4;
                g.setColor(new Color(0xE9, 0xED, 0xF1));
                g.drawLine(left, y, left + plotWidth, y);
                g.setColor(UITheme.MUTED);
                g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                g.drawString(axisMoney(max * tick / 4), 2, y + 4);
            }
            for (int i = 0; i < employees.size(); i++) {
                EmployeeRevenue item = employees.get(i);
                int centerX = left + slotWidth * i + slotWidth / 2;
                int barHeight = Math.max(2, (int) Math.round(plotHeight * item.revenue / max));
                int barY = top + plotHeight - barHeight;
                g.setColor(UITheme.INFO);
                g.fillRoundRect(centerX - barWidth / 2, barY, barWidth, barHeight, 8, 8);
                g.setColor(UITheme.TEXT);
                g.setFont(new Font("Segoe UI", Font.BOLD, 10));
                String amount = axisMoney(item.revenue);
                g.drawString(amount, centerX - g.getFontMetrics().stringWidth(amount) / 2,
                        Math.max(12, barY - 5));
                g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                String name = ellipsize(item.name, g.getFontMetrics(), Math.max(36, slotWidth - 4));
                g.setColor(UITheme.MUTED);
                g.drawString(name, centerX - g.getFontMetrics().stringWidth(name) / 2, top + plotHeight + 17);
                String count = item.completedOrders + " đơn";
                g.drawString(count, centerX - g.getFontMetrics().stringWidth(count) / 2, top + plotHeight + 32);
            }
            g.dispose();
        }
    }

    private String ellipsize(String text, FontMetrics metrics, int maxWidth) {
        if (metrics.stringWidth(text) <= maxWidth) return text;
        String shortened = text;
        while (!shortened.isEmpty() && metrics.stringWidth(shortened + "…") > maxWidth) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }

    private String axisMoney(double value) {
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1f tr", value / 1_000_000);
        if (value >= 1_000) return String.format(Locale.ROOT, "%.0f k", value / 1_000);
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private List<Order> filteredCompletedOrders() {
        if (reportData == null) return List.of();
        DateRange range = currentDateRange();
        EmployeeFilter selected = (EmployeeFilter) employeeFilter.getSelectedItem();
        int employeeId = selected == null ? 0 : selected.id;
        Integer staffId = user != null && "STAFF".equalsIgnoreCase(user.getRole()) ? user.getId() : null;
        return reportService.completedInRange(reportData, range.start, range.end,
                employeeId, staffId, isAdmin());
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
            NotificationDialog.showMessageDialog(this, "Đã xuất báo cáo: " + path,
                    "Xuất Excel thành công", NotificationDialog.INFORMATION_MESSAGE);
        } catch (Exception exception) {
            NotificationDialog.showMessageDialog(this, "Không thể xuất Excel: " + exception.getMessage(),
                    "Lỗi", NotificationDialog.ERROR_MESSAGE);
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
            int employeeId = reportService.processorId(order, reportData);
            rows.add(List.of(String.valueOf(order.getId()),
                    reportData.employeeNames.getOrDefault(employeeId, "Chưa ghi nhận"),
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
            for (CustomerPurchase customer : calculateCurrentStats().topCustomers) {
                rows.add(List.of(customer.name + " • " + customer.completedOrders + " đơn • "
                        + money(customer.totalSpent)));
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
        error.setForeground(UITheme.DANGER);
        error.setBorder(new EmptyBorder(24, 18, 24, 18));
        content.add(buildFilterBarComponent(), BorderLayout.NORTH);
        content.add(error, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private boolean isTransfer(String method) {
        return "TRANSFER".equalsIgnoreCase(method) || "BANK".equalsIgnoreCase(method)
                || "CK".equalsIgnoreCase(method);
    }

    private String money(double value) {
        return UITheme.formatVnd(value);
    }

    private static final class ToolbarButton extends JButton {
        ToolbarButton(String text, Color color) {
            super(text);
            setBackground(color);
            setForeground(Color.WHITE);
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(new EmptyBorder(0, 14, 0, 14));
            setPreferredSize(new Dimension(text.equals("Xuất Excel") ? 132 : 112, 36));
            setMinimumSize(new Dimension(102, 36));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) {
                    if (isEnabled()) setBackground(color.darker());
                }
                @Override public void mouseExited(MouseEvent event) {
                    setBackground(color);
                }
            });
            addPropertyChangeListener("enabled", event -> {
                if (!isEnabled()) setForeground(UITheme.MUTED);
                else setForeground(Color.WHITE);
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = isEnabled() ? getBackground() : new Color(0xE9, 0xEC, 0xEF);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g.setColor(isEnabled() ? fill.darker() : UITheme.BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int radius;

        RoundedBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(component.hasFocus() ? UITheme.PRIMARY : color);
            g.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g.dispose();
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(1, 1, 1, 1);
        }
    }

    private static final class WrapLayout extends FlowLayout {
        WrapLayout(int alignment, int horizontalGap, int verticalGap) {
            super(alignment, horizontalGap, verticalGap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, false);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension size = layoutSize(target, true);
            size.width -= getHgap() + 1;
            return size;
        }

        private Dimension layoutSize(Container target, boolean minimum) {
            synchronized (target.getTreeLock()) {
                Container parent = target.getParent();
                int availableWidth = target.getWidth();
                if (parent != null && parent.getWidth() > 0) {
                    Insets parentInsets = parent.getInsets();
                    availableWidth = parent.getWidth() - parentInsets.left - parentInsets.right;
                }
                if (availableWidth <= 0) availableWidth = 1120;
                Insets insets = target.getInsets();
                int maxRowWidth = Math.max(1, availableWidth - insets.left - insets.right
                        - getHgap() * 2);
                int rowWidth = 0;
                int rowHeight = 0;
                int totalHeight = insets.top + insets.bottom + getVgap() * 2;
                int widestRow = 0;
                boolean hasRow = false;
                for (Component component : target.getComponents()) {
                    if (!component.isVisible()) continue;
                    Dimension size = minimum ? component.getMinimumSize() : component.getPreferredSize();
                    int nextWidth = rowWidth == 0 ? size.width : rowWidth + getHgap() + size.width;
                    if (rowWidth > 0 && nextWidth > maxRowWidth) {
                        widestRow = Math.max(widestRow, rowWidth);
                        totalHeight += rowHeight + getVgap();
                        rowWidth = size.width;
                        rowHeight = size.height;
                    } else {
                        rowWidth = nextWidth;
                        rowHeight = Math.max(rowHeight, size.height);
                    }
                    hasRow = true;
                }
                if (hasRow) {
                    widestRow = Math.max(widestRow, rowWidth);
                    totalHeight += rowHeight;
                }
                return new Dimension(Math.min(availableWidth, widestRow + insets.left + insets.right
                        + getHgap() * 2), totalHeight);
            }
        }
    }

    private record EmployeeFilter(int id, String name) {
        @Override public String toString() { return name; }
    }

    private record PeriodSelection(LocalDate start, LocalDate end, String label) {
        @Override public String toString() { return label; }
    }
    private record DateRange(LocalDate start, LocalDate end, String label) { }

}
