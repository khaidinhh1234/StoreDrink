package view.layout;

import model.User;
import view.auth.LoginFrame;
import view.admin.CategoryPanel;
import view.admin.EmployeePanel;
import view.admin.TablePanel;
import view.customer.CustomerCartPanel;
import view.customer.CustomerDrinkPanel;
import view.customer.CustomerPanel;
import view.customer.CustomerShopPanel;
import view.staff.DashboardPanel;
import view.staff.DrinkPanel;
import view.staff.OrderPanel;
import javax.swing.*;
import java.awt.*;

public class MainView extends JFrame {

        private CardLayout cardLayout;
        private JPanel contentPanel;
        private final User user;
        private CustomerCartPanel customerCartPanel;
        private static final String COUNTER_ORDER = "COUNTER_ORDER";

        public MainView(User user) {
                this.user = user;

                setTitle("Store Drink - " + user.getRole());
                setSize(1200, 700);
                setLocationRelativeTo(null);
                setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                // =========================
                // CARD LAYOUT
                // =========================

                cardLayout = new CardLayout();

                contentPanel = new JPanel(cardLayout);

                boolean customer = "CUSTOMER".equalsIgnoreCase(user.getRole());
                boolean staff = "STAFF".equalsIgnoreCase(user.getRole());
                boolean admin = "ADMIN".equalsIgnoreCase(user.getRole());

                if (admin || staff) {
                        contentPanel.add(new DashboardPanel(admin ? "Dashboard quản trị" : "Thống kê vận hành"), "DASHBOARD");
                        contentPanel.add(new OrderPanel(user), "ORDER");
                }
                if (admin) {
                        contentPanel.add(new DrinkPanel(), "DRINK");
                        contentPanel.add(new CategoryPanel(), "CATEGORY");
                        contentPanel.add(new EmployeePanel(), "EMPLOYEE");
                        contentPanel.add(new TablePanel(), "TABLE");
                        contentPanel.add(new CustomerPanel(), "CUSTOMER");
                }
                if (customer) {
                        contentPanel.add(new DashboardPanel("Trang chủ"), "DASHBOARD");
                        contentPanel.add(new CustomerDrinkPanel(), "DRINK");
                }
                if ("CUSTOMER".equalsIgnoreCase(user.getRole())) {
                        customerCartPanel = new CustomerCartPanel(user);
                        contentPanel.add(customerCartPanel, "CART");
                }
                if (staff) {
                        contentPanel.add(new CustomerShopPanel(user, true), COUNTER_ORDER);
                }

                // =========================
                // SIDEBAR
                // =========================

                JPanel sidebar = createSidebar();

                // =========================
                // FRAME
                // =========================

                setLayout(new BorderLayout());

                add(sidebar, BorderLayout.WEST);
                add(contentPanel, BorderLayout.CENTER);

                cardLayout.show(contentPanel, customer ? "DRINK" : staff ? "ORDER" : "DASHBOARD");
        }

        private JPanel createSidebar() {

                JPanel sidebar = new JPanel();

                sidebar.setPreferredSize(new Dimension(220, 0));
                sidebar.setBackground(new Color(25, 35, 50));

                sidebar.setLayout(new BorderLayout());

                // =========================
                // LOGO
                // =========================

                JLabel logo = new JLabel("  Store Drink");

                logo.setForeground(Color.WHITE);
                logo.setFont(new Font("Arial", Font.BOLD, 20));

                logo.setPreferredSize(new Dimension(220, 70));

                sidebar.add(logo, BorderLayout.NORTH);

                // =========================
                // MENU
                // =========================

                JPanel menuPanel = new JPanel();

                menuPanel.setBackground(new Color(25, 35, 50));

                menuPanel.setLayout(
                                new BoxLayout(menuPanel, BoxLayout.Y_AXIS));

                JButton dashboardButton = createMenuButton("  Dashboard");
                JButton customerButton = createMenuButton("  Khách hàng");
                JButton drinkButton = createMenuButton("  Đồ uống");
                JButton categoryButton = createMenuButton("  Danh mục");
                JButton employeeButton = createMenuButton("  Nhân viên");
                JButton tableButton = createMenuButton("  Bàn");
                JButton orderButton = createMenuButton("  Đơn hàng");
                JButton counterOrderButton = createMenuButton("  Tạo đơn tại quầy");
                JButton cartButton = createMenuButton("  Giỏ hàng");

                menuPanel.add(dashboardButton);
                menuPanel.add(customerButton);
                menuPanel.add(drinkButton);
                menuPanel.add(categoryButton);
                menuPanel.add(employeeButton);
                menuPanel.add(tableButton);
                menuPanel.add(orderButton);
                menuPanel.add(counterOrderButton);
                menuPanel.add(cartButton);

                if ("CUSTOMER".equalsIgnoreCase(user.getRole())) {
                        customerButton.setVisible(false);
                        dashboardButton.setText("  Trang chủ");
                        orderButton.setVisible(false);
                        counterOrderButton.setVisible(false);
                        categoryButton.setVisible(false);
                        employeeButton.setVisible(false);
                        tableButton.setVisible(false);
                        cartButton.setVisible(true);
                } else if ("STAFF".equalsIgnoreCase(user.getRole())) {
                        dashboardButton.setVisible(true);
                        dashboardButton.setText("  Thống kê");
                        customerButton.setVisible(false);
                        drinkButton.setVisible(false);
                        categoryButton.setVisible(false);
                        employeeButton.setVisible(false);
                        tableButton.setVisible(false);
                        counterOrderButton.setVisible(true);
                        cartButton.setVisible(false);
                } else if ("ADMIN".equalsIgnoreCase(user.getRole())) {
                        customerButton.setVisible(false);
                        counterOrderButton.setVisible(false);
                        cartButton.setVisible(false);
                }

                sidebar.add(menuPanel, BorderLayout.CENTER);

                // =========================
                // LOGOUT
                // =========================

                JButton logoutButton = createMenuButton("  Đăng xuất");

                JPanel bottom = new JPanel(
                                new BorderLayout());

                bottom.setBackground(
                                new Color(25, 35, 50));

                bottom.add(
                                logoutButton,
                                BorderLayout.CENTER);

                sidebar.add(bottom, BorderLayout.SOUTH);

                // =========================
                // EVENTS
                // =========================

                dashboardButton.addActionListener(e -> showPage("DASHBOARD"));
                customerButton.addActionListener(e -> showPage("CUSTOMER"));

                drinkButton.addActionListener(e -> showPage("DRINK"));
                categoryButton.addActionListener(e -> showPage("CATEGORY"));
                employeeButton.addActionListener(e -> showPage("EMPLOYEE"));
                tableButton.addActionListener(e -> showPage("TABLE"));

                orderButton.addActionListener(e -> showPage("ORDER"));
                counterOrderButton.addActionListener(e -> showPage(COUNTER_ORDER));
                cartButton.addActionListener(e -> {
                        customerCartPanel.refresh();
                        showPage("CART");
                });

                logoutButton.addActionListener(e -> {
                        dispose();
                        new LoginFrame().setVisible(true);
                });

                return sidebar;
        }

        private JButton createMenuButton(String text) {

                JButton button = new JButton(text);

                button.setMaximumSize(
                                new Dimension(220, 50));

                button.setPreferredSize(
                                new Dimension(220, 50));

                button.setHorizontalAlignment(
                                SwingConstants.LEFT);

                button.setForeground(Color.WHITE);

                button.setBackground(
                                new Color(25, 35, 50));

                button.setBorderPainted(false);

                button.setFocusPainted(false);

                button.setFont(
                                new Font("Arial", Font.PLAIN, 15));

                return button;
        }

        private void showPage(String page) {

                cardLayout.show(
                                contentPanel,
                                page);
        }
}