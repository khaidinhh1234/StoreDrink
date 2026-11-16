package model;

public class Topping {
    private int id;
    private String name;
    private double price;
    private boolean active;

    public Topping() {}
    public Topping(String name, double price) { this.name = name; this.price = price; this.active = true; }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
