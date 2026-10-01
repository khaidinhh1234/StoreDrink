package model;

public class OrderDetail {
    private int id;
    private int orderId;
    private int drinkId;
    private int quantity;
    private double price;
    private double subtotal;

    public OrderDetail() {
    }

    public OrderDetail(int id, int orderId, int drinkId, int quantity, double price) {
        this.id = id;
        this.orderId = orderId;
        this.drinkId = drinkId;
        this.quantity = quantity;
        this.price = price;
        this.subtotal = quantity * price;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getOrderId() { return orderId; }
    public int getDrinkId() { return drinkId; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public double getSubtotal() { return subtotal; }
    public void setOrderId(int orderId) { this.orderId = orderId; }
    public void setDrinkId(int drinkId) { this.drinkId = drinkId; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setPrice(double price) { this.price = price; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

}
