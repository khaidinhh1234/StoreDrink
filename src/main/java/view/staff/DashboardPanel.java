package view.staff;

import model.Drink;
import model.Order;
import model.OrderDetail;
import service.DrinkService;
import service.OrderService;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardPanel extends JPanel {
    private final OrderService orderService = new OrderService();
    private final DrinkService drinkService = new DrinkService();
    private final NumberFormat currency = NumberFormat.getInstance(Locale.of("vi", "VN"));
    private final JPanel content = new JPanel(new BorderLayout());

    public DashboardPanel() {
        this("Tổng quan");
    }

    public DashboardPanel(String titleText) {

        setBackground(
                new Color(245, 247, 250));

        setLayout(new BorderLayout());

        JLabel title = new JLabel(titleText);

        title.setFont(
                new Font("Arial", Font.BOLD, 28));

        title.setBorder(
                BorderFactory.createEmptyBorder(
                        25, 30, 20, 30));

        add(title, BorderLayout.NORTH);

        content.setBackground(new Color(245, 247, 250));
        add(content, BorderLayout.CENTER);
        loadStatistics();
    }

    private void loadStatistics() {
        JLabel loading = new JLabel("Đang tải thống kê...", SwingConstants.CENTER);
        loading.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        content.add(loading, BorderLayout.CENTER);
        new SwingWorker<Stats, Void>() {
            @Override
            protected Stats doInBackground() throws Exception {
                return calculate(orderService.getAll(), drinkService.getAll());
            }

            @Override
            protected void done() {
                try {
                    content.removeAll();
                    content.add(createStatisticsView(get()), BorderLayout.CENTER);
                    content.revalidate();
                    content.repaint();
                } catch (Exception exception) {
                    content.removeAll();
                    JLabel error = new JLabel("Không tải được thống kê: " + exception.getMessage());
                    error.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
                    content.add(error);
                    content.revalidate();
                }
            }
        }.execute();
    }

    private Stats calculate(List<Order> orders, List<Drink> drinks) throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<Integer, String> drinkNames = new HashMap<>();
        for (Drink drink : drinks) drinkNames.put(drink.getId(), drink.getName());
        Map<Integer, Integer> dailyTop = new HashMap<>();
        Map<Integer, Integer> weeklyTop = new HashMap<>();
        Stats stats = new Stats();
        for (Order order : orders) {
            LocalDate date = parseDate(order.getCreatedAt());
            if (date == null) continue;
            boolean completed = "COMPLETED".equalsIgnoreCase(order.getStatus());
            if (date.equals(today)) stats.dailyOrders++;
            if (date.isBefore(monday) || date.isAfter(today)) continue;
            if (completed) {
                stats.weeklyRevenue += order.getTotal();
                if (date.equals(today)) {
                    stats.dailyRevenue += order.getTotal();
                    if ("CASH".equalsIgnoreCase(order.getPaymentMethod())) stats.dailyCash += order.getTotal();
                    if (isTransfer(order.getPaymentMethod())) stats.dailyTransfer += order.getTotal();
                }
                if ("CASH".equalsIgnoreCase(order.getPaymentMethod())) stats.weeklyCash += order.getTotal();
                if (isTransfer(order.getPaymentMethod())) stats.weeklyTransfer += order.getTotal();
                for (OrderDetail detail : orderService.getDetails(order.getId())) {
                    weeklyTop.merge(detail.getDrinkId(), detail.getQuantity(), Integer::sum);
                    if (date.equals(today)) dailyTop.merge(detail.getDrinkId(), detail.getQuantity(), Integer::sum);
                }
            }
        }
        stats.dailyTop = topNames(dailyTop, drinkNames);
        stats.weeklyTop = topNames(weeklyTop, drinkNames);
        return stats;
    }

    private JPanel createStatisticsView(Stats stats) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 15));
        wrapper.setBackground(new Color(245, 247, 250));
        JPanel cards = new JPanel(new GridLayout(2, 4, 15, 15));
        cards.setBackground(new Color(245, 247, 250));
        cards.setBorder(BorderFactory.createEmptyBorder(10, 30, 0, 30));
        cards.add(createCard("Doanh thu hôm nay", money(stats.dailyRevenue)));
        cards.add(createCard("Tiền mặt hôm nay", money(stats.dailyCash)));
        cards.add(createCard("Chuyển khoản hôm nay", money(stats.dailyTransfer)));
        cards.add(createCard("Đơn hôm nay", String.valueOf(stats.dailyOrders)));
        cards.add(createCard("Doanh thu tuần", money(stats.weeklyRevenue)));
        cards.add(createCard("Tiền mặt tuần", money(stats.weeklyCash)));
        cards.add(createCard("Chuyển khoản tuần", money(stats.weeklyTransfer)));
        cards.add(createCard("Thống kê", "Đơn đã hoàn thành"));
        wrapper.add(cards, BorderLayout.NORTH);

        JPanel rankings = new JPanel(new GridLayout(1, 2, 15, 15));
        rankings.setBackground(new Color(245, 247, 250));
        rankings.setBorder(BorderFactory.createEmptyBorder(0, 30, 30, 30));
        rankings.add(createRanking("Đồ uống bán chạy hôm nay", stats.dailyTop));
        rankings.add(createRanking("Đồ uống bán chạy trong tuần", stats.weeklyTop));
        wrapper.add(rankings, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createRanking(String title, List<String> rows) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.setBackground(Color.WHITE);
        panel.add(new JScrollPane(new JList<>(rows.toArray(String[]::new))), BorderLayout.CENTER);
        return panel;
    }

    private List<String> topNames(Map<Integer, Integer> quantities, Map<Integer, String> names) {
        return quantities.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(10)
                .map(entry -> names.getOrDefault(entry.getKey(), "Đồ uống #" + entry.getKey())
                        + " - " + entry.getValue() + " ly")
                .toList();
    }

    private LocalDate parseDate(String value) {
        try { return value == null ? null : LocalDateTime.parse(value).toLocalDate(); }
        catch (Exception exception) { return null; }
    }

    private boolean isTransfer(String method) {
        return "TRANSFER".equalsIgnoreCase(method) || "BANK".equalsIgnoreCase(method)
                || "CK".equalsIgnoreCase(method);
    }

    private String money(double value) { return currency.format(value) + " đ"; }

    private static class Stats {
        int dailyOrders;
        double dailyRevenue, dailyCash, dailyTransfer, weeklyRevenue, weeklyCash, weeklyTransfer;
        List<String> dailyTop = List.of("Chưa có dữ liệu");
        List<String> weeklyTop = List.of("Chưa có dữ liệu");
    }

    private JPanel createCard(
            String title,
            String value) {

        JPanel panel = new JPanel();

        panel.setBackground(new Color(25, 35, 50));

        panel.setBorder(
                BorderFactory.createEmptyBorder(
                        20, 20, 20, 20));

        panel.setLayout(
                new BoxLayout(
                        panel,
                        BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Color.WHITE);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setForeground(Color.WHITE);

        valueLabel.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        25));

        panel.add(Box.createVerticalStrut(20));

        panel.add(titleLabel);

        panel.add(Box.createVerticalStrut(10));

        panel.add(valueLabel);

        return panel;
    }
}