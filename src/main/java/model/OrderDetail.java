package model;

public class OrderDetail {
    public static class ToppingSnapshot {
        public int toppingId; public String name; public double price;
        public ToppingSnapshot() {}
        public ToppingSnapshot(int id, String name, double price) { this.toppingId=id; this.name=name; this.price=price; }
    }
    private int id;
    private int orderId;
    private int drinkId;
    private int quantity;
    private double price;
    private double subtotal;
    private String drinkName, size="S", note;
    private double sizeExtraPrice, toppingTotal;
    private java.util.List<ToppingSnapshot> toppings = new java.util.ArrayList<>();

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
    public String getDrinkName(){return drinkName;} public void setDrinkName(String v){drinkName=v;}
    public String getSize(){return size;} public void setSize(String v){size=v;}
    public double getSizeExtraPrice(){return sizeExtraPrice;} public void setSizeExtraPrice(double v){sizeExtraPrice=v;}
    public java.util.List<ToppingSnapshot> getToppings(){return toppings;}
    public void setToppings(java.util.List<ToppingSnapshot> v){toppings=v==null?new java.util.ArrayList<>():new java.util.ArrayList<>(v);}
    public double getToppingTotal(){return toppingTotal;} public void setToppingTotal(double v){toppingTotal=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}

}
