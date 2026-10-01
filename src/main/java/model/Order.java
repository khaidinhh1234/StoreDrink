package model;

public class Order {
    private int id;
    private int userId;
    private int createdByUserId;
    private Integer tableId;
    private String customerName;
    private String customerPhone;
    private String orderType;
    private double total;
    private String paymentMethod;
    private String status;
    private String createdAt;

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
    public void setTotal(double total) { this.total = total; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() { return userId; }
    public int getCreatedByUserId() { return createdByUserId; }
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
    public void setStatus(String status) { this.status = status; }

}
