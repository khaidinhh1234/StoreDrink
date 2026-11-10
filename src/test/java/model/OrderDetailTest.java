package model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OrderDetailTest {
    @Test
    void constructorCalculatesSubtotalFromQuantityAndPrice() {
        OrderDetail detail = new OrderDetail(1, 2, 3, 4, 12500.0);

        assertEquals(1, detail.getId());
        assertEquals(2, detail.getOrderId());
        assertEquals(3, detail.getDrinkId());
        assertEquals(4, detail.getQuantity());
        assertEquals(12500.0, detail.getPrice());
        assertEquals(50000.0, detail.getSubtotal());
    }
}
