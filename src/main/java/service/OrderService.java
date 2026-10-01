package service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import model.Order;
import model.OrderDetail;
import utils.ApiClient;

import java.time.LocalDateTime;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

public class OrderService {
    private final Gson gson = new Gson();

    public List<Order> getAll() throws Exception {
        String json = ApiClient.get("/orders");
        Type type = new TypeToken<List<Order>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void updateStatus(Order order, String status) throws Exception {
        if ("COMPLETED".equalsIgnoreCase(status)
                && !"CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("Đơn hàng phải được xác nhận trước khi thanh toán");
        }
        order.setStatus(status);
        ApiClient.put("/orders/" + order.getId(), gson.toJson(order));
    }

    public void completePayment(Order order, String paymentMethod) throws Exception {
        if (!"CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("Đơn hàng phải được xác nhận trước khi thanh toán");
        }
        if (!"CASH".equalsIgnoreCase(paymentMethod)
                && !"TRANSFER".equalsIgnoreCase(paymentMethod)) {
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ");
        }
        order.setPaymentMethod(paymentMethod);
        order.setStatus("COMPLETED");
        ApiClient.put("/orders/" + order.getId(), gson.toJson(order));
    }

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
        ApiClient.post("/orders", gson.toJson(order));

        int detailId = nextDetailId();
        for (OrderDetail detail : details) {
            detail.setId(detailId++);
            detail.setOrderId(orderId);
            detail.setSubtotal(detail.getQuantity() * detail.getPrice());
            ApiClient.post("/orderDetails", gson.toJson(detail));
        }
        return order;
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
