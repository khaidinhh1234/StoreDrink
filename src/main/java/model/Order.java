package model;

import java.util.HashMap;
import java.util.Map;

public class Order {
    private int id;
    private int userId;
    private int createdByUserId;
    private int processedByUserId;
    private Integer tableId;
    private String customerName;
    private String customerPhone;
    private String orderType;
    private double total;
    private String paymentMethod;
    private String status;
    private String createdAt;
    private Integer confirmedBy;
    private String confirmedAt;
    private Integer completedBy;
    private String completedAt;
    private String cancelledBy;
    private String cancelledAt;
    private String cancelReason;
    // null marks older orders created before inventory tracking was enabled.
    private Boolean stockDeducted;
    private Boolean stockRestored;
    private Map<Integer, Integer> reservedStock;

    public Order() {
    }

    public Order(int id, int userId, int createdByUserId, double total, String paymentMethod, String status,
            String createdAt) {
        this.id = id;
        this.userId = userId;
        this.createdByUserId = createdByUserId;
        this.total = total;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Order(int id, Integer tableId, String customerName, String customerPhone, double total,
            String paymentMethod, String status, String orderType, String createdAt,
            int createdByUserId) {
        this.id = id;
        this.tableId = tableId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.total = total;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.orderType = orderType;
        this.createdAt = createdAt;
        this.createdByUserId = createdByUserId;
    }

    public int getId() {
        return id;
    }

    public void setUserId(int userId) { this.userId = userId; }
    public void setCreatedByUserId(int createdByUserId) { this.createdByUserId = createdByUserId; }
    public void setProcessedByUserId(int processedByUserId) { this.processedByUserId = processedByUserId; }
    public void setTotal(double total) { this.total = total; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }
    public void setConfirmedBy(Integer confirmedBy) { this.confirmedBy = confirmedBy; }
    public void setConfirmedAt(String confirmedAt) { this.confirmedAt = confirmedAt; }
    public void setCompletedBy(Integer completedBy) { this.completedBy = completedBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    public void setCancelledAt(String cancelledAt) { this.cancelledAt = cancelledAt; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public void setStockDeducted(Boolean stockDeducted) { this.stockDeducted = stockDeducted; }
    public void setStockRestored(Boolean stockRestored) { this.stockRestored = stockRestored; }
    public void setReservedStock(Map<Integer, Integer> reservedStock) {
        this.reservedStock = reservedStock == null ? null : new HashMap<>(reservedStock);
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() { return userId; }
    public int getCreatedByUserId() { return createdByUserId; }
    public int getProcessedByUserId() { return processedByUserId; }
    public Boolean getStockDeducted() { return stockDeducted; }
    public Boolean getStockRestored() { return stockRestored; }
    public Map<Integer, Integer> getReservedStock() {
        return reservedStock == null ? null : new HashMap<>(reservedStock);
    }
    public Integer getTableId() { return tableId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public String getOrderType() { return orderType; }
    public void setTableId(Integer tableId) { this.tableId = tableId; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public double getTotal() { return total; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public Integer getConfirmedBy() { return confirmedBy; }
    public String getConfirmedAt() { return confirmedAt; }
    public Integer getCompletedBy() { return completedBy; }
    public String getCompletedAt() { return completedAt; }
    public String getCancelledBy() { return cancelledBy; }
    public String getCancelledAt() { return cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public void setStatus(String status) { this.status = status; }

}
