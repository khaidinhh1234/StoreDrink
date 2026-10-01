package view.customer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class CustomerPanel extends JPanel {

    private JTable table;

    public CustomerPanel() {

        setLayout(new BorderLayout());

        setBackground(
                new Color(245, 247, 250));

        // =========================
        // HEADER
        // =========================

        JPanel header = new JPanel(
                new BorderLayout());

        header.setBackground(Color.WHITE);

        JLabel title = new JLabel("Quản lý khách hàng");

        title.setFont(
                new Font(
                        "Arial",
                        Font.BOLD,
                        24));

        JButton addButton = new JButton("Thêm khách hàng");

        header.setBorder(
                BorderFactory.createEmptyBorder(
                        20, 20, 20, 20));

        header.add(
                title,
                BorderLayout.WEST);

        header.add(
                addButton,
                BorderLayout.EAST);

        add(
                header,
                BorderLayout.NORTH);

        // =========================
        // TABLE
        // =========================

        String[] columns = {
                "ID",
                "Tên khách hàng",
                "Số điện thoại",
                "Email",
                "Thao tác"
        };

        DefaultTableModel model = new DefaultTableModel(
                columns,
                0);

        model.addRow(new Object[] {
                1,
                "Nguyễn Văn An",
                "0987654321",
                "an@gmail.com",
                "Sửa | Xóa"
        });

        model.addRow(new Object[] {
                2,
                "Trần Thị Bích",
                "0912345678",
                "bich@gmail.com",
                "Sửa | Xóa"
        });

        table = new JTable(model);

        add(
                new JScrollPane(table),
                BorderLayout.CENTER);
    }
}