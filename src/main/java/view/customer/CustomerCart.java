package view.customer;

import model.Drink;
import java.util.ArrayList;
import java.util.List;

final class CustomerCart {
    private static final List<Item> items = new ArrayList<>();

    private CustomerCart() {}

    static List<Item> items() {
        return items;
    }

    static void add(Drink drink) {
        for (Item item : items) {
            if (item.drink().getId() == drink.getId()) {
                if (item.quantity() < drink.getQuantity()) item.setQuantity(item.quantity() + 1);
                return;
            }
        }
        items.add(new Item(drink, 1));
    }

    static double total() {
        return items.stream().mapToDouble(Item::subtotal).sum();
    }

    static final class Item {
        private final Drink drink;
        private int quantity;
        Item(Drink drink, int quantity) { this.drink = drink; this.quantity = quantity; }
        Drink drink() { return drink; }
        int quantity() { return quantity; }
        void setQuantity(int quantity) { this.quantity = quantity; }
        double subtotal() { return drink.getPrice() * quantity; }
    }
}
