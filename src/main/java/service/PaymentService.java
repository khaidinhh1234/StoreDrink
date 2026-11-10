package service;

import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import model.Order;
import model.Payment;
import utils.ApiClient;

/** Reads and records payment history in the JSON Server payments collection. */
public class PaymentService {
    private final Gson gson = new Gson();
    private static final Object WRITE_LOCK = new Object();

    public List<Payment> getAll() throws Exception {
        Type type = new TypeToken<List<Payment>>() {}.getType();
        List<Payment> payments = gson.fromJson(ApiClient.get("/payments"), type);
        return payments == null ? List.of() : payments;
    }

    /** Idempotently creates or refreshes the success record for a completed order. */
    public Payment recordCompletedOrder(Order order) throws Exception {
        if (order == null || !"COMPLETED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Chỉ ghi nhận payment cho đơn đã hoàn thành.");
        }
        if (order.getCompletedAt() == null || order.getCompletedAt().isBlank()
                || order.getCompletedBy() == null
                || !("CASH".equalsIgnoreCase(order.getPaymentMethod())
                || "TRANSFER".equalsIgnoreCase(order.getPaymentMethod()))) {
            throw new IllegalArgumentException("Đơn thiếu thời gian, nhân viên hoặc phương thức thanh toán.");
        }

        synchronized (WRITE_LOCK) {
            List<Payment> current = getAll();
            Payment existing = current.stream()
                    .filter(payment -> payment.getOrderId() == order.getId())
                    .max(Comparator.comparingInt(Payment::getId))
                    .orElse(null);
            int id = existing == null
                    ? current.stream().mapToInt(Payment::getId).max().orElse(0) + 1
                    : existing.getId();
            Payment payment = new Payment(id, order.getId(), order.getCustomerName(),
                    order.getOrderType(), order.getCompletedBy(), order.getTotal(),
                    order.getPaymentMethod(), "SUCCESS", order.getCompletedAt());
            if (existing == null) {
                try {
                    ApiClient.post("/payments", gson.toJson(payment));
                } catch (Exception postFailure) {
                    // A server may persist POST and lose its response; verify before exposing failure.
                    try {
                        boolean persisted = getAll().stream()
                                .anyMatch(item -> item.getOrderId() == order.getId());
                        if (persisted) return payment;
                    } catch (Exception verifyFailure) {
                        postFailure.addSuppressed(verifyFailure);
                    }
                    throw postFailure;
                }
            } else if (!same(existing, payment)) {
                ApiClient.put("/payments/" + existing.getId(), gson.toJson(payment));
            }
            return payment;
        }
    }

    private boolean same(Payment a, Payment b) {
        return a.getOrderId() == b.getOrderId()
                && a.getEmployeeId() == b.getEmployeeId()
                && Double.compare(a.getAmount(), b.getAmount()) == 0
                && Objects.equals(a.getCustomerName(), b.getCustomerName())
                && Objects.equals(a.getOrderType(), b.getOrderType())
                && Objects.equals(a.getPaymentMethod(), b.getPaymentMethod())
                && Objects.equals(a.getPaidAt(), b.getPaidAt())
                && "SUCCESS".equalsIgnoreCase(a.getStatus());
    }
}
