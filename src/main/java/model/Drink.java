package model;

import java.util.ArrayList;
import java.util.List;

public class Drink {

    public static class SizeOption {
        private String name;
        private double price;

        public SizeOption() {}
        public SizeOption(String name, double price) { this.name = name; this.price = price; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        @Override public String toString() { return name + " · " + String.format("%,.0f đ", price); }
    }

    private int id;
    private String name;
    private double price;
    private int quantity;
    private boolean active;
    private int categoryId;
    private String image;
    private boolean hasLargeSize;
    private double largeExtraPrice;
    private String description;
    private List<SizeOption> sizes = new ArrayList<>();

    public Drink() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean isActive() {
        return active;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public String getImage() {
        return image;
    }
    public boolean isHasLargeSize() { return hasLargeSize; }
    public double getLargeExtraPrice() { return largeExtraPrice; }
    public void setHasLargeSize(boolean value) { hasLargeSize = value; }
    public void setLargeExtraPrice(double value) { largeExtraPrice = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public List<SizeOption> getSizes() { return sizes == null ? List.of() : List.copyOf(sizes); }
    public void setSizes(List<SizeOption> value) { sizes = value == null ? new ArrayList<>() : new ArrayList<>(value); }

    /** Returns configured sizes, mapping legacy Size S/L fields for existing JSON records. */
    public List<SizeOption> getAvailableSizes() {
        if (sizes != null && !sizes.isEmpty()) {
            List<SizeOption> available = new ArrayList<>();
            for (SizeOption option : sizes) {
                if (option == null || option.getName() == null) continue;
                // Persisted non-default size prices are surcharges; customer-facing options
                // expose the final unit price so all existing ordering screens stay consistent.
                double finalPrice = "S".equalsIgnoreCase(option.getName())
                        ? price : price + option.getPrice();
                available.add(new SizeOption(option.getName(), finalPrice));
            }
            return List.copyOf(available);
        }
        List<SizeOption> available = new ArrayList<>();
        available.add(new SizeOption("S", price));
        if (hasLargeSize) available.add(new SizeOption("L", price + largeExtraPrice));
        return List.copyOf(available);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
