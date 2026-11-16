package model;

/** A successful payment record stored independently from its order. */
public class Payment {
    private int id;
    private int orderId;
    private Integer checkoutRequestId;
    private String customerName;
    private String orderType;
    private int employeeId;
    private double amount;
    private String paymentMethod;
    private String status;
    private String paidAt;
    private String refundStatus;
    private String refundType;
    private Integer refundedByUserId;
    private String refundedAt;
    private String refundReason;

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
    public Integer getCheckoutRequestId() { return checkoutRequestId; }
    public String getCustomerName() { return customerName; }
    public String getOrderType() { return orderType; }
    public int getEmployeeId() { return employeeId; }
    public double getAmount() { return amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getStatus() { return status; }
    public String getPaidAt() { return paidAt; }
    public String getRefundStatus(){return refundStatus;} public Integer getRefundedByUserId(){return refundedByUserId;}
    public void setCheckoutRequestId(Integer v){checkoutRequestId=v;}
    public String getRefundType(){return refundType;}
    public String getRefundedAt(){return refundedAt;} public String getRefundReason(){return refundReason;}
    public void setRefundStatus(String v){refundStatus=v;} public void setRefundedByUserId(Integer v){refundedByUserId=v;}
    public void setRefundType(String v){refundType=v;}
    public void setRefundedAt(String v){refundedAt=v;} public void setRefundReason(String v){refundReason=v;}
}
