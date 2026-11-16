package service;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import model.Drink;
import model.Order;
import model.OrderDetail;
import model.User;
import utils.ApiClient;

public class OrderService {
    private final Gson gson = new Gson();
    private static final Object ORDER_CREATION_LOCK = new Object();
    private static final Object STATUS_TRANSITION_LOCK = new Object();
    private final PaymentService paymentService = new PaymentService();
    private final InventoryService inventoryService = new InventoryService();

    public List<Order> getAll() throws Exception {
        String json = ApiClient.get("/orders");
        Type type = new TypeToken<List<Order>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void updateStatus(Order staleOrder, String status, User actor, String cancelReason) throws Exception {
        requireStaff(actor);
        String nextStatus = normalizeStatus(status);
        if (!"CANCELLED".equals(nextStatus)) {
            throw new IllegalArgumentException("Chỉ cho phép hủy đơn đang pha chế.");
        }
        synchronized (STATUS_TRANSITION_LOCK) {
            Order current = getOrder(staleOrder.getId());
            String currentStatus = normalizeStatus(current.getStatus());
            if (!"CONFIRMED".equals(currentStatus)) {
                throw new IllegalStateException("Chỉ đơn đã duyệt, đang pha chế mới được hủy.");
            }
            if (cancelReason == null || cancelReason.isBlank()) {
                throw new IllegalArgumentException("Cần nhập lý do hủy đơn.");
            }
            model.Payment payment = paymentService.getAll().stream()
                    .filter(p -> p.getOrderId() == current.getId()
                            && "SUCCESS".equalsIgnoreCase(p.getStatus()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Chỉ hủy được đơn đã thanh toán thành công."));
            // Persist the refund transaction first. If order cancellation later fails, retrying
            // returns this same ledger entry and can finish cancellation without refunding twice.
            paymentService.recordRefund(current.getId(), actor, cancelReason.trim());
            cancel(current, String.valueOf(actor.getId()), cancelReason.trim());
            copyStatus(current, staleOrder);
        }
    }

    /** Marks an already-paid, in-preparation order as fulfilled without touching inventory. */
    public void completeOrder(Order staleOrder, User actor) throws Exception {
        requireStaff(actor);
        synchronized (STATUS_TRANSITION_LOCK) {
            Order current=getOrder(staleOrder.getId());
            if(!"CONFIRMED".equals(normalizeStatus(current.getStatus())))
                throw new IllegalStateException("Chỉ đơn đang pha chế mới được hoàn tất.");
            current.setStatus("COMPLETED"); current.setCompletedBy(actor.getId());
            current.setCompletedAt(LocalDateTime.now().toString());
            ApiClient.put("/orders/"+current.getId(),gson.toJson(current));
            Order saved=getOrder(current.getId());
            if(!"COMPLETED".equals(normalizeStatus(saved.getStatus())) || saved.getCompletedBy()==null || saved.getCompletedBy()!=actor.getId())
                throw new IllegalStateException("Không xác nhận được trạng thái hoàn tất.");
            copyStatus(saved,staleOrder);
        }
    }

    private void cancel(Order order, String cancelledBy, String reason) throws Exception {
        List<StockChange> returnedStock = List.of();
        boolean legacyConfirmedReservation = "CONFIRMED".equals(normalizeStatus(order.getStatus()))
                && order.getStockDeducted() == null;
        boolean shouldRestore = !Boolean.TRUE.equals(order.getStockRestored())
                && (Boolean.TRUE.equals(order.getStockDeducted()) || legacyConfirmedReservation);
        if (shouldRestore) {
            Map<Integer, Integer> reservation = order.getReservedStock();
            if (reservation == null || reservation.isEmpty()) {
                reservation = aggregateQuantities(getDetails(order.getId()));
            }
            if (!reservation.isEmpty()) returnedStock = applyStockAdjustments(
                    reservation, "order:" + order.getId() + ":cancel-restore", null, null);
            order.setStockDeducted(false);
        }
        order.setStockRestored(true);
        order.setStatus("CANCELLED");
        order.setCancelledBy(cancelledBy);
        order.setCancelledAt(LocalDateTime.now().toString());
        order.setCancelReason(reason);
        try {
            ApiClient.put("/orders/" + order.getId(), gson.toJson(order));
            Order persisted = getOrder(order.getId());
            if (!"CANCELLED".equals(normalizeStatus(persisted.getStatus()))
                    || !Boolean.TRUE.equals(persisted.getStockRestored())
                    || !Objects.equals(cancelledBy, persisted.getCancelledBy())
                    || !Objects.equals(reason, persisted.getCancelReason())) {
                throw new IllegalStateException("Đơn hàng đã được nhân viên khác xử lý. Danh sách đã được làm mới.");
            }
            copyStatus(persisted, order);
        } catch (Exception exception) {
            try {
                Order persisted = getOrder(order.getId());
                if ("CANCELLED".equals(normalizeStatus(persisted.getStatus()))
                        && Boolean.TRUE.equals(persisted.getStockRestored())
                        && Objects.equals(cancelledBy, persisted.getCancelledBy())
                        && Objects.equals(reason, persisted.getCancelReason())) {
                    copyStatus(persisted, order);
                    return;
                }
            } catch (Exception verificationException) {
                exception.addSuppressed(verificationException);
            }
            rollbackStock(returnedStock, exception);
            throw exception;
        }
    }

    private Order getOrder(int orderId) throws Exception {
        Order order = gson.fromJson(ApiClient.get("/orders/" + orderId), Order.class);
        if (order == null || order.getId() != orderId) {
            throw new IllegalStateException("Không tìm thấy đơn hàng #" + orderId);
        }
        return order;
    }

    private void requireStaff(User actor) {
        if (actor == null || actor.getId() <= 0 || !"STAFF".equalsIgnoreCase(actor.getRole())) {
            throw new SecurityException("Chỉ nhân viên được phép xử lý đơn hàng.");
        }
    }

    private void copyStatus(Order source, Order target) {
        target.setStatus(source.getStatus());
        target.setProcessedByUserId(source.getProcessedByUserId());
        target.setConfirmedBy(source.getConfirmedBy());
        target.setConfirmedAt(source.getConfirmedAt());
        target.setCompletedBy(source.getCompletedBy());
        target.setCompletedAt(source.getCompletedAt());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setCancelledBy(source.getCancelledBy());
        target.setCancelledAt(source.getCancelledAt());
        target.setCancelReason(source.getCancelReason());
        target.setStockDeducted(source.getStockDeducted());
        target.setStockRestored(source.getStockRestored());
        target.setReservedStock(source.getReservedStock());
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private Map<Integer, Integer> aggregateQuantities(List<OrderDetail> details) {
        Map<Integer, Integer> quantities = new HashMap<>();
        for (OrderDetail detail : details) {
            if (detail.getDrinkId() <= 0 || detail.getQuantity() <= 0)
                throw new IllegalArgumentException("Số lượng hoặc mã đồ uống trong order không hợp lệ");
            quantities.merge(detail.getDrinkId(), detail.getQuantity(), Integer::sum);
        }
        return quantities;
    }

    private Map<Integer, Integer> negate(Map<Integer, Integer> quantities) {
        Map<Integer, Integer> result = new HashMap<>();
        quantities.forEach((id, quantity) -> result.put(id, -quantity));
        return result;
    }

    private List<StockChange> applyStockAdjustments(Map<Integer, Integer> deltas) throws Exception {
        return applyStockAdjustments(deltas, "stock:" + UUID.randomUUID(), null, null);
    }

    private List<StockChange> applyStockAdjustments(Map<Integer, Integer> deltas, String operationId,
            Integer reservationId, Integer restoreReservationId) throws Exception {
        if (deltas.isEmpty()) return List.of();
        inventoryService.adjust(operationId, deltas, reservationId, restoreReservationId);
        Map<Integer, Integer> appliedDeltas = new HashMap<>(deltas);
        appliedDeltas.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() == 0);
        if (appliedDeltas.isEmpty()) return List.of();
        return List.of(new StockChange(appliedDeltas, reservationId, restoreReservationId));
    }

    private void rollbackStock(List<StockChange> changes, Exception originalException) {
        for (int index = changes.size() - 1; index >= 0; index--) {
            StockChange change = changes.get(index);
            try {
                Map<Integer, Integer> inverse = new HashMap<>();
                change.deltas().forEach((drinkId, delta) -> inverse.put(drinkId, -delta));
                inventoryService.adjust("stock-compensation:" + UUID.randomUUID(), inverse,
                        change.restoreReservationId(), change.reservationId());
            } catch (Exception rollbackException) {
                originalException.addSuppressed(rollbackException);
            }
        }
    }

    private record StockChange(Map<Integer, Integer> deltas, Integer reservationId,
            Integer restoreReservationId) { }

    private Order createOrder(int createdByUserId, Integer tableId, String customerName,
            String customerPhone, double total, String paymentMethod, String orderType,
            List<OrderDetail> details, Integer checkoutRequestId) throws Exception {
        synchronized (ORDER_CREATION_LOCK) {
            synchronized (STATUS_TRANSITION_LOCK) {
                return createOrderLocked(createdByUserId, tableId, customerName, customerPhone,
                        total, paymentMethod, orderType, details, checkoutRequestId);
            }
        }
    }

    /** Creates an internal payment-processing order and reserves inventory before final confirmation. */
    public Order createCashierPaidOrder(Integer tableId, String customerName, String customerPhone,
            double total, String paymentMethod, String orderType, List<OrderDetail> details) throws Exception {
        return createCashierPaidOrder(tableId, customerName, customerPhone, total, paymentMethod,
                orderType, details, null);
    }

    public Order createCashierPaidOrder(Integer tableId, String customerName, String customerPhone,
            double total, String paymentMethod, String orderType, List<OrderDetail> details,
            Integer checkoutRequestId) throws Exception {
        synchronized (STATUS_TRANSITION_LOCK) {
            Order order = createOrder(0, tableId, customerName, customerPhone, total,
                    paymentMethod, orderType, details, checkoutRequestId);
            List<StockChange> deducted = List.of();
            try {
                Map<Integer, Integer> quantities = aggregateQuantities(details);
                deducted = applyStockAdjustments(negate(quantities),
                        "order:" + order.getId() + ":deduct", checkoutRequestId, null);
                order.setReservedStock(quantities);
                order.setStockDeducted(true);
                order.setStockRestored(false);
                try {
                    ApiClient.put("/orders/" + order.getId(), gson.toJson(order));
                } catch (Exception writeFailure) {
                    try {
                        Order persisted = getOrder(order.getId());
                        if (!Boolean.TRUE.equals(persisted.getStockDeducted())) {
                            throw writeFailure;
                        }
                        order = persisted;
                    } catch (Exception verifyFailure) {
                        if (verifyFailure != writeFailure) writeFailure.addSuppressed(verifyFailure);
                        throw writeFailure;
                    }
                }
                Order persisted = getOrder(order.getId());
                if (!Boolean.TRUE.equals(persisted.getStockDeducted()))
                    throw new IllegalStateException("Không xác nhận được cập nhật tồn kho cho đơn.");
                return persisted;
            } catch (Exception exception) {
                try {
                    Order latest = getOrder(order.getId());
                    if (!Boolean.TRUE.equals(latest.getStockDeducted()) && !deducted.isEmpty()) {
                        rollbackStock(deducted, exception);
                    }
                    discardUnpaidCashierOrder(order.getId());
                } catch (Exception cleanup) { exception.addSuppressed(cleanup); }
                throw exception;
            }
        }
    }

    public Order findCashierOrderByRequestId(int requestId) throws Exception {
        if (requestId <= 0) return null;
        return getAll().stream().filter(order -> order.getSourceCheckoutRequestId() != null
                && order.getSourceCheckoutRequestId() == requestId).findFirst().orElse(null);
    }

    public Order confirmCashierOrder(int orderId, User cashier, Integer customerUserId) throws Exception {
        requireStaff(cashier);
        synchronized (STATUS_TRANSITION_LOCK) {
            Order order = getOrder(orderId);
            boolean paid = paymentService.getAll().stream().anyMatch(payment -> payment.getOrderId() == orderId
                    && "SUCCESS".equalsIgnoreCase(payment.getStatus()));
            if (!paid) throw new IllegalStateException("Chưa ghi nhận thanh toán thành công cho đơn.");
            if ("CONFIRMED".equals(normalizeStatus(order.getStatus()))) return order;
            if (!"PAYMENT_PROCESSING".equals(normalizeStatus(order.getStatus()))) {
                throw new IllegalStateException("Đơn không ở trạng thái chờ hoàn tất thanh toán.");
            }
            order.setStatus("CONFIRMED");
            order.setConfirmedBy(cashier.getId());
            order.setConfirmedAt(LocalDateTime.now().toString());
            order.setProcessedByUserId(cashier.getId());
            if (customerUserId != null && customerUserId > 0) order.setUserId(customerUserId);
            ApiClient.put("/orders/" + orderId, gson.toJson(order));
            Order saved = getOrder(orderId);
            if (!"CONFIRMED".equals(normalizeStatus(saved.getStatus()))
                    || saved.getConfirmedBy() == null || saved.getConfirmedBy() != cashier.getId()) {
                throw new IllegalStateException("Không xác nhận được đơn đã thanh toán.");
            }
            return saved;
        }
    }

    /** Deletes a cashier checkout draft and restores its reservation when payment was not recorded. */
    public void discardUnpaidCashierOrder(int orderId) throws Exception {
        synchronized (STATUS_TRANSITION_LOCK) {
            Order order = getAll().stream().filter(item -> item.getId() == orderId).findFirst().orElse(null);
            if (order == null) {
                for (OrderDetail detail : getDetails(orderId)) ApiClient.delete("/orderDetails/" + detail.getId());
                return;
            }
            if (!"PAYMENT_PROCESSING".equals(normalizeStatus(order.getStatus()))) {
                throw new IllegalStateException("Chỉ có thể dọn order đang xử lý thanh toán.");
            }
            if (paymentService.getAll().stream().anyMatch(payment -> payment.getOrderId() == orderId
                    && "SUCCESS".equalsIgnoreCase(payment.getStatus()))) {
                throw new IllegalStateException("Đơn đã có thanh toán thành công, không thể xóa.");
            }
            if (Boolean.TRUE.equals(order.getStockDeducted()) && !Boolean.TRUE.equals(order.getStockRestored())) {
                Map<Integer, Integer> reservation = order.getReservedStock();
                if (reservation == null || reservation.isEmpty()) reservation = aggregateQuantities(getDetails(orderId));
                List<StockChange> restored = applyStockAdjustments(reservation,
                        "order:" + orderId + ":discard-restore", null, order.getSourceCheckoutRequestId());
                order.setStockDeducted(false);
                order.setStockRestored(true);
                try {
                    ApiClient.put("/orders/" + orderId, gson.toJson(order));
                    Order persisted = getOrder(orderId);
                    if (Boolean.TRUE.equals(persisted.getStockDeducted())
                            || !Boolean.TRUE.equals(persisted.getStockRestored())) {
                        throw new IllegalStateException("Không xác nhận được hoàn kho cho order tạm.");
                    }
                    order = persisted;
                } catch (Exception exception) {
                    try {
                        Order persisted = getOrder(orderId);
                        if (!Boolean.TRUE.equals(persisted.getStockDeducted())
                                && Boolean.TRUE.equals(persisted.getStockRestored())) {
                            order = persisted;
                        } else {
                            rollbackStock(restored, exception);
                            throw exception;
                        }
                    } catch (Exception verificationFailure) {
                        if (verificationFailure != exception) exception.addSuppressed(verificationFailure);
                        throw exception;
                    }
                }
            }
            ApiClient.delete("/orders/" + orderId);
            for (OrderDetail detail : getDetails(orderId)) ApiClient.delete("/orderDetails/" + detail.getId());
        }
    }

    /** Staff-created counter orders are paid immediately and go straight to preparation. */
    public Order createStaffCounterPaidOrder(String customerName,String customerPhone,double total,
            String method,List<OrderDetail> details,User staff) throws Exception {
        requireStaff(staff);
        Order order=createCashierPaidOrder(null,customerName,customerPhone,total,method,"TAKEAWAY",details);
        try {
            paymentService.recordCashierPayment(order,staff,method);
            return confirmCashierOrder(order.getId(), staff, null);
        } catch(Exception exception) {
            boolean paymentSaved = false;
            try { paymentSaved = paymentService.getAll().stream().anyMatch(payment -> payment.getOrderId() == order.getId()
                    && "SUCCESS".equalsIgnoreCase(payment.getStatus())); }
            catch (Exception verificationFailure) { exception.addSuppressed(verificationFailure); }
            if (paymentSaved) {
                try { return confirmCashierOrder(order.getId(), staff, null); }
                catch (Exception recoveryFailure) { exception.addSuppressed(recoveryFailure); }
            } else {
                try { discardUnpaidCashierOrder(order.getId()); }
                catch(Exception cleanup){exception.addSuppressed(cleanup);}
            }
            throw exception;
        }
    }

    private Order createOrderLocked(int createdByUserId, Integer tableId, String customerName,
            String customerPhone, double total, String paymentMethod, String orderType,
            List<OrderDetail> details, Integer checkoutRequestId) throws Exception {
        List<Order> orders = getAll();
        if (details == null || details.isEmpty()) throw new IllegalArgumentException("Order phải có ít nhất một đồ uống.");
        Map<Integer, Integer> reservation = aggregateQuantities(details);
        for (OrderDetail detail : details) {
            if (!Double.isFinite(detail.getPrice()) || detail.getPrice() < 0)
                throw new IllegalArgumentException("Giá đồ uống trong order không hợp lệ.");
        }
        Map<Integer, Drink> availableDrinks = new HashMap<>();
        for (Drink drink : new DrinkService().getAll()) availableDrinks.put(drink.getId(), drink);
        for (Integer drinkId : reservation.keySet()) {
            Drink drink = availableDrinks.get(drinkId);
            if (drink == null || !drink.isActive())
                throw new IllegalStateException("Đồ uống mã " + drinkId + " không còn được bán.");
            if (reservation.get(drinkId) > drink.getQuantity())
                throw new IllegalStateException("Order cần tổng " + reservation.get(drinkId) + " cốc "
                        + drink.getName() + " ở các size, nhưng kho chỉ còn " + drink.getQuantity() + " cốc.");
        }
        double computedTotal = details.stream().mapToDouble(d -> d.getQuantity() * d.getPrice()).sum();
        if (!Double.isFinite(computedTotal) || computedTotal < 0) throw new IllegalArgumentException("Tổng tiền order không hợp lệ.");
        int orderId = nextOrderId(orders);
        Order order = new Order(orderId, tableId, customerName, customerPhone, total,
                paymentMethod, "PAYMENT_PROCESSING", orderType, LocalDateTime.now().withNano(0).toString(),
                createdByUserId);
        order.setSourceCheckoutRequestId(checkoutRequestId);
        order.setUserId(createdByUserId);
        order.setTotal(computedTotal);
        order.setStockDeducted(false);
        order.setStockRestored(false);
        order.setReservedStock(reservation);
        try {
            postAndConfirm("/orders", gson.toJson(order), "/orders/" + orderId);
            int detailId = nextDetailId();
            for (OrderDetail detail : details) {
                detail.setId(detailId++);
                detail.setOrderId(orderId);
                detail.setSubtotal(detail.getQuantity() * detail.getPrice());
                postAndConfirm("/orderDetails", gson.toJson(detail), "/orderDetails/" + detail.getId());
            }
        } catch (Exception exception) {
            try {
                discardUnpaidCashierOrder(orderId);
            } catch (Exception compensationException) { exception.addSuppressed(compensationException); }
            throw exception;
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

    private int nextOrderId(List<Order> orders) throws Exception {
        int max = orders.stream().mapToInt(Order::getId).max().orElse(1000);
        Type type = new TypeToken<List<OrderDetail>>() {}.getType();
        List<OrderDetail> details = gson.fromJson(ApiClient.get("/orderDetails"), type);
        if (details != null) max = Math.max(max, details.stream().mapToInt(OrderDetail::getOrderId).max().orElse(0));
        return Math.max(1001, max + 1);
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

    /** Returns only orders paid by this employee for personal revenue reports. */
    public List<Order> getForProcessor(int processorId) throws Exception {
        return getAll().stream()
                .filter(order -> {
                    int responsibleId = order.getCompletedBy() == null ? 0 : order.getCompletedBy();
                    if (responsibleId <= 0 && order.getConfirmedBy() != null) responsibleId = order.getConfirmedBy();
                    if (responsibleId <= 0) responsibleId = order.getProcessedByUserId();
                    return responsibleId == processorId;
                })
                .toList();
    }

    public List<OrderDetail> getDetails(int orderId) throws Exception {
        Type type = new TypeToken<List<OrderDetail>>() {}.getType();
        List<OrderDetail> details = gson.fromJson(ApiClient.get("/orderDetails?orderId=" + orderId), type);
        return details == null ? new java.util.ArrayList<>() : details;
    }

    /** Checks stock and product validity before the cashier collects or refunds an edit difference. */
    public void validateOrderDetailsStock(int orderId, List<OrderDetail> requested, User actor) throws Exception {
        requireStaff(actor);
        synchronized (STATUS_TRANSITION_LOCK) {
            Order current = requirePaidConfirmedOrder(orderId);
            if (requested == null || requested.isEmpty()) {
                throw new IllegalArgumentException("Order phải còn ít nhất 1 đồ uống.");
            }
            List<OrderDetail> original = getDetails(orderId);
            Map<Integer, OrderDetail> originalById = original.stream()
                    .collect(Collectors.toMap(OrderDetail::getId, detail -> detail));
            Map<Integer, Integer> newReservation = new HashMap<>();
            for (OrderDetail line : requested) {
                if (line.getQuantity() <= 0 || line.getDrinkId() <= 0
                        || !Double.isFinite(line.getPrice()) || line.getPrice() < 0
                        || (line.getToppings() != null && line.getToppings().size() > 3)) {
                    throw new IllegalArgumentException("Số lượng, mã đồ uống hoặc giá không hợp lệ.");
                }
                if (line.getId() > 0 && (!originalById.containsKey(line.getId())
                        || originalById.get(line.getId()).getDrinkId() != line.getDrinkId())) {
                    throw new IllegalArgumentException("Chi tiết món không thuộc order đang sửa.");
                }
                newReservation.merge(line.getDrinkId(), line.getQuantity(), Integer::sum);
            }

            Map<Integer, Integer> oldReservation = Boolean.TRUE.equals(current.getStockDeducted())
                    ? current.getReservedStock() : new HashMap<>();
            if (Boolean.TRUE.equals(current.getStockDeducted())
                    && (oldReservation == null || oldReservation.isEmpty())) {
                oldReservation = aggregateQuantities(original);
            }
            Map<Integer, Integer> stockDeltas = new HashMap<>(oldReservation);
            newReservation.forEach((id, quantity) -> stockDeltas.merge(id, -quantity, Integer::sum));
            stockDeltas.entrySet().removeIf(entry -> entry.getValue() == 0);

            Map<Integer, Drink> catalog = new HashMap<>();
            for (Drink drink : new DrinkService().getAll()) catalog.put(drink.getId(), drink);
            for (Integer drinkId : newReservation.keySet()) {
                Drink drink = catalog.get(drinkId);
                boolean alreadyInOrder = original.stream().anyMatch(line -> line.getDrinkId() == drinkId);
                if (drink == null || (!drink.isActive() && !alreadyInOrder)) {
                    throw new IllegalStateException("Đồ uống mã " + drinkId + " không thể thêm vào order.");
                }
            }
            for (Map.Entry<Integer, Integer> entry : stockDeltas.entrySet()) {
                Drink drink = catalog.get(entry.getKey());
                if (drink == null) {
                    throw new IllegalStateException("Không tìm thấy đồ uống có mã " + entry.getKey());
                }
                if (drink.getQuantity() + entry.getValue() < 0) {
                    throw new IllegalStateException(drink.getName() + " chỉ còn " + drink.getQuantity()
                            + " cốc trong kho, không đủ cho order đã sửa.");
                }
            }
        }
    }

    /** Atomically-at-service-level updates an order's lines and inventory reservation with compensation. */
    public void saveOrderDetails(int orderId, List<OrderDetail> requested, User actor) throws Exception {
        saveOrderDetails(orderId, requested, actor, null, null);
    }

    /** Saves the order, inventory changes, and the confirmed money adjustment as one compensated workflow. */
    public void saveOrderDetails(int orderId, List<OrderDetail> requested, User actor,
            Double confirmedAdjustment, String adjustmentReason) throws Exception {
        requireStaff(actor);
        synchronized (STATUS_TRANSITION_LOCK) {
            Order current = requirePaidConfirmedOrder(orderId);
            Order originalOrder = gson.fromJson(gson.toJson(current), Order.class);
            if (requested == null || requested.isEmpty()) throw new IllegalArgumentException("Order phải còn ít nhất 1 đồ uống.");
            List<OrderDetail> original = getDetails(orderId);
            Map<Integer, OrderDetail> originalById = original.stream()
                    .collect(Collectors.toMap(OrderDetail::getId, d -> d));
            Map<Integer, Integer> oldReservation = Boolean.TRUE.equals(current.getStockDeducted())
                    ? current.getReservedStock() : new HashMap<>();
            if (Boolean.TRUE.equals(current.getStockDeducted())
                    && (oldReservation == null || oldReservation.isEmpty())) oldReservation = aggregateQuantities(original);
            List<OrderDetail> desired = new ArrayList<>();
            java.util.Set<Integer> seenLineIds = new java.util.HashSet<>();
            Map<Integer, Integer> newReservation = new HashMap<>();
            double newTotal = 0;
            for (OrderDetail line : requested) {
                if (line.getQuantity() <= 0 || line.getDrinkId() <= 0 || !Double.isFinite(line.getPrice())
                        || line.getPrice() < 0 || (line.getToppings() != null && line.getToppings().size() > 3))
                    throw new IllegalArgumentException("Số lượng, mã đồ uống, giá hoặc số topping không hợp lệ.");
                if(line.getId()>0&&!seenLineIds.add(line.getId()))
                    throw new IllegalArgumentException("Chi tiết món bị trùng trong order.");
                if (line.getId() > 0 && (!originalById.containsKey(line.getId())
                        || originalById.get(line.getId()).getDrinkId() != line.getDrinkId()))
                    throw new IllegalArgumentException("Chi tiết món không thuộc order đang sửa.");
                OrderDetail copy = new OrderDetail(line.getId(), orderId, line.getDrinkId(), line.getQuantity(), line.getPrice());
                copy.setSubtotal(copy.getQuantity() * copy.getPrice());
                copy.setDrinkName(line.getDrinkName()); copy.setSize(line.getSize());
                copy.setSizeExtraPrice(line.getSizeExtraPrice()); copy.setToppings(line.getToppings());
                copy.setToppingTotal(line.getToppingTotal()); copy.setNote(line.getNote());
                desired.add(copy);
                newReservation.merge(copy.getDrinkId(), copy.getQuantity(), Integer::sum);
                newTotal += copy.getSubtotal();
            }
            if (!Double.isFinite(newTotal)) throw new IllegalArgumentException("Tổng tiền order không hợp lệ.");
            double actualAdjustment = newTotal - current.getTotal();
            boolean hasAdjustment = Math.abs(actualAdjustment) > 0.0001;
            if (hasAdjustment && (confirmedAdjustment == null
                    || Math.abs(confirmedAdjustment - actualAdjustment) > 0.01
                    || adjustmentReason == null || adjustmentReason.isBlank())) {
                throw new IllegalStateException("Cần xác nhận và ghi rõ xử lý khoản chênh lệch trước khi lưu order.");
            }
            if (!hasAdjustment && confirmedAdjustment != null) {
                throw new IllegalArgumentException("Order không có chênh lệch thanh toán để ghi nhận.");
            }
            Map<Integer, Drink> catalog = new HashMap<>();
            for (Drink drink : new DrinkService().getAll()) catalog.put(drink.getId(), drink);
            for (OrderDetail line : desired) {
                Drink drink = catalog.get(line.getDrinkId());
                boolean alreadyInOrder = original.stream().anyMatch(old -> old.getDrinkId() == line.getDrinkId());
                if (drink == null || (!drink.isActive() && !alreadyInOrder))
                    throw new IllegalStateException("Đồ uống mã " + line.getDrinkId() + " không thể thêm vào order.");
            }
            Map<Integer, Integer> stockDeltas = new HashMap<>(oldReservation);
            newReservation.forEach((id, quantity) -> stockDeltas.merge(id, -quantity, Integer::sum));
            stockDeltas.entrySet().removeIf(entry -> entry.getValue() == 0);
            List<StockChange> applied = Boolean.TRUE.equals(current.getStockDeducted())
                    ? applyStockAdjustments(stockDeltas) : List.of();
            List<OrderDetail> toPersist = new ArrayList<>();
            try {
                int nextId = nextDetailId();
                for (OrderDetail line : desired) {
                    if (line.getId() <= 0) line.setId(nextId++);
                    line.setOrderId(orderId);
                    toPersist.add(line);
                }
                Map<Integer, OrderDetail> wantedById = toPersist.stream().filter(d -> d.getId() > 0)
                        .collect(Collectors.toMap(OrderDetail::getId, d -> d));
                for (OrderDetail old : original) if (!wantedById.containsKey(old.getId())) ApiClient.delete("/orderDetails/" + old.getId());
                for (OrderDetail line : toPersist) {
                    if (originalById.containsKey(line.getId())) ApiClient.put("/orderDetails/" + line.getId(), gson.toJson(line));
                    else postAndConfirm("/orderDetails", gson.toJson(line), "/orderDetails/" + line.getId());
                }
                current.setTotal(newTotal);
                current.setReservedStock(newReservation);
                if (Boolean.TRUE.equals(current.getStockDeducted())) {
                    current.setStockDeducted(true);
                    current.setStockRestored(false);
                } else {
                    current.setStockDeducted(false);
                    current.setStockRestored(false);
                }
                ApiClient.put("/orders/" + orderId, gson.toJson(current));
                if (confirmedAdjustment != null) {
                    paymentService.recordOrderAdjustment(current, actor, actualAdjustment,
                            actualAdjustment < 0, adjustmentReason.trim());
                }
                for (int i = 0; i < requested.size(); i++) requested.get(i).setId(toPersist.get(i).getId());
            } catch (Exception exception) {
                try { restoreDetails(orderId, original); } catch (Exception e) { exception.addSuppressed(e); }
                try { ApiClient.put("/orders/" + orderId, gson.toJson(originalOrder)); } catch (Exception e) { exception.addSuppressed(e); }
                rollbackStock(applied, exception);
                throw exception;
            }
        }
    }

    private void restoreDetails(int orderId, List<OrderDetail> original) throws Exception {
        List<OrderDetail> now = getDetails(orderId);
        Map<Integer, OrderDetail> originals = original.stream().collect(Collectors.toMap(OrderDetail::getId, d -> d));
        for (OrderDetail line : now) if (!originals.containsKey(line.getId())) ApiClient.delete("/orderDetails/" + line.getId());
        Map<Integer, OrderDetail> existing = now.stream().collect(Collectors.toMap(OrderDetail::getId, d -> d));
        for (OrderDetail line : original) {
            if (existing.containsKey(line.getId())) ApiClient.put("/orderDetails/" + line.getId(), gson.toJson(line));
            else postAndConfirm("/orderDetails", gson.toJson(line), "/orderDetails/" + line.getId());
        }
    }

    private Order requirePaidConfirmedOrder(int orderId) throws Exception {
        Order current = getOrder(orderId);
        if (!"CONFIRMED".equals(normalizeStatus(current.getStatus()))) {
            throw new IllegalStateException("Chỉ sửa được đơn đã thanh toán, đang pha chế.");
        }
        if (paymentService.getAll().stream().noneMatch(p -> p.getOrderId() == orderId
                && "SUCCESS".equalsIgnoreCase(p.getStatus()))) {
            throw new IllegalStateException("Không tìm thấy thanh toán thành công cho đơn đang pha chế.");
        }
        return current;
    }

}
