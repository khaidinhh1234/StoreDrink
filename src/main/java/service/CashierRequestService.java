package service;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import model.Order;
import model.OrderDetail;
import model.Payment;
import model.User;
import utils.ApiClient;

/** Temporary checkout requests. They are separate from official orders until cashier confirmation. */
public class CashierRequestService {
    private static final Object REQUEST_LOCK = new Object();
    private final Gson gson = new Gson();

    public static class Request {
        public int id;
        public String customerName, customerPhone, orderType, status, createdAt, paymentMethod, paidAt, creationToken;
        public boolean inventoryReserved;
        public String processingToken, paymentPendingStatus, rejectedAt, rejectedReason, refundStatus;
        public String refundReceivedAt, refundConfirmedAt, refundCheckAt;
        public Integer tableId, receivedByUserId, orderId, customerUserId, processingByUserId, rejectedByUserId;
        public Integer refundReceivedByUserId, refundConfirmedByUserId, refundCheckedByUserId;
        public double total;
        public List<OrderDetail> details;
        public Request() {}
        public Request(String name, String phone, Integer table, double amount, String type,
                List<OrderDetail> lines, String state, String created, String method) {
            customerName=name; customerPhone=phone; tableId=table; total=amount;
            orderType=type; details=lines; status=state; createdAt=created; paymentMethod=method;
        }
    }

    public List<Request> getAll() throws Exception {
        Type type = new TypeToken<List<Request>>(){}.getType();
        List<Request> result = gson.fromJson(ApiClient.get("/checkoutRequests"), type);
        return result == null ? List.of() : result.stream().filter(java.util.Objects::nonNull).toList();
    }

    public int create(String json) throws Exception {
        Request request = gson.fromJson(json, Request.class);
        if (request == null) throw new IllegalArgumentException("Thông tin yêu cầu đặt món không hợp lệ.");
        if (request.creationToken == null || request.creationToken.isBlank())
            request.creationToken = UUID.randomUUID().toString();
        InventoryService inventory = new InventoryService();
        return inventory.withLock(() -> {
            Request existing = getAll().stream()
                    .filter(row -> request.creationToken.equals(row.creationToken))
                    .findFirst().orElse(null);
            if (existing != null) return existing.id;
            request.id = getAll().stream().mapToInt(row -> row.id).max().orElse(0) + 1;
            if (request.details == null || request.details.isEmpty())
                throw new IllegalArgumentException("Yêu cầu không có món.");
            java.util.Map<Integer, Integer> quantities = new java.util.HashMap<>();
            for (OrderDetail detail : request.details) {
                if (detail == null || detail.getDrinkId() <= 0 || detail.getQuantity() <= 0)
                    throw new IllegalArgumentException("Mã đồ uống hoặc số lượng đặt không hợp lệ.");
                quantities.merge(detail.getDrinkId(), detail.getQuantity(), Integer::sum);
            }
            request.inventoryReserved = true;
            inventory.reserveLocked(request.id, quantities);
            try {
                ApiClient.post("/checkoutRequests", gson.toJson(request));
                Request saved = getRequest(request.id);
                if (!request.creationToken.equals(saved.creationToken))
                    throw new IllegalStateException("Không xác nhận được yêu cầu đặt món đã lưu.");
                return request.id;
            } catch (Exception writeFailure) {
                try {
                    Request persisted = getRequest(request.id);
                    if (request.creationToken.equals(persisted.creationToken)) return persisted.id;
                } catch (Exception verificationFailure) {
                    writeFailure.addSuppressed(verificationFailure);
                }
                try { inventory.releaseReservationLocked(request.id); }
                catch (Exception releaseFailure) { writeFailure.addSuppressed(releaseFailure); }
                throw writeFailure;
            }
        });
    }

    public Order confirm(int id, String method, User cashier) throws Exception {
        requireStaff(cashier);
        synchronized (REQUEST_LOCK) {
            Request request = getRequest(id);
            OrderService orderService = new OrderService();
            PaymentService paymentService = new PaymentService();
            if ("SUCCESS".equalsIgnoreCase(request.status)) {
                Order completed = findRequestOrder(request, orderService);
                if (completed != null) return completed;
                throw new IllegalStateException("Yêu cầu đã hoàn tất nhưng không tìm thấy đơn liên kết.");
            }

            String previousStatus = request.status;
            boolean retrying = "PROCESSING".equalsIgnoreCase(request.status);
            if (!retrying && !isWaiting(request.status)) {
                throw new IllegalStateException("Yêu cầu đã bị từ chối hoặc xử lý.");
            }
            if (request.paymentMethod == null || method == null
                    || !method.equalsIgnoreCase(request.paymentMethod)) {
                throw new IllegalArgumentException("Phương thức phải khớp lựa chọn của khách.");
            }
            if (request.details == null || request.details.isEmpty() || !Double.isFinite(request.total)
                    || request.total <= 0) {
                throw new IllegalStateException("Yêu cầu không có món hoặc tổng tiền hợp lệ.");
            }
            double computedTotal = 0;
            for (OrderDetail detail : request.details) {
                if (detail.getQuantity() <= 0 || !Double.isFinite(detail.getPrice()) || detail.getPrice() < 0
                        || (detail.getToppings() != null && detail.getToppings().size() > 3)) {
                    throw new IllegalStateException("Chi tiết món trong yêu cầu không hợp lệ.");
                }
                computedTotal += detail.getQuantity() * detail.getPrice();
            }
            if (!Double.isFinite(computedTotal) || Math.abs(computedTotal - request.total) > 0.01) {
                throw new IllegalStateException("Tổng tiền yêu cầu không khớp với chi tiết món.");
            }

            if (!retrying) {
                request.paymentPendingStatus = request.status;
                request.status = "PROCESSING";
                request.processingToken = UUID.randomUUID().toString();
                request.processingByUserId = cashier.getId();
                String claimToken = request.processingToken;
                saveRequest(request);
                request = getRequest(id);
                if (!"PROCESSING".equalsIgnoreCase(request.status)
                        || !java.util.Objects.equals(claimToken, request.processingToken))
                    throw new IllegalStateException("Yêu cầu đang được nhân viên khác xử lý.");
            }

            Order order = null;
            try {
                order = findRequestOrder(request, orderService);
                if (order != null && "PAYMENT_PROCESSING".equalsIgnoreCase(order.getStatus())
                        && (!Boolean.TRUE.equals(order.getStockDeducted())
                                || Boolean.TRUE.equals(order.getStockRestored()))) {
                    int existingOrderId = order.getId();
                    boolean paymentAlreadySaved = paymentService.getAll().stream().anyMatch(payment ->
                            payment.getOrderId() == existingOrderId && "SUCCESS".equalsIgnoreCase(payment.getStatus()));
                    if (paymentAlreadySaved) {
                        throw new IllegalStateException("Thanh toán đã ghi nhận nhưng tồn kho của order tạm chưa được xác nhận; cần đối soát trước khi tiếp tục.");
                    }
                    orderService.discardUnpaidCashierOrder(order.getId());
                    order = null;
                    request.orderId = null;
                    saveRequest(request);
                }
                if (order == null) {
                    order = orderService.createCashierPaidOrder(request.tableId, request.customerName,
                            request.customerPhone, request.total, request.paymentMethod,
                            request.orderType, request.details, request.id);
                }
                request.orderId = order.getId();
                request.processingByUserId = cashier.getId();
                saveRequest(request);

                paymentService.recordCashierPayment(order, cashier, request.paymentMethod);
                Order confirmed = orderService.confirmCashierOrder(order.getId(), cashier, request.customerUserId);
                finishRequest(request, confirmed.getId(), cashier);
                return confirmed;
            } catch (Exception failure) {
                boolean paymentSaved = false;
                if (order != null) {
                    int orderId = order.getId();
                    try {
                        paymentSaved = paymentService.getAll().stream().anyMatch(payment ->
                                payment.getOrderId() == orderId
                                        && "SUCCESS".equalsIgnoreCase(payment.getStatus()));
                    } catch (Exception verifyFailure) {
                        failure.addSuppressed(verifyFailure);
                    }
                }
                if (paymentSaved && order != null) {
                    try {
                        Order confirmed = orderService.confirmCashierOrder(order.getId(), cashier, request.customerUserId);
                        finishRequest(request, confirmed.getId(), cashier);
                        return confirmed;
                    } catch (Exception recoveryFailure) {
                        failure.addSuppressed(recoveryFailure);
                    }
                } else {
                    try {
                        if (order != null) orderService.discardUnpaidCashierOrder(order.getId());
                        request.status = isWaiting(previousStatus) ? previousStatus
                                : isWaiting(request.paymentPendingStatus) ? request.paymentPendingStatus
                                : ("CASH".equalsIgnoreCase(request.paymentMethod) ? "WAITING_CASHIER" : "WAITING_CONFIRMATION");
                        request.orderId = null;
                        request.processingToken = null;
                        request.processingByUserId = null;
                        request.paymentPendingStatus = null;
                        saveRequest(request);
                    } catch (Exception cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                throw failure;
            }
        }
    }

    public void reject(int id, String reason, boolean moneyReceived, User cashier) throws Exception {
        requireStaff(cashier);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Cần nhập lý do từ chối.");
        synchronized (REQUEST_LOCK) {
            Request request = getRequest(id);
            if (!isWaiting(request.status))
                throw new IllegalStateException("Chỉ từ chối được yêu cầu đang chờ thanh toán.");
            if (request.refundStatus != null && !request.refundStatus.isBlank())
                throw new IllegalStateException("Yêu cầu đã được đối soát thanh toán.");
            request.rejectedByUserId = cashier.getId();
            request.rejectedAt = LocalDateTime.now().toString();
            request.rejectedReason = reason.trim();
            PaymentService payments = new PaymentService();
            if (moneyReceived) {
                // The cashier first verifies funds in the bank or cash drawer; refund is then
                // a separate manual action so the ledger never claims money was returned early.
                Payment received = payments.recordCheckoutRequestPayment(request, cashier);
                request.status = "REFUND_PENDING";
                request.refundStatus = "PENDING";
                request.receivedByUserId = cashier.getId();
                request.paidAt = received.getPaidAt();
                request.refundReceivedByUserId = cashier.getId();
                request.refundReceivedAt = received.getPaidAt();
            } else {
                request.status = "FAILED";
                request.refundStatus = "NOT_RECEIVED";
                request.refundCheckedByUserId = cashier.getId();
                request.refundCheckAt = LocalDateTime.now().toString();
            }
            request.inventoryReserved = false;
            saveRequest(request);
            new InventoryService().releaseReservation(request.id);
        }
    }

    /** Reconciles older rejected requests created before refund tracking was added. */
    public void reconcileRejectedRequest(int id, boolean moneyReceived, User cashier) throws Exception {
        requireStaff(cashier);
        synchronized (REQUEST_LOCK) {
            Request request = getRequest(id);
            if (!"FAILED".equalsIgnoreCase(request.status)
                    || (request.refundStatus != null && !request.refundStatus.isBlank())) {
                throw new IllegalStateException("Yêu cầu này đã được đối soát hoặc không cần đối soát.");
            }
            request.refundCheckedByUserId = cashier.getId();
            request.refundCheckAt = LocalDateTime.now().toString();
            if (moneyReceived) {
                Payment received = new PaymentService().recordCheckoutRequestPayment(request, cashier);
                request.status = "REFUND_PENDING";
                request.refundStatus = "PENDING";
                request.receivedByUserId = cashier.getId();
                request.paidAt = received.getPaidAt();
                request.refundReceivedByUserId = cashier.getId();
                request.refundReceivedAt = received.getPaidAt();
            } else {
                request.refundStatus = "NOT_RECEIVED";
            }
            saveRequest(request);
        }
    }

    /** Confirms the manual cash/bank refund after it has actually been returned to the customer. */
    public void confirmRefund(int id, User cashier) throws Exception {
        requireStaff(cashier);
        synchronized (REQUEST_LOCK) {
            Request request = getRequest(id);
            if (!"REFUND_PENDING".equalsIgnoreCase(request.status)
                    || !"PENDING".equalsIgnoreCase(request.refundStatus)) {
                throw new IllegalStateException("Yêu cầu này không còn chờ hoàn tiền.");
            }
            new PaymentService().recordCheckoutRequestRefund(request, cashier,
                    request.rejectedReason == null || request.rejectedReason.isBlank()
                            ? "Hoàn tiền yêu cầu bị từ chối" : request.rejectedReason);
            request.status = "REFUNDED";
            request.refundStatus = "SUCCESS";
            request.refundConfirmedByUserId = cashier.getId();
            request.refundConfirmedAt = LocalDateTime.now().toString();
            saveRequest(request);
        }
    }

    private Request getRequest(int id) throws Exception {
        return getAll().stream().filter(request -> request.id == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu thanh toán."));
    }

    private Order findRequestOrder(Request request, OrderService orderService) throws Exception {
        if (request.orderId != null && request.orderId > 0) {
            Order linked = orderService.getAll().stream().filter(order -> order.getId() == request.orderId)
                    .findFirst().orElse(null);
            if (linked != null) return linked;
        }
        return orderService.findCashierOrderByRequestId(request.id);
    }

    private void finishRequest(Request request, int orderId, User cashier) throws Exception {
        request.status = "SUCCESS";
        request.orderId = orderId;
        request.inventoryReserved = false;
        request.receivedByUserId = cashier.getId();
        request.paidAt = LocalDateTime.now().toString();
        request.processingToken = null;
        request.processingByUserId = null;
        request.paymentPendingStatus = null;
        saveRequest(request);
    }

    private void saveRequest(Request request) throws Exception {
        ApiClient.put("/checkoutRequests/" + request.id, gson.toJson(request));
        Request persisted = getRequest(request.id);
        if (!java.util.Objects.equals(persisted.status, request.status)
                || !java.util.Objects.equals(persisted.orderId, request.orderId)
                || !java.util.Objects.equals(persisted.refundStatus, request.refundStatus)
                || !java.util.Objects.equals(persisted.processingToken, request.processingToken)) {
            throw new IllegalStateException("Không xác nhận được cập nhật yêu cầu thanh toán.");
        }
    }

    private boolean isWaiting(String status) {
        return "WAITING_CASHIER".equalsIgnoreCase(status) || "WAITING_CONFIRMATION".equalsIgnoreCase(status);
    }

    private void requireStaff(User cashier) {
        if (cashier == null || cashier.getId() <= 0 || !"STAFF".equalsIgnoreCase(cashier.getRole()))
            throw new IllegalArgumentException("Chỉ tài khoản nhân viên mới được xử lý yêu cầu thanh toán.");
    }
}
