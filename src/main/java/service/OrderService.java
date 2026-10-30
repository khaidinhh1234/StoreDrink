package service;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final Duration DUPLICATE_WINDOW = Duration.ofSeconds(30);
    private static final Duration PENDING_TIMEOUT = Duration.ofMinutes(30);
    private static volatile ScheduledExecutorService expiryMonitor;

    public List<Order> getAll() throws Exception {
        String json = ApiClient.get("/orders");
        Type type = new TypeToken<List<Order>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void updateStatus(Order staleOrder, String status, User actor, String cancelReason) throws Exception {
        requireStaff(actor);
        String nextStatus = normalizeStatus(status);
        synchronized (STATUS_TRANSITION_LOCK) {
            Order current = getOrder(staleOrder.getId());
            String currentStatus = normalizeStatus(current.getStatus());
            if ("CONFIRMED".equals(nextStatus)) {
                if (!"PENDING".equals(currentStatus)) {
                    throw new IllegalStateException("Đơn đã được nhân viên khác xử lý hoặc không còn chờ duyệt.");
                }
                if (isExpired(current)) {
                    cancel(current, "SYSTEM", "Đơn tự động hủy do quá 30 phút chưa được duyệt");
                    throw new OrderExpiredException("Đơn đã quá 30 phút và được hệ thống tự động hủy.");
                }
                confirm(current, actor);
                copyStatus(current, staleOrder);
                return;
            }
            if (!"CANCELLED".equals(nextStatus)) {
                throw new IllegalArgumentException("Trạng thái chuyển đổi không hợp lệ: " + status);
            }
            if (!"PENDING".equals(currentStatus) && !"CONFIRMED".equals(currentStatus)) {
                throw new IllegalStateException("Đơn đã được xử lý, không thể hủy.");
            }
            if (cancelReason == null || cancelReason.isBlank()) {
                throw new IllegalArgumentException("Cần nhập lý do hủy đơn.");
            }
            cancel(current, String.valueOf(actor.getId()), cancelReason.trim());
            copyStatus(current, staleOrder);
        }
    }

    private void confirm(Order order, User actor) throws Exception {
        Order latest = getOrder(order.getId());
        if (!"PENDING".equals(normalizeStatus(latest.getStatus()))) {
            throw new IllegalStateException("Đơn đã được xử lý bởi thao tác khác. Danh sách đã được làm mới.");
        }
        if (isExpired(latest)) {
            cancel(latest, "SYSTEM", "Đơn tự động hủy do quá 30 phút chưa được duyệt");
            throw new OrderExpiredException("Đơn đã quá 30 phút và được hệ thống tự động hủy.");
        }

        List<StockChange> legacyStockChanges = List.of();
        if (!Boolean.TRUE.equals(latest.getStockDeducted())) {
            Map<Integer, Integer> legacyReservation = aggregateQuantities(getDetails(order.getId()));
            if (legacyReservation.isEmpty()) throw new IllegalStateException("Order không có món để duyệt.");
            legacyStockChanges = applyStockAdjustments(negate(legacyReservation));
            latest.setReservedStock(legacyReservation);
            latest.setStockDeducted(true);
            latest.setStockRestored(false);
        }
        try {
            Order current = getOrder(order.getId());
            if (!"PENDING".equals(normalizeStatus(current.getStatus())) || isExpired(current)) {
                if (!legacyStockChanges.isEmpty()) {
                    rollbackStock(legacyStockChanges, new IllegalStateException("Đơn đã thay đổi trong lúc duyệt."));
                    legacyStockChanges = List.of();
                }
                if ("PENDING".equals(normalizeStatus(current.getStatus())) && isExpired(current)) {
                    cancel(current, "SYSTEM", "Đơn tự động hủy do quá 30 phút chưa được duyệt");
                    throw new OrderExpiredException("Đơn đã quá 30 phút và được hệ thống tự động hủy.");
                }
                throw new IllegalStateException("Đơn đã được xử lý bởi thao tác khác. Danh sách đã được làm mới.");
            }
            LocalDateTime now = LocalDateTime.now();
            current.setStatus("CONFIRMED");
            current.setProcessedByUserId(actor.getId());
            current.setConfirmedBy(actor.getId());
            current.setConfirmedAt(now.toString());
            if (!legacyStockChanges.isEmpty()) {
                current.setReservedStock(latest.getReservedStock());
                current.setStockDeducted(true);
                current.setStockRestored(false);
            }
            ApiClient.put("/orders/" + current.getId(), gson.toJson(current));
            Order persisted = getOrder(current.getId());
            if (!"CONFIRMED".equals(normalizeStatus(persisted.getStatus()))
                    || persisted.getConfirmedBy() == null
                    || persisted.getConfirmedBy() != actor.getId()) {
                throw new IllegalStateException("Đơn hàng đã được nhân viên khác xử lý. Danh sách đã được làm mới.");
            }
            latest = persisted;
        } catch (Exception exception) {
            try {
                Order persisted = getOrder(order.getId());
                if ("CONFIRMED".equals(normalizeStatus(persisted.getStatus()))
                        && persisted.getConfirmedBy() != null
                        && persisted.getConfirmedBy() == actor.getId()) {
                    copyStatus(persisted, order);
                    return;
                }
            } catch (Exception verificationException) {
                exception.addSuppressed(verificationException);
            }
            rollbackStock(legacyStockChanges, exception);
            throw exception;
        }
        copyStatus(latest, order);
    }

    public void completePayment(Order staleOrder, String paymentMethod, User actor) throws Exception {
        requireStaff(actor);
        if (!"CASH".equalsIgnoreCase(paymentMethod) && !"TRANSFER".equalsIgnoreCase(paymentMethod)) {
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ");
        }
        synchronized (STATUS_TRANSITION_LOCK) {
            Order current = getOrder(staleOrder.getId());
            if (!"CONFIRMED".equals(normalizeStatus(current.getStatus()))) {
                throw new IllegalStateException("Đơn không còn ở trạng thái đã duyệt để thanh toán.");
            }
            current.setPaymentMethod(paymentMethod);
            current.setCompletedAt(LocalDateTime.now().toString());
            current.setCompletedBy(actor.getId());
            current.setStatus("COMPLETED");
            try {
                ApiClient.put("/orders/" + current.getId(), gson.toJson(current));
                Order persisted = getOrder(current.getId());
                if (!"COMPLETED".equals(normalizeStatus(persisted.getStatus()))
                        || persisted.getCompletedBy() == null
                        || persisted.getCompletedBy() != actor.getId()
                        || !paymentMethod.equalsIgnoreCase(persisted.getPaymentMethod())) {
                    throw new IllegalStateException("Đơn hàng đã được nhân viên khác thanh toán. Danh sách đã được làm mới.");
                }
                current = persisted;
            } catch (Exception exception) {
                try {
                    Order persisted = getOrder(current.getId());
                    if ("COMPLETED".equals(normalizeStatus(persisted.getStatus()))
                            && persisted.getCompletedBy() != null
                            && persisted.getCompletedBy() == actor.getId()
                            && paymentMethod.equalsIgnoreCase(persisted.getPaymentMethod())) {
                        copyStatus(persisted, staleOrder);
                        return;
                    }
                } catch (Exception verificationException) {
                    exception.addSuppressed(verificationException);
                }
                throw exception;
            }
            copyStatus(current, staleOrder);
        }
    }

    public int cancelExpiredPendingOrders() throws Exception {
        synchronized (STATUS_TRANSITION_LOCK) {
            int cancelled = 0;
            for (Order candidate : getAll()) {
                if (!"PENDING".equals(normalizeStatus(candidate.getStatus())) || !isExpired(candidate)) continue;
                try {
                    Order current = getOrder(candidate.getId());
                    if ("PENDING".equals(normalizeStatus(current.getStatus())) && isExpired(current)) {
                        cancel(current, "SYSTEM", "Đơn tự động hủy do quá 30 phút chưa được duyệt");
                        cancelled++;
                    }
                } catch (Exception exception) {
                    System.err.println("Không thể tự hủy order #" + candidate.getId()
                            + ": " + exception.getMessage());
                }
            }
            return cancelled;
        }
    }

    public static void startExpiryMonitor() {
        if (expiryMonitor != null) return;
        synchronized (STATUS_TRANSITION_LOCK) {
            if (expiryMonitor != null) return;
            expiryMonitor = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "storedrink-order-expiry");
                thread.setDaemon(true);
                return thread;
            });
            expiryMonitor.scheduleWithFixedDelay(() -> {
                try {
                    new OrderService().cancelExpiredPendingOrders();
                } catch (Exception exception) {
                    System.err.println("Không thể kiểm tra order quá hạn: " + exception.getMessage());
                }
            }, 0, 45, TimeUnit.SECONDS);
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
            if (!reservation.isEmpty()) returnedStock = applyStockAdjustments(reservation);
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

    private boolean isExpired(Order order) {
        LocalDateTime createdAt = parseDateTime(order.getCreatedAt());
        return createdAt != null && Duration.between(createdAt, LocalDateTime.now()).compareTo(PENDING_TIMEOUT) > 0;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))) {
            try { return LocalDateTime.parse(value.trim(), formatter); }
            catch (DateTimeParseException ignored) { }
        }
        return null;
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

    public static class OrderExpiredException extends IllegalStateException {
        public OrderExpiredException(String message) { super(message); }
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
        if (deltas.isEmpty()) return List.of();

        Map<Integer, Drink> drinksById = new HashMap<>();
        for (Drink drink : new DrinkService().getAll()) {
            drinksById.put(drink.getId(), drink);
        }

        List<StockChange> changes = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : deltas.entrySet()) {
            Drink drink = drinksById.get(entry.getKey());
            if (drink == null) {
                throw new IllegalStateException("Không tìm thấy đồ uống có mã " + entry.getKey());
            }
            int newQuantity = drink.getQuantity() + entry.getValue();
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
                try {
                    new DrinkService().update(change.drink);
                } catch (Exception updateException) {
                    try {
                        Drink persisted = gson.fromJson(ApiClient.get("/drinks/" + change.drink.getId()), Drink.class);
                        if (persisted == null || persisted.getQuantity() != change.newQuantity) throw updateException;
                        change.drink.setQuantity(persisted.getQuantity());
                    } catch (Exception verifyException) {
                        if (verifyException != updateException) updateException.addSuppressed(verifyException);
                        // Include the ambiguous write in compensation; writing the prior stock is idempotent here.
                        applied.add(change);
                        throw updateException;
                    }
                }
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
                try {
                    new DrinkService().update(change.drink);
                } catch (Exception updateException) {
                    Drink persisted = gson.fromJson(ApiClient.get("/drinks/" + change.drink.getId()), Drink.class);
                    if (persisted == null || persisted.getQuantity() != change.previousQuantity) throw updateException;
                }
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
        synchronized (ORDER_CREATION_LOCK) {
            synchronized (STATUS_TRANSITION_LOCK) {
                return createOrderLocked(createdByUserId, tableId, customerName, customerPhone,
                        total, paymentMethod, orderType, details);
            }
        }
    }

    private Order createOrderLocked(int createdByUserId, Integer tableId, String customerName,
            String customerPhone, double total, String paymentMethod, String orderType,
            List<OrderDetail> details) throws Exception {
        List<Order> orders = getAll();
        Order duplicate = findRecentDuplicate(orders, tableId, customerName, customerPhone,
                total, orderType, details);
        if (duplicate != null) {
            throw new IllegalStateException("Đơn tương tự vừa được gửi (mã #" + duplicate.getId()
                    + "). Đơn đang chờ duyệt nên chưa tạo thêm order.");
        }

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
        }
        double computedTotal = details.stream().mapToDouble(d -> d.getQuantity() * d.getPrice()).sum();
        if (!Double.isFinite(computedTotal) || computedTotal < 0) throw new IllegalArgumentException("Tổng tiền order không hợp lệ.");
        int orderId = nextId(orders, 1000);
        List<StockChange> reserved = applyStockAdjustments(negate(reservation));
        Order order = new Order(orderId, tableId, customerName, customerPhone, total,
                paymentMethod, "PENDING", orderType, LocalDateTime.now().withNano(0).toString(),
                createdByUserId);
        order.setUserId(createdByUserId);
        order.setTotal(computedTotal);
        order.setStockDeducted(true);
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
                Order persisted = getOrder(orderId);
                // A partial order is retained for audit and cancelled through the same idempotent refund path.
                cancel(persisted, "SYSTEM", "Tạo order lỗi, hệ thống hoàn kho");
            } catch (Exception compensationException) {
                exception.addSuppressed(compensationException);
                // If order creation did not persist, return the reservation directly.
                try {
                    getOrder(orderId);
                } catch (Exception missingOrder) {
                    rollbackStock(reserved, exception);
                }
            }
            throw exception;
        }
        return order;
    }

    private Order findRecentDuplicate(List<Order> orders, Integer tableId, String customerName,
            String customerPhone, double total, String orderType, List<OrderDetail> details)
            throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String items = detailsSignature(details);
        for (Order existing : orders) {
            if (!"PENDING".equalsIgnoreCase(existing.getStatus())
                    || !Objects.equals(existing.getTableId(), tableId)
                    || !normalize(existing.getCustomerName()).equals(normalize(customerName))
                    || !normalize(existing.getCustomerPhone()).equals(normalize(customerPhone))
                    || !normalize(existing.getOrderType()).equals(normalize(orderType))
                    || BigDecimal.valueOf(existing.getTotal()).compareTo(BigDecimal.valueOf(total)) != 0) {
                continue;
            }
            try {
                LocalDateTime createdAt = LocalDateTime.parse(existing.getCreatedAt());
                Duration age = Duration.between(createdAt, now);
                if (!age.isNegative() && age.compareTo(DUPLICATE_WINDOW) <= 0
                        && items.equals(detailsSignature(getDetails(existing.getId())))) {
                    return existing;
                }
            } catch (RuntimeException ignored) {
                // Ignore malformed dates or details on older records and continue checking.
            }
        }
        return null;
    }

    private String detailsSignature(List<OrderDetail> details) {
        return details.stream()
                .sorted(Comparator.comparingInt(OrderDetail::getDrinkId)
                        .thenComparingInt(OrderDetail::getQuantity)
                        .thenComparingDouble(OrderDetail::getPrice))
                .map(detail -> detail.getDrinkId() + ":" + detail.getQuantity() + ":"
                        + BigDecimal.valueOf(detail.getPrice()).stripTrailingZeros().toPlainString())
                .collect(Collectors.joining("|"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ")
                .toLowerCase(java.util.Locale.ROOT);
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

    public void updateDetail(OrderDetail detail, User actor) throws Exception {
        requireStaff(actor);
        synchronized (STATUS_TRANSITION_LOCK) {
            List<OrderDetail> desired = getDetails(detail.getOrderId());
            boolean found = false;
            for (int i = 0; i < desired.size(); i++) if (desired.get(i).getId() == detail.getId()) {
                desired.set(i, detail); found = true; break;
            }
            if (!found) throw new IllegalStateException("Không tìm thấy món trong order.");
            saveOrderDetails(detail.getOrderId(), desired, actor);
        }
    }

    public void addDetail(OrderDetail detail, User actor) throws Exception {
        requireStaff(actor);
        synchronized (STATUS_TRANSITION_LOCK) {
            List<OrderDetail> desired = getDetails(detail.getOrderId());
            desired.add(detail);
            saveOrderDetails(detail.getOrderId(), desired, actor);
        }
    }

    public void deleteDetail(OrderDetail detail, User actor) throws Exception {
        requireStaff(actor);
        synchronized (STATUS_TRANSITION_LOCK) {
            List<OrderDetail> desired = getDetails(detail.getOrderId());
            desired.removeIf(item -> item.getId() == detail.getId());
            saveOrderDetails(detail.getOrderId(), desired, actor);
        }
    }

    /** Atomically-at-service-level updates an order's lines and inventory reservation with compensation. */
    public void saveOrderDetails(int orderId, List<OrderDetail> requested, User actor) throws Exception {
        requireStaff(actor);
        synchronized (STATUS_TRANSITION_LOCK) {
            Order current = requirePendingOrder(orderId);
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
            Map<Integer, Integer> idsByDrink = new HashMap<>();
            Map<Integer, Integer> newReservation = new HashMap<>();
            double newTotal = 0;
            for (OrderDetail line : requested) {
                if (line.getQuantity() <= 0 || line.getDrinkId() <= 0 || !Double.isFinite(line.getPrice()) || line.getPrice() < 0)
                    throw new IllegalArgumentException("Số lượng, mã đồ uống hoặc giá không hợp lệ.");
                if (idsByDrink.put(line.getDrinkId(), line.getId()) != null)
                    throw new IllegalArgumentException("Mỗi đồ uống chỉ được có một dòng trong order.");
                if (line.getId() > 0 && (!originalById.containsKey(line.getId())
                        || originalById.get(line.getId()).getDrinkId() != line.getDrinkId()))
                    throw new IllegalArgumentException("Chi tiết món không thuộc order đang sửa.");
                OrderDetail copy = new OrderDetail(line.getId(), orderId, line.getDrinkId(), line.getQuantity(), line.getPrice());
                copy.setSubtotal(copy.getQuantity() * copy.getPrice());
                desired.add(copy);
                newReservation.merge(copy.getDrinkId(), copy.getQuantity(), Integer::sum);
                newTotal += copy.getSubtotal();
            }
            if (!Double.isFinite(newTotal)) throw new IllegalArgumentException("Tổng tiền order không hợp lệ.");
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
            List<StockChange> applied = applyStockAdjustments(stockDeltas);
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
                current.setStockDeducted(true);
                current.setStockRestored(false);
                ApiClient.put("/orders/" + orderId, gson.toJson(current));
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

    private Order requirePendingOrder(int orderId) throws Exception {
        Order current = getOrder(orderId);
        if (!"PENDING".equals(normalizeStatus(current.getStatus()))) {
            throw new IllegalStateException("Chỉ order đang chờ duyệt mới được sửa món hoặc tổng tiền.");
        }
        return current;
    }

    public List<String> getStatuses() {
        return Arrays.asList("PENDING", "CONFIRMED", "CANCELLED");
    }
}
