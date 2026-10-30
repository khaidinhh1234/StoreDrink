package service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import model.Drink;
import model.Order;
import model.OrderDetail;
import model.User;

/** Loads report inputs and calculates dashboard statistics outside Swing components. */
public class DashboardReportService {
    public static final int LOW_STOCK_THRESHOLD = 10;

    private final OrderService orderService = new OrderService();
    private final DrinkService drinkService = new DrinkService();
    private final UserService userService = new UserService();

    public ReportData load(Integer staffUserId, boolean admin, User currentUser) throws Exception {
        List<Order> orders = staffUserId != null
                ? orderService.getForProcessor(staffUserId) : orderService.getAll();
        List<Drink> drinks = drinkService.getAll();
        List<User> employees = admin ? userService.getAll()
                : currentUser == null ? List.of() : List.of(currentUser);
        Map<Integer, List<OrderDetail>> detailsByOrder = new HashMap<>();
        for (Order order : orders) {
            if ("COMPLETED".equalsIgnoreCase(order.getStatus())) {
                detailsByOrder.put(order.getId(), orderService.getDetails(order.getId()));
            }
        }
        return new ReportData(orders, drinks, employees, detailsByOrder);
    }

    public Stats calculate(ReportData data, LocalDate start, LocalDate end, String period,
            int employeeId, Integer staffUserId, boolean admin, String staffName) {
        Map<Integer, String> drinkNames = new HashMap<>();
        Map<Integer, String> employeeNames = new HashMap<>(data.employeeNames);
        for (Drink drink : data.drinks) drinkNames.put(drink.getId(), drink.getName());

        Map<Integer, Integer> soldByDrink = new HashMap<>();
        Map<LocalDate, Double> revenueByDate = new HashMap<>();
        Map<Integer, Double> revenueByHour = new HashMap<>();
        Map<Integer, EmployeeRevenue> revenueByEmployee = new HashMap<>();
        Map<String, CustomerPurchase> customerPurchases = new HashMap<>();
        Stats stats = new Stats();
        for (Order order : data.orders) {
            if (!isCompletedPayment(order)) continue;
            LocalDate date = parseDate(order.getCompletedAt());
            if (date == null || date.isBefore(start) || date.isAfter(end)) continue;
            int processorId = processorId(order, data);
            if (staffUserId != null && processorId != staffUserId) continue;
            if (processorId > 0) {
                EmployeeRevenue employeeRevenue = revenueByEmployee.computeIfAbsent(processorId,
                        ignored -> new EmployeeRevenue(employeeNames.getOrDefault(processorId,
                                "Nhân viên #" + processorId)));
                employeeRevenue.revenue += order.getTotal();
                employeeRevenue.completedOrders++;
            }
            if (admin && employeeId != 0 && processorId != employeeId) continue;

            stats.completedOrders++;
            stats.revenue += order.getTotal();
            revenueByDate.merge(date, order.getTotal(), Double::sum);
            LocalDateTime completedAt = parseDateTime(order.getCompletedAt());
            if (completedAt != null && ("Hôm nay".equals(period) || "Theo ngày".equals(period))) {
                revenueByHour.merge(completedAt.getHour(), order.getTotal(), Double::sum);
            }
            if ("CASH".equalsIgnoreCase(order.getPaymentMethod())) stats.cash += order.getTotal();
            if (isTransfer(order.getPaymentMethod())) stats.transfer += order.getTotal();

            String name = order.getCustomerName();
            if ((name == null || name.isBlank()) && order.getUserId() > 0) {
                name = employeeNames.get(order.getUserId());
            }
            String customerName = name == null ? "" : name.trim().replaceAll("\\s+", " ");
            if (!customerName.isEmpty()) {
                String key = customerName.toLowerCase(Locale.ROOT);
                CustomerPurchase purchase = customerPurchases.computeIfAbsent(key,
                        ignored -> new CustomerPurchase(customerName));
                purchase.completedOrders++;
                purchase.totalSpent += order.getTotal();
            }
            for (OrderDetail detail : data.detailsByOrder.getOrDefault(order.getId(), List.of())) {
                soldByDrink.merge(detail.getDrinkId(), detail.getQuantity(), Integer::sum);
            }
        }

        stats.topDrinks = soldByDrink.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(10)
                .map(entry -> new ChartPoint(drinkNames.getOrDefault(entry.getKey(),
                        "Đồ uống #" + entry.getKey()), entry.getValue()))
                .toList();
        if (stats.topDrinks.isEmpty()) stats.topDrinks = List.of(new ChartPoint("Chưa có dữ liệu", 0));
        stats.periodLabel = periodLabel(start, end, period);
        stats.employeeLabel = admin && employeeId != 0
                ? employeeNames.getOrDefault(employeeId, "Nhân viên #" + employeeId)
                : staffUserId != null ? staffName : "Toàn hệ thống";
        stats.topCustomers = customerPurchases.values().stream()
                .sorted(Comparator.comparingInt((CustomerPurchase purchase) -> purchase.completedOrders)
                        .reversed().thenComparing(Comparator.comparingDouble(
                                (CustomerPurchase purchase) -> purchase.totalSpent).reversed()))
                .limit(8).toList();
        stats.employeeRevenue = revenueByEmployee.entrySet().stream()
                .filter(entry -> employeeId == 0 || entry.getKey() == employeeId)
                .sorted(Comparator.comparingDouble((Map.Entry<Integer, EmployeeRevenue> entry)
                        -> entry.getValue().revenue).reversed())
                .map(Map.Entry::getValue).toList();
        stats.revenueSeries = buildRevenueSeries(start, end, period, revenueByDate, revenueByHour);
        stats.metrics = calculateMetrics(data, start, end, employeeId, staffUserId, admin);
        stats.metrics.revenueInPeriod = stats.revenue;
        stats.untrackedCompletedOrders = (int) data.orders.stream()
                .filter(order -> "COMPLETED".equalsIgnoreCase(order.getStatus()))
                .filter(order -> order.getCompletedAt() == null || order.getCompletedAt().isBlank())
                .filter(order -> staffUserId == null || processorId(order, data) == staffUserId)
                .filter(order -> !admin || employeeId == 0 || processorId(order, data) == employeeId)
                .count();
        stats.unattributedCompletedOrders = (int) data.orders.stream()
                .filter(order -> "COMPLETED".equalsIgnoreCase(order.getStatus()))
                .filter(order -> processorId(order, data) == 0)
                .filter(order -> staffUserId == null && (!admin || employeeId == 0))
                .count();
        return stats;
    }

    public List<Order> completedInRange(ReportData data, LocalDate start, LocalDate end,
            int employeeId, Integer staffUserId, boolean admin) {
        return data.orders.stream()
                .filter(this::isCompletedPayment)
                .filter(order -> {
                    LocalDate date = parseDate(order.getCompletedAt());
                    return date != null && !date.isBefore(start) && !date.isAfter(end);
                })
                .filter(order -> staffUserId == null || processorId(order, data) == staffUserId)
                .filter(order -> !admin || employeeId == 0 || processorId(order, data) == employeeId)
                .toList();
    }

    public static int completedBy(Order order) {
        return order.getCompletedBy() == null ? 0 : order.getCompletedBy();
    }

    public int processorId(Order order, ReportData data) {
        // Older records may not contain completedBy. Fall back to the employee
        // who approved the order, then to the employee recorded as processing it.
        int processorId = completedBy(order);
        if (processorId <= 0 && order.getConfirmedBy() != null) processorId = order.getConfirmedBy();
        if (processorId <= 0) processorId = order.getProcessedByUserId();
        return data.employeeNames.containsKey(processorId) ? processorId : 0;
    }

    private MetricStats calculateMetrics(ReportData data, LocalDate start, LocalDate end,
            int employeeId, Integer staffUserId, boolean admin) {
        MetricStats metrics = new MetricStats();
        for (Order order : data.orders) {
            int processorId = processorId(order, data);
            if ((staffUserId != null && processorId != staffUserId)
                    || (admin && employeeId != 0 && processorId != employeeId)) continue;
            LocalDate createdDate = parseDate(order.getCreatedAt());
            if (createdDate != null && !createdDate.isBefore(start) && !createdDate.isAfter(end)) {
                metrics.ordersInPeriod++;
                if ("PENDING".equalsIgnoreCase(order.getStatus())) metrics.pendingOrders++;
            }
            if (!"COMPLETED".equalsIgnoreCase(order.getStatus())) {
                continue;
            }
            LocalDate completedDate = parseDate(order.getCompletedAt());
            if (completedDate == null) continue;
            boolean inRange = !completedDate.isBefore(start) && !completedDate.isAfter(end);
            if (inRange) metrics.completedInPeriod++;
            if (!isCompletedPayment(order)) continue;
            if (inRange) {
                if ("CASH".equalsIgnoreCase(order.getPaymentMethod())) metrics.cash += order.getTotal();
                if (isTransfer(order.getPaymentMethod())) metrics.transfer += order.getTotal();
            }
        }
        metrics.hasDrinkInventory = !data.drinks.isEmpty();
        if (metrics.hasDrinkInventory) {
            metrics.lowStockDrinks = (int) data.drinks.stream().filter(Drink::isActive)
                    .filter(drink -> drink.getQuantity() <= LOW_STOCK_THRESHOLD).count();
        }
        return metrics;
    }

    private List<ChartPoint> buildRevenueSeries(LocalDate start, LocalDate end, String period,
            Map<LocalDate, Double> byDate, Map<Integer, Double> byHour) {
        List<ChartPoint> points = new ArrayList<>();
        if ("Hôm nay".equals(period) || "Theo ngày".equals(period)) {
            for (int hour = 0; hour < 24; hour++) {
                points.add(new ChartPoint(String.format("%02dh", hour),
                        byHour.getOrDefault(hour, 0.0)));
            }
            return points;
        }
        if ("Theo năm".equals(period)) {
            for (int month = 1; month <= 12; month++) {
                YearMonth yearMonth = YearMonth.of(start.getYear(), month);
                double revenue = 0;
                for (LocalDate date = yearMonth.atDay(1); !date.isAfter(yearMonth.atEndOfMonth()); date = date.plusDays(1)) {
                    revenue += byDate.getOrDefault(date, 0.0);
                }
                points.add(new ChartPoint(yearMonth.format(DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("vi-VN"))), revenue));
            }
            return points;
        }
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String label = ("Tuần này".equals(period) || "Theo tuần".equals(period))
                    ? date.format(DateTimeFormatter.ofPattern("EE", Locale.forLanguageTag("vi-VN")))
                    : String.format("%02d", date.getDayOfMonth());
            points.add(new ChartPoint(label, byDate.getOrDefault(date, 0.0)));
        }
        return points;
    }

    private String periodLabel(LocalDate start, LocalDate end, String period) {
        if (start.equals(end)) return "Ngày " + start.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if ("Theo năm".equals(period)) return String.valueOf(start.getYear());
        if ("Theo tuần".equals(period) || "Tuần này".equals(period)) {
            return start.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " – "
                    + end.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
        if ("Theo tháng".equals(period)) return YearMonth.from(start).atDay(1)
                .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("vi-VN")));
        return start.getYear() + "";
    }

    private boolean isCompletedPayment(Order order) {
        return "COMPLETED".equalsIgnoreCase(order.getStatus())
                && ("CASH".equalsIgnoreCase(order.getPaymentMethod()) || isTransfer(order.getPaymentMethod()))
                && order.getCompletedAt() != null && !order.getCompletedAt().isBlank();
    }

    private boolean isTransfer(String payment) {
        return "TRANSFER".equalsIgnoreCase(payment) || "BANK".equalsIgnoreCase(payment)
                || "CK".equalsIgnoreCase(payment);
    }

    private LocalDate parseDate(String value) {
        LocalDateTime dateTime = parseDateTime(value);
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))) {
            try { return LocalDateTime.parse(value.trim(), formatter); }
            catch (Exception ignored) { }
        }
        try { return LocalDate.parse(value.trim()).atStartOfDay(); }
        catch (Exception ignored) { return null; }
    }

    public static class ReportData {
        public final List<Order> orders;
        public final List<Drink> drinks;
        public final List<User> employees;
        public final Map<Integer, List<OrderDetail>> detailsByOrder;
        public final Map<Integer, String> employeeNames = new HashMap<>();

        ReportData(List<Order> orders, List<Drink> drinks, List<User> employees,
                Map<Integer, List<OrderDetail>> detailsByOrder) {
            this.orders = orders;
            this.drinks = drinks;
            this.employees = employees == null ? List.of() : employees;
            this.detailsByOrder = detailsByOrder;
            for (User employee : this.employees) {
                if ("STAFF".equalsIgnoreCase(employee.getRole())) {
                    employeeNames.put(employee.getId(), employee.getFullName());
                }
            }
        }
    }

    public static class Stats {
        public int completedOrders;
        public int untrackedCompletedOrders;
        public int unattributedCompletedOrders;
        public double revenue;
        public double cash;
        public double transfer;
        public String periodLabel;
        public String employeeLabel;
        public List<ChartPoint> revenueSeries = List.of();
        public List<ChartPoint> topDrinks = List.of(new ChartPoint("Chưa có dữ liệu", 0));
        public List<CustomerPurchase> topCustomers = List.of();
        public List<EmployeeRevenue> employeeRevenue = List.of();
        public MetricStats metrics = new MetricStats();
    }

    public record ChartPoint(String label, double value) { }

    public static class CustomerPurchase {
        public final String name;
        public int completedOrders;
        public double totalSpent;
        CustomerPurchase(String name) { this.name = name; }
    }

    public static class EmployeeRevenue {
        public final String name;
        public int completedOrders;
        public double revenue;
        EmployeeRevenue(String name) { this.name = name; }
    }

    public static class MetricStats {
        public double revenueInPeriod;
        public int ordersInPeriod;
        public int pendingOrders;
        public int completedInPeriod;
        public double cash;
        public double transfer;
        public int lowStockDrinks;
        public boolean hasDrinkInventory;
    }
}
