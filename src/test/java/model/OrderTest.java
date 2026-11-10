package model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OrderTest {
    @Test
    void reservedStockIsCopiedWhenSetAndRead() {
        Order order = new Order();
        Map<Integer, Integer> stock = new HashMap<>(Map.of(7, 3));

        order.setReservedStock(stock);
        stock.put(7, 9);

        Map<Integer, Integer> reservedStock = order.getReservedStock();
        assertEquals(3, reservedStock.get(7));

        reservedStock.put(7, 12);
        assertEquals(3, order.getReservedStock().get(7));
    }
}
