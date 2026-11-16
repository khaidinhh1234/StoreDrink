package service;

import java.lang.reflect.Type;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import model.Order;
import model.Payment;
import model.User;
import service.CashierRequestService.Request;
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

    public Payment recordRefund(int orderId, User actor, String reason) throws Exception {
        if (orderId <= 0 || actor == null || actor.getId() <= 0
                || !"STAFF".equalsIgnoreCase(actor.getRole()) || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cần nhân viên và lý do hợp lệ để ghi nhận hoàn tiền.");
        }
        synchronized (WRITE_LOCK) {
            List<Payment> all = getAll();
            List<Payment> orderPayments = all.stream().filter(p -> p.getOrderId() == orderId).toList();
            Payment existingRefund = orderPayments.stream()
                    .filter(PaymentService::isFullOrderRefund)
                    .findFirst().orElse(null);
            if (existingRefund != null) return existingRefund;

            Payment original = orderPayments.stream()
                    .filter(p -> "SUCCESS".equalsIgnoreCase(p.getStatus()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Đơn chưa có thanh toán thành công."));
            if ("SUCCESS".equalsIgnoreCase(original.getRefundStatus())) {
                return original;
            }

            double refundable = orderPayments.stream().mapToDouble(payment -> {
                String status = payment.getStatus() == null ? "" : payment.getStatus().toUpperCase(java.util.Locale.ROOT);
                if ("SUCCESS".equals(status) || "ADDITIONAL_PAYMENT_SUCCESS".equals(status)) return payment.getAmount();
                if ("REFUND_SUCCESS".equals(status)) return -payment.getAmount();
                return 0;
            }).sum();
            if (!Double.isFinite(refundable) || refundable <= 0) {
                throw new IllegalStateException("Không có số tiền thực thu hợp lệ để hoàn.");
            }

            String refundedAt = java.time.LocalDateTime.now().toString();
            int id = all.stream().mapToInt(Payment::getId).max().orElse(0) + 1;
            Payment refund = new Payment(id, orderId, original.getCustomerName(), original.getOrderType(),
                    actor.getId(), refundable, original.getPaymentMethod(), "REFUND_SUCCESS", refundedAt);
            refund.setRefundType("FULL_ORDER");
            refund.setRefundStatus("SUCCESS");
            refund.setRefundedByUserId(actor.getId());
            refund.setRefundedAt(refundedAt);
            refund.setRefundReason(reason.trim());
            postPaymentAndConfirm(refund, "REFUND_SUCCESS");
            return refund;
        }
    }

    public boolean hasFullOrderRefund(int orderId) throws Exception {
        List<Payment> orderPayments = getAll().stream().filter(payment -> payment.getOrderId() == orderId).toList();
        return orderPayments.stream().anyMatch(PaymentService::isFullOrderRefund)
                || orderPayments.stream().anyMatch(payment -> "SUCCESS".equalsIgnoreCase(payment.getStatus())
                        && "SUCCESS".equalsIgnoreCase(payment.getRefundStatus()));
    }

    private static boolean isFullOrderRefund(Payment payment) {
        return "REFUND_SUCCESS".equalsIgnoreCase(payment.getStatus())
                && "FULL_ORDER".equalsIgnoreCase(payment.getRefundType());
    }

    /** Records money physically received for a rejected temporary checkout request. */
    public Payment recordCheckoutRequestPayment(Request request, User actor) throws Exception {
        if (request == null || request.id <= 0 || !Double.isFinite(request.total) || request.total <= 0
                || actor == null || actor.getId() <= 0 || !"STAFF".equalsIgnoreCase(actor.getRole())
                || !("CASH".equalsIgnoreCase(request.paymentMethod) || "TRANSFER".equalsIgnoreCase(request.paymentMethod))) {
            throw new IllegalArgumentException("Thông tin giao dịch của yêu cầu thanh toán không hợp lệ.");
        }
        synchronized (WRITE_LOCK) {
            List<Payment> all = getAll();
            Payment existing = all.stream().filter(payment -> payment.getCheckoutRequestId() != null
                    && payment.getCheckoutRequestId() == request.id
                    && "SUCCESS".equalsIgnoreCase(payment.getStatus())).findFirst().orElse(null);
            if (existing != null) {
                if (Math.abs(existing.getAmount() - request.total) > 0.01
                        || !request.paymentMethod.equalsIgnoreCase(existing.getPaymentMethod())) {
                    throw new IllegalStateException("Giao dịch nhận tiền đã lưu không khớp với yêu cầu.");
                }
                return existing;
            }
            int id = all.stream().mapToInt(Payment::getId).max().orElse(0) + 1;
            Payment payment = new Payment(id, 0, request.customerName, request.orderType,
                    actor.getId(), request.total, request.paymentMethod, "SUCCESS",
                    java.time.LocalDateTime.now().toString());
            payment.setCheckoutRequestId(request.id);
            postPaymentAndConfirm(payment, "SUCCESS");
            return payment;
        }
    }

    /** Records a manual refund after staff has actually returned money to the customer. */
    public Payment recordCheckoutRequestRefund(Request request, User actor, String reason) throws Exception {
        if (request == null || request.id <= 0 || actor == null || actor.getId() <= 0
                || !"STAFF".equalsIgnoreCase(actor.getRole()) || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cần nhân viên và lý do hợp lệ để ghi nhận hoàn tiền.");
        }
        synchronized (WRITE_LOCK) {
            List<Payment> all = getAll();
            Payment existingRefund = all.stream().filter(payment -> payment.getCheckoutRequestId() != null
                    && payment.getCheckoutRequestId() == request.id
                    && "REFUND_SUCCESS".equalsIgnoreCase(payment.getStatus()))
                    .findFirst().orElse(null);
            if (existingRefund != null) return existingRefund;
            Payment received = all.stream().filter(payment -> payment.getCheckoutRequestId() != null
                    && payment.getCheckoutRequestId() == request.id
                    && "SUCCESS".equalsIgnoreCase(payment.getStatus())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Chưa ghi nhận tiền đã nhận cho yêu cầu này."));
            int id = all.stream().mapToInt(Payment::getId).max().orElse(0) + 1;
            String refundedAt = java.time.LocalDateTime.now().toString();
            Payment refund = new Payment(id, 0, request.customerName, request.orderType,
                    actor.getId(), received.getAmount(), received.getPaymentMethod(), "REFUND_SUCCESS", refundedAt);
            refund.setCheckoutRequestId(request.id);
            refund.setRefundType("CHECKOUT_REQUEST");
            refund.setRefundStatus("SUCCESS");
            refund.setRefundedByUserId(actor.getId());
            refund.setRefundedAt(refundedAt);
            refund.setRefundReason(reason.trim());
            postPaymentAndConfirm(refund, "REFUND_SUCCESS");
            return refund;
        }
    }

    public Payment recordOrderAdjustment(Order order, User actor, double delta, boolean refund, String reason) throws Exception {
        if (!Double.isFinite(delta) || Math.abs(delta) < 0.0001) {
            throw new IllegalArgumentException("Khoản chênh lệch thanh toán không hợp lệ.");
        }
        if (actor == null || actor.getId() <= 0 || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cần ghi nhận nhân viên và lý do xử lý chênh lệch.");
        }
        synchronized (WRITE_LOCK) {
            List<Payment> all = getAll();
            Payment base = all.stream()
                    .filter(payment -> payment.getOrderId() == order.getId()
                            && "SUCCESS".equalsIgnoreCase(payment.getStatus()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy thanh toán gốc cho đơn."));
            int id = all.stream().mapToInt(Payment::getId).max().orElse(0) + 1;
            String paidAt = java.time.LocalDateTime.now().toString();
            Payment adjustment = new Payment(id, order.getId(), order.getCustomerName(), order.getOrderType(),
                    actor.getId(), Math.abs(delta), base.getPaymentMethod(),
                    refund ? "REFUND_SUCCESS" : "ADDITIONAL_PAYMENT_SUCCESS", paidAt);
            if (refund) {
                adjustment.setRefundType("ADJUSTMENT");
                adjustment.setRefundStatus("SUCCESS");
                adjustment.setRefundedByUserId(actor.getId());
                adjustment.setRefundedAt(paidAt);
                adjustment.setRefundReason(reason.trim());
            }
            postPaymentAndConfirm(adjustment, refund ? "REFUND_SUCCESS" : "ADDITIONAL_PAYMENT_SUCCESS");
            return adjustment;
        }
    }

    /** Records successful cashier receipt at the moment the official order is created. */
    public Payment recordCashierPayment(Order order, User cashier, String method) throws Exception {
        if (order == null || order.getId() <= 0 || cashier == null || cashier.getId() <= 0
                || !"STAFF".equalsIgnoreCase(cashier.getRole())
                || !("CASH".equalsIgnoreCase(method) || "TRANSFER".equalsIgnoreCase(method))) {
            throw new IllegalArgumentException("Thông tin thanh toán tại quầy không hợp lệ.");
        }
        synchronized (WRITE_LOCK) {
            List<Payment> all = getAll();
            Payment existing = all.stream().filter(p -> p.getOrderId() == order.getId()
                    && "SUCCESS".equalsIgnoreCase(p.getStatus())).findFirst().orElse(null);
            if (existing != null) {
                if (Math.abs(existing.getAmount() - order.getTotal()) > 0.01
                        || !method.equalsIgnoreCase(existing.getPaymentMethod())) {
                    throw new IllegalStateException("Giao dịch đã lưu không khớp số tiền hoặc phương thức của đơn.");
                }
                return existing;
            }
            if (all.stream().anyMatch(p -> p.getOrderId() == order.getId())) {
                throw new IllegalStateException("Đơn đã có giao dịch khác, cần kiểm tra lịch sử trước khi thu tiền.");
            }
            int id = all.stream().mapToInt(Payment::getId).max().orElse(0) + 1;
            Payment payment = new Payment(id, order.getId(), order.getCustomerName(), order.getOrderType(),
                    cashier.getId(), order.getTotal(), method, "SUCCESS", java.time.LocalDateTime.now().toString());
            postPaymentAndConfirm(payment, "SUCCESS");
            return payment;
        }
    }

    private void postPaymentAndConfirm(Payment payment, String expectedStatus) throws Exception {
        try {
            ApiClient.post("/payments", gson.toJson(payment));
        } catch (Exception writeFailure) {
            try {
                Payment persisted = getAll().stream().filter(candidate -> candidate.getId() == payment.getId()
                        && candidate.getOrderId() == payment.getOrderId()
                        && java.util.Objects.equals(candidate.getCheckoutRequestId(), payment.getCheckoutRequestId())
                        && Math.abs(candidate.getAmount() - payment.getAmount()) < 0.01
                        && expectedStatus.equalsIgnoreCase(candidate.getStatus()))
                        .findFirst().orElse(null);
                if (persisted != null) return;
            } catch (Exception verifyFailure) {
                writeFailure.addSuppressed(verifyFailure);
            }
            throw writeFailure;
        }
    }

}
