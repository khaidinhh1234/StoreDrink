package view.customer;

import model.Drink;
import model.OrderDetail;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class CustomerCart {
    private static final List<Item> items = new ArrayList<>();

    private CustomerCart() {}

    static List<Item> items() {
        return items;
    }

    static boolean add(Item incoming) {
        Item same = items.stream().filter(item -> item.sameConfiguration(incoming)).findFirst().orElse(null);
        int existingQuantity = items.stream().filter(item -> item.drink().getId() == incoming.drink().getId())
                .mapToInt(Item::quantity).sum();
        if (existingQuantity + incoming.quantity() > incoming.drink().getQuantity()) return false;
        if (same == null) items.add(incoming);
        else same.setQuantity(same.quantity() + incoming.quantity());
        return true;
    }

    static double total() {
        return items.stream().mapToDouble(Item::subtotal).sum();
    }

    static final class Item {
        private final Drink drink;
        private int quantity;
        String size = "S", note = "";
        double sizeExtraPrice;
        final List<OrderDetail.ToppingSnapshot> toppings = new ArrayList<>();
        Item(Drink drink, int quantity) { this.drink = drink; this.quantity = quantity; }
        Drink drink() { return drink; }
        int quantity() { return quantity; }
        void setQuantity(int quantity) { this.quantity = quantity; }
        double unitPrice() { return drink.getPrice() + sizeExtraPrice + toppings.stream().mapToDouble(t -> t.price).sum(); }
        double subtotal() { return unitPrice() * quantity; }
        String configurationLabel() {
            String toppingNames = toppings.stream().map(t -> t.name).collect(Collectors.joining(", "));
            return drink.getName() + " / " + size
                    + (toppingNames.isBlank() ? "" : " / " + toppingNames)
                    + (note.isBlank() ? "" : " / " + note);
        }
        boolean sameConfiguration(Item other) {
            if (drink.getId() != other.drink.getId() || !size.equals(other.size)
                    || !note.trim().equals(other.note.trim()) || toppings.size() != other.toppings.size()) return false;
            Map<Integer, Long> first = toppings.stream().collect(Collectors.groupingBy(t -> t.toppingId, Collectors.counting()));
            Map<Integer, Long> second = other.toppings.stream().collect(Collectors.groupingBy(t -> t.toppingId, Collectors.counting()));
            return first.equals(second);
        }
    }
}
