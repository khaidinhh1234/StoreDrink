package view.customer;

import javax.swing.JFrame;

public class GuestOrderFrame extends JFrame {
    public GuestOrderFrame(int tableId) {
        setTitle("Store Drink - Đặt món tại bàn " + String.format("%02d", tableId));
        setSize(1200, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        add(new CustomerShopPanel(tableId));
    }
}
