package view.staff;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import model.User;
import service.CashierRequestService;
import view.layout.RefreshablePanel;
import view.layout.UITheme;

public class CashierRequestsPanel extends JPanel implements RefreshablePanel {
    private final User user;
    private final CashierRequestService service = new CashierRequestService();
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{
            "Mã tạm", "Khách hàng", "Phương thức", "Bàn", "Tổng tiền", "Trạng thái",
            "Xem chi tiết", "Xác nhận", "Từ chối"
    }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<CashierRequestService.Request> visibleRequests = List.of();

    public CashierRequestsPanel(User user) {
        this.user = user;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JLabel title = new JLabel("YÊU CẦU THANH TOÁN TẠI QUẦY");
        title.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 23));
        title.setForeground(UITheme.TEXT);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        add(title, BorderLayout.NORTH);

        UITheme.styleTable(table);
        table.setRowHeight(46);
        table.getColumnModel().getColumn(6).setPreferredWidth(110);
        table.getColumnModel().getColumn(7).setPreferredWidth(110);
        table.getColumnModel().getColumn(8).setPreferredWidth(100);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton refresh = new JButton("Làm mới");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(refresh);
        add(actions, BorderLayout.SOUTH);
        refresh.addActionListener(event -> refreshData());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                int row = table.rowAtPoint(event.getPoint());
                int column = table.columnAtPoint(event.getPoint());
                if (row < 0) return;
                table.setRowSelectionInterval(row, row);
                if (column == 6) showOrder();
                else if (column == 7) confirmPayment();
                else if (column == 8) {
                    String action = String.valueOf(model.getValueAt(table.convertRowIndexToModel(row), 8));
                    if ("Xác nhận hoàn tiền".equals(action)) confirmRefund();
                    else if ("Đối soát".equals(action)) rejectPayment();
                    else if ("Từ chối".equals(action)) rejectPayment();
                }
                else if (event.getClickCount() == 2) showOrder();
            }
        });
        refreshData();
    }

    @Override public void refreshData() {
        try {
            List<CashierRequestService.Request> requests = service.getAll();
            visibleRequests = requests.stream().filter(request ->
                    "WAITING_CASHIER".equals(request.status)
                            || "WAITING_CONFIRMATION".equals(request.status)
                            || "PROCESSING".equals(request.status)
                            || "REFUND_PENDING".equals(request.status)
                            || ("FAILED".equals(request.status)
                                    && ("CASH".equalsIgnoreCase(request.paymentMethod)
                                            || "TRANSFER".equalsIgnoreCase(request.paymentMethod))
                                    && (request.refundStatus == null || request.refundStatus.isBlank()))).toList();
            model.setRowCount(0);
            for (CashierRequestService.Request request : visibleRequests) {
                boolean processing = "PROCESSING".equals(request.status);
                boolean refundPending = "REFUND_PENDING".equals(request.status);
                boolean reconcile = "FAILED".equals(request.status);
                boolean cash = "CASH".equals(request.paymentMethod);
                model.addRow(new Object[]{
                        "TMP" + request.id,
                        request.customerName,
                        cash ? "Tiền mặt" : "Chuyển khoản",
                        request.tableId == null ? "-" : String.format("%02d", request.tableId),
                        String.format("%,.0f đ", request.total),
                        processing ? "Đang xử lý · có thể tiếp tục"
                                : refundPending ? "Đã nhận tiền · chờ hoàn khách"
                                : reconcile ? "Đã từ chối · cần đối soát tiền"
                                : cash ? "Chờ nhận tiền" : "Chờ xác nhận chuyển khoản",
                        "Xem chi tiết",
                        processing ? "Tiếp tục" : refundPending || reconcile ? "-" : "Xác nhận",
                        refundPending ? "Xác nhận hoàn tiền" : reconcile ? "Đối soát" : processing ? "-" : "Từ chối"
                });
            }
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Không tải được yêu cầu: " + exception.getMessage());
        }
    }

    private CashierRequestService.Request selectedRequest() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= visibleRequests.size()) {
            JOptionPane.showMessageDialog(this, "Chọn yêu cầu cần xử lý.");
            return null;
        }
        return visibleRequests.get(table.convertRowIndexToModel(row));
    }

    private void showOrder() {
        CashierRequestService.Request request = selectedRequest();
        if (request == null) return;
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(UITheme.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        root.setPreferredSize(new java.awt.Dimension(690, 520));

        JPanel info = cardPanel();
        info.setLayout(new GridLayout(0, 2, 14, 7));
        info.add(field("Mã yêu cầu", "TMP" + request.id));
        info.add(field("Trạng thái", requestStatus(request.status, request.paymentMethod)));
        info.add(field("Khách hàng", request.customerName));
        info.add(field("Loại đơn", "TAKEAWAY".equalsIgnoreCase(request.orderType) ? "Mang về" : "Tại bàn"));
        info.add(field("Điện thoại", blankAsDash(request.customerPhone)));
        info.add(field("Bàn", request.tableId == null ? "-" : String.format("%02d", request.tableId)));
        info.add(field("Phương thức", "CASH".equals(request.paymentMethod) ? "Tiền mặt" : "Chuyển khoản"));
        info.add(field("Tạo lúc", blankAsDash(request.createdAt)));
        if (request.rejectedReason != null && !request.rejectedReason.isBlank())
            info.add(field("Lý do từ chối", request.rejectedReason));
        if (request.refundStatus != null && !request.refundStatus.isBlank())
            info.add(field("Trạng thái hoàn tiền", refundStatusLabel(request.refundStatus)));

        JPanel details = cardPanel();
        details.setLayout(new BorderLayout(0, 8));
        JLabel section = new JLabel("Danh sách món");
        section.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 16));
        details.add(section, BorderLayout.NORTH);
        DefaultTableModel lines = new DefaultTableModel(new Object[]{"Món / tùy chọn", "SL", "Đơn giá", "Thành tiền"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        int count = 0;
        if (request.details != null) for (var detail : request.details) {
            count += detail.getQuantity();
            StringBuilder name = new StringBuilder(detail.getDrinkName() == null
                    ? "Đồ uống #" + detail.getDrinkId() : detail.getDrinkName());
            name.append(" · Size ").append(detail.getSize() == null ? "S" : detail.getSize());
            if (detail.getToppings() != null && !detail.getToppings().isEmpty()) {
                name.append(" · Topping: ").append(detail.getToppings().stream()
                        .map(topping -> topping.name + " (+" + String.format("%,.0f", topping.price) + "đ)")
                        .collect(java.util.stream.Collectors.joining(", ")));
            }
            if (detail.getNote() != null && !detail.getNote().isBlank()) name.append(" · Ghi chú: ").append(detail.getNote());
            lines.addRow(new Object[]{name.toString(), detail.getQuantity(), money(detail.getPrice()), money(detail.getSubtotal())});
        }
        JTable lineTable = new JTable(lines);
        UITheme.styleTable(lineTable);
        lineTable.setRowHeight(64);
        details.add(new JScrollPane(lineTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(UITheme.CARD);
        JLabel qty = new JLabel("Tổng số lượng món: " + count);
        qty.setForeground(UITheme.MUTED);
        JLabel total = new JLabel("TỔNG THANH TOÁN: " + money(request.total));
        total.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 20));
        total.setForeground(UITheme.SUCCESS);
        bottom.add(qty, BorderLayout.WEST);
        bottom.add(total, BorderLayout.EAST);
        root.add(info, BorderLayout.NORTH);
        root.add(details, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(this, root, "Chi tiết yêu cầu TMP" + request.id, JOptionPane.PLAIN_MESSAGE);
    }

    private JPanel cardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(UITheme.CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UITheme.BORDER),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        return panel;
    }

    private JPanel field(String label, String value) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel caption = new JLabel(label);
        caption.setForeground(UITheme.MUTED);
        JLabel content = new JLabel(value == null ? "-" : value);
        content.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        content.setForeground(UITheme.TEXT);
        panel.add(caption);
        panel.add(content);
        return panel;
    }

    private void confirmPayment() {
        CashierRequestService.Request request = selectedRequest();
        if (request == null) return;
        boolean processing = "PROCESSING".equals(request.status);
        String method = "CASH".equals(request.paymentMethod) ? "CASH" : "TRANSFER";
        String instruction = processing
                ? "Yêu cầu đang xử lý dở. Hệ thống sẽ tiếp tục cùng yêu cầu cũ, không tạo thêm đơn. Hãy bảo đảm tiền đã được nhận/kiểm tra."
                : method.equals("CASH")
                        ? "Chỉ xác nhận sau khi đã nhận đủ tiền mặt."
                        : "Chỉ xác nhận sau khi đã kiểm tra giao dịch thực tế vào tài khoản; không dựa vào ảnh chụp.";
        int choice = JOptionPane.showConfirmDialog(this, instruction,
                processing ? "Tiếp tục thanh toán" : "Xác nhận thanh toán",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;
        try {
            var order = service.confirm(request.id, method, user);
            JOptionPane.showMessageDialog(this, "Đã xác nhận thanh toán, tạo và duyệt đơn #" + order.getId()
                    + "; tồn kho đã trừ.");
            if (JOptionPane.showConfirmDialog(this, "In bill giao khách ngay?", "In hóa đơn",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                service.OrderService orderService = new service.OrderService();
                utils.ReceiptPrinter.printAsync(order, () -> orderService.getDetails(order.getId()),
                        exception -> JOptionPane.showMessageDialog(this,
                                "Không thể in hóa đơn: " + exception.getMessage(), "Lỗi",
                                JOptionPane.ERROR_MESSAGE), () -> {});
            }
            refreshData();
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Chưa hoàn tất yêu cầu: " + exception.getMessage()
                    + "\nHãy làm mới danh sách; nếu yêu cầu đang xử lý, dùng Tiếp tục để khôi phục cùng đơn.");
            refreshData();
        }
    }

    private void rejectPayment() {
        CashierRequestService.Request request = selectedRequest();
        if (request == null) return;
        boolean reconcile = "FAILED".equals(request.status)
                && (request.refundStatus == null || request.refundStatus.isBlank());
        if (!reconcile && !"WAITING_CASHIER".equals(request.status)
                && !"WAITING_CONFIRMATION".equals(request.status)) {
            JOptionPane.showMessageDialog(this, "Không thể từ chối yêu cầu đã bắt đầu xử lý.");
            return;
        }
        String reason = request.rejectedReason;
        if (!reconcile) {
            reason = JOptionPane.showInputDialog(this,
                    "Nhập lý do từ chối yêu cầu TMP" + request.id + ":", "Từ chối thanh toán",
                    JOptionPane.WARNING_MESSAGE);
            if (reason == null) return;
            if (reason.isBlank()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập lý do từ chối.");
                return;
            }
        }
        String intro = reconcile ? "Đối soát yêu cầu TMP" + request.id + ".\nLý do từ chối: "
                + blankAsDash(request.rejectedReason) : "Từ chối yêu cầu TMP" + request.id
                        + " với lý do: " + reason.trim();
        int fundsChoice = JOptionPane.showOptionDialog(this,
                intro + "\n\nĐã kiểm tra và xác nhận tiền thực tế đã nhận từ khách chưa?",
                reconcile ? "Đối soát giao dịch" : "Từ chối và đối soát tiền",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null,
                new Object[]{"Đã nhận tiền", "Chưa nhận tiền", "Hủy"}, "Chưa nhận tiền");
        if (fundsChoice < 0 || fundsChoice == 2) return;
        boolean moneyReceived = fundsChoice == 0;
        if (!moneyReceived) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Xác nhận chưa nhận tiền mặt/chuyển khoản? Hệ thống sẽ từ chối yêu cầu, không ghi giao dịch thu và không tạo đơn.",
                    "Xác nhận chưa nhận tiền", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
        } else {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Xác nhận tiền đã thực sự vào tài khoản/đã nhận tiền mặt? Yêu cầu sẽ chờ nhân viên hoàn tiền thủ công; chưa ghi nhận đã hoàn.",
                    "Xác nhận đã nhận tiền", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
        }
        try {
            if (reconcile) service.reconcileRejectedRequest(request.id, moneyReceived, user);
            else service.reject(request.id, reason, moneyReceived, user);
            JOptionPane.showMessageDialog(this, moneyReceived
                    ? "Đã ghi nhận khoản tiền nhận cho TMP" + request.id
                            + ". Hãy hoàn lại tiền thực tế cho khách rồi nhấn Xác nhận hoàn tiền để ghi lịch sử."
                    : "Đã từ chối TMP" + request.id + ". Ghi nhận chưa nhận được tiền, nên không phát sinh hoàn tiền.");
            refreshData();
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Không thể từ chối yêu cầu: " + exception.getMessage());
            refreshData();
        }
    }

    private void confirmRefund() {
        CashierRequestService.Request request = selectedRequest();
        if (request == null) return;
        if (!"REFUND_PENDING".equals(request.status)) return;
        int choice = JOptionPane.showConfirmDialog(this,
                "Chỉ xác nhận sau khi đã thực sự trả " + money(request.total) + " cho khách qua "
                        + ("CASH".equalsIgnoreCase(request.paymentMethod) ? "tiền mặt" : "chuyển khoản")
                        + ". Hệ thống sẽ ghi giao dịch REFUND_SUCCESS vào lịch sử thanh toán.",
                "Xác nhận đã hoàn tiền", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        try {
            service.confirmRefund(request.id, user);
            JOptionPane.showMessageDialog(this, "Đã ghi giao dịch hoàn tiền cho yêu cầu TMP" + request.id + ".");
            refreshData();
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Không thể ghi nhận hoàn tiền: " + exception.getMessage());
            refreshData();
        }
    }

    private String requestStatus(String status, String method) {
        if ("PROCESSING".equalsIgnoreCase(status)) return "Đang xử lý thanh toán";
        if ("REFUND_PENDING".equalsIgnoreCase(status)) return "Chờ hoàn tiền thủ công";
        if ("FAILED".equalsIgnoreCase(status)) return "Đã từ chối · cần đối soát tiền";
        return "CASH".equals(method) ? "Chờ nhận tiền mặt" : "Chờ xác nhận chuyển khoản";
    }

    private String refundStatusLabel(String status) {
        if ("PENDING".equalsIgnoreCase(status)) return "Chờ nhân viên hoàn tiền";
        if ("SUCCESS".equalsIgnoreCase(status)) return "Đã hoàn tiền";
        if ("NOT_RECEIVED".equalsIgnoreCase(status)) return "Đã đối soát: chưa nhận tiền";
        return status;
    }

    private String money(double amount) { return String.format("%,.0f đ", amount); }
    private String blankAsDash(String value) { return value == null || value.isBlank() ? "-" : value; }
}
