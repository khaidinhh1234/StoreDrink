package model;

/** A successful payment record stored independently from its order. */
public class Payment {
    private int id;
    private int orderId;
    private String customerName;
    private String orderType;
    private int employeeId;
    private double amount;
    private String paymentMethod;
    private String status;
    private String paidAt;

    public Payment() {}

    public Payment(int id, int orderId, String customerName, String orderType,
            int employeeId, double amount, String paymentMethod, String status, String paidAt) {
        this.id = id;
        this.orderId = orderId;
        this.customerName = customerName;
        this.orderType = orderType;
        this.employeeId = employeeId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.paidAt = paidAt;
    }

    public int getId() { return id; }
    public int getOrderId() { return orderId; }
    public String getCustomerName() { return customerName; }
    public String getOrderType() { return orderType; }
    public int getEmployeeId() { return employeeId; }
    public double getAmount() { return amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getStatus() { return status; }
    public String getPaidAt() { return paidAt; }
}
