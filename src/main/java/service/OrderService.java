package service;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import model.Drink;
import model.Order;
import model.OrderDetail;
import utils.ApiClient;

public class OrderService {
    private final Gson gson = new Gson();

    public List<Order> getAll() throws Exception {
        String json = ApiClient.get("/orders");
        Type type = new TypeToken<List<Order>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void updateStatus(Order order, String status) throws Exception {
        String previousStatus = normalizeStatus(order.getStatus());
        String nextStatus = normalizeStatus(status);
        Boolean previousStockDeducted = order.getStockDeducted();
        List<StockChange> stockChanges = List.of();

        if ("CONFIRMED".equals(nextStatus)) {
            if (!"PENDING".equals(previousStatus)) {
                throw new IllegalStateException("Chỉ đơn đang chờ duyệt mới được xác nhận");
            }
            stockChanges = applyOrderStock(order.getId(), -1);
            order.setStockDeducted(true);
        } else if ("CANCELLED".equals(nextStatus)) {
            if ("PENDING".equals(previousStatus)) {
                // Pending orders have not reserved or deducted inventory.
            } else if ("CONFIRMED".equals(previousStatus)) {
                if (Boolean.TRUE.equals(previousStockDeducted)) {
                    stockChanges = applyOrderStock(order.getId(), 1);
                }
                order.setStockDeducted(false);
            } else {
                throw new IllegalStateException("Chỉ đơn chờ duyệt hoặc đã duyệt mới được hủy");
            }
        } else {
            throw new IllegalArgumentException("Trạng thái chuyển đổi không hợp lệ: " + status);
        }

        order.setStatus(nextStatus);
        try {
            ApiClient.put("/orders/" + order.getId(), gson.toJson(order));
        } catch (Exception exception) {
            order.setStatus(previousStatus);
            order.setStockDeducted(previousStockDeducted);
            rollbackStock(stockChanges, exception);
            throw exception;
        }
    }

    public void completePayment(Order order, String paymentMethod) throws Exception {
        if (!"CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("Đơn hàng phải được xác nhận trước khi thanh toán");
        }
        if (!"CASH".equalsIgnoreCase(paymentMethod)
                && !"TRANSFER".equalsIgnoreCase(paymentMethod)) {
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ");
        }
        String previousPaymentMethod = order.getPaymentMethod();
        order.setPaymentMethod(paymentMethod);
        order.setStatus("COMPLETED");
        try {
            // Inventory was deducted at confirmation; completing payment never deducts it again.
            ApiClient.put("/orders/" + order.getId(), gson.toJson(order));
        } catch (Exception exception) {
            order.setPaymentMethod(previousPaymentMethod);
            order.setStatus("CONFIRMED");
            throw exception;
        }
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** direction -1 deducts stock; +1 returns stock for a cancelled confirmed order. */
    private List<StockChange> applyOrderStock(int orderId, int direction) throws Exception {
        Map<Integer, Integer> quantitiesByDrink = new HashMap<>();
        for (OrderDetail detail : getDetails(orderId)) {
            if (detail.getQuantity() <= 0) {
                throw new IllegalStateException("Số lượng món trong order không hợp lệ");
            }
            quantitiesByDrink.merge(detail.getDrinkId(), detail.getQuantity(), Integer::sum);
        }
        if (quantitiesByDrink.isEmpty()) {
            throw new IllegalStateException("Order không có món để xử lý kho");
        }

        Map<Integer, Drink> drinksById = new HashMap<>();
        for (Drink drink : new DrinkService().getAll()) {
            drinksById.put(drink.getId(), drink);
        }

        List<StockChange> changes = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : quantitiesByDrink.entrySet()) {
            Drink drink = drinksById.get(entry.getKey());
            if (drink == null) {
                throw new IllegalStateException("Không tìm thấy đồ uống có mã " + entry.getKey());
            }
            int newQuantity = drink.getQuantity() + direction * entry.getValue();
            if (newQuantity < 0) {
                throw new IllegalStateException(drink.getName() + " chỉ còn " + drink.getQuantity()
                        + " cốc trong kho, không đủ cho order này");
            }
            changes.add(new StockChange(drink, drink.getQuantity(), newQuantity));
        }

        List<StockChange> applied = new ArrayList<>();
        try {
            for (StockChange change : changes) {
                change.drink.setQuantity(change.newQuantity);
                new DrinkService().update(change.drink);
                applied.add(change);
            }
        } catch (Exception exception) {
            rollbackStock(applied, exception);
            throw exception;
        }
        return applied;
    }

    private void rollbackStock(List<StockChange> changes, Exception originalException) {
        for (int index = changes.size() - 1; index >= 0; index--) {
            StockChange change = changes.get(index);
            try {
                change.drink.setQuantity(change.previousQuantity);
                new DrinkService().update(change.drink);
            } catch (Exception rollbackException) {
                originalException.addSuppressed(rollbackException);
            }
        }
    }

    private record StockChange(Drink drink, int previousQuantity, int newQuantity) { }

    public Order createOrder(int userId, double total, String paymentMethod,
            List<OrderDetail> details) throws Exception {
        return createOrder(userId, null, null, null, total, paymentMethod, "DINE_IN", details);
    }

    public Order createOrder(int createdByUserId, Integer tableId, String customerName,
            String customerPhone, double total, String paymentMethod, String orderType,
            List<OrderDetail> details) throws Exception {
        int orderId = nextId(getAll(), 1000);
        Order order = new Order(orderId, tableId, customerName, customerPhone, total,
                paymentMethod, "PENDING", orderType, LocalDateTime.now().withNano(0).toString(),
                createdByUserId);
        order.setUserId(createdByUserId);
        postAndConfirm("/orders", gson.toJson(order), "/orders/" + orderId);

        int detailId = nextDetailId();
        for (OrderDetail detail : details) {
            detail.setId(detailId++);
            detail.setOrderId(orderId);
            detail.setSubtotal(detail.getQuantity() * detail.getPrice());
            postAndConfirm("/orderDetails", gson.toJson(detail),
                    "/orderDetails/" + detail.getId());
        }
        return order;
    }

    private void postAndConfirm(String endpoint, String body, String resourceEndpoint) throws Exception {
        try {
            ApiClient.post(endpoint, body);
        } catch (Exception postException) {
            try {
                ApiClient.get(resourceEndpoint);
            } catch (Exception verificationException) {
                postException.addSuppressed(verificationException);
                throw postException;
            }
        }
    }

    private int nextDetailId() throws Exception {
        String json = ApiClient.get("/orderDetails");
        Type type = new TypeToken<List<OrderDetail>>() {}.getType();
        List<OrderDetail> details = gson.fromJson(json, type);
        return nextId(details, 0);
    }

    private int nextId(List<?> items, int minimum) {
        int max = minimum;
        for (Object item : items) {
            int id = item instanceof Order
                    ? ((Order) item).getId()
                    : ((OrderDetail) item).getId();
            max = Math.max(max, id);
        }
        return max + 1;
    }

    public List<Order> getForUser(int userId) throws Exception {
        String json = ApiClient.get("/orders?userId=" + userId);
        Type type = new TypeToken<List<Order>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public List<OrderDetail> getDetails(int orderId) throws Exception {
        Type type = new TypeToken<List<OrderDetail>>() {}.getType();
        List<OrderDetail> details = gson.fromJson(ApiClient.get("/orderDetails?orderId=" + orderId), type);
        return details == null ? new java.util.ArrayList<>() : details;
    }

    public void updateDetail(OrderDetail detail) throws Exception {
        detail.setSubtotal(detail.getQuantity() * detail.getPrice());
        ApiClient.put("/orderDetails/" + detail.getId(), gson.toJson(detail));
    }

    public void addDetail(OrderDetail detail) throws Exception {
        detail.setId(nextDetailId());
        detail.setSubtotal(detail.getQuantity() * detail.getPrice());
        ApiClient.post("/orderDetails", gson.toJson(detail));
    }

    public void deleteDetail(OrderDetail detail) throws Exception {
        ApiClient.delete("/orderDetails/" + detail.getId());
    }

    public void updateOrder(Order order) throws Exception {
        ApiClient.put("/orders/" + order.getId(), gson.toJson(order));
    }

    public List<String> getStatuses() {
        return Arrays.asList("PENDING", "CONFIRMED", "CANCELLED");
    }
}
