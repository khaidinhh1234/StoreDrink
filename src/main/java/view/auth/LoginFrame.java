package view.auth;

import model.User;
import service.UserService;
import view.customer.GuestOrderFrame;
import view.layout.MainView;

import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {
    private final JTextField usernameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JButton loginButton = new JButton("Đăng nhập");
    private final JButton guestOrderButton = new JButton("Khách đặt món tại bàn");
    private final UserService userService = new UserService();

    public LoginFrame() {
        setTitle("Store Drink - Đăng nhập");
        setSize(440, 320);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        buildView();
    }

    private void buildView() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(28, 36, 28, 36));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 8, 8, 8);
        constraints.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Đăng nhập Store Drink", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 24));
        constraints.gridwidth = 2;
        constraints.gridx = 0;
        constraints.gridy = 0;
        form.add(title, constraints);

        constraints.gridwidth = 1;
        constraints.gridy++;
        form.add(new JLabel("Tài khoản:"), constraints);
        constraints.gridx = 1;
        form.add(usernameField, constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        form.add(new JLabel("Mật khẩu:"), constraints);
        constraints.gridx = 1;
        form.add(passwordField, constraints);

        constraints.gridx = 1;
        constraints.gridy++;
        form.add(loginButton, constraints);
        constraints.gridy++;
        form.add(guestOrderButton, constraints);

        loginButton.addActionListener(e -> authenticate());
        passwordField.addActionListener(e -> authenticate());
        guestOrderButton.addActionListener(e -> openGuestOrder());
        add(form);
    }

    private void openGuestOrder() {
        String value = JOptionPane.showInputDialog(this,
                "Nhập mã bàn trên QR hoặc tại bàn:", "Đặt món tại bàn",
                JOptionPane.QUESTION_MESSAGE);
        if (value == null) {
            return;
        }
        try {
            int tableId = Integer.parseInt(value.trim());
            if (tableId <= 0) {
                throw new NumberFormatException();
            }
            new GuestOrderFrame(tableId).setVisible(true);
            dispose();
        } catch (NumberFormatException exception) {
            showError("Mã bàn phải là số nguyên dương.");
        }
    }

    private void authenticate() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập đầy đủ tài khoản và mật khẩu.");
            return;
        }

        loginButton.setEnabled(false);
        try {
            User user = userService.authenticate(username, password);
            if (user == null) {
                showError("Tài khoản hoặc mật khẩu không đúng.");
                return;
            }
            new MainView(user).setVisible(true);
            dispose();
        } catch (Exception exception) {
            exception.printStackTrace();
            showError("Không thể kết nối máy chủ: " + exception.getMessage());
        } finally {
            loginButton.setEnabled(true);
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Đăng nhập", JOptionPane.ERROR_MESSAGE);
    }
}
