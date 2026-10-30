package view.customer;

import model.StoreTable;
import service.OrderService;
import service.TableService;
import javax.swing.JFrame;
import java.util.List;

public class GuestOrderFrame extends JFrame {
    public GuestOrderFrame(int tableId) throws Exception {
        this(String.valueOf(tableId));
    }

    public GuestOrderFrame(String tableCode) throws Exception {
        String code = tableCode == null ? "" : tableCode.trim();
        List<StoreTable> tables = new TableService().getAll();
        StoreTable table = tables.stream()
                .filter(item -> matchesId(item, code)
                        || (item.getQrCode() != null && code.equalsIgnoreCase(item.getQrCode().trim())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Mã bàn không tồn tại."));
        if (!"Đang hoạt động".equalsIgnoreCase(table.getStatus())
                && !"ACTIVE".equalsIgnoreCase(table.getStatus())) {
            throw new IllegalArgumentException("Bàn này hiện không hoạt động.");
        }
        int tableId = table.getId();
        OrderService.startExpiryMonitor();
        setTitle("Store Drink - Đặt món tại bàn " + String.format("%02d", tableId));
        setSize(1200, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        add(new CustomerShopPanel(tableId));
    }

    private static boolean matchesId(StoreTable table, String code) {
        try {
            return table.getId() == Integer.parseInt(code);
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
