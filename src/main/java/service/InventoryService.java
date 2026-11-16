package service;

import java.lang.reflect.Type;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import model.Drink;
import utils.ApiClient;

/** Inventory operations implemented through the project's existing JSON Server REST API. */
public class InventoryService {
    private static final ReentrantLock JVM_LOCK = new ReentrantLock(true);

    private final Gson gson = new Gson();

    public static class Availability {
        public int drinkId;
        public int stock;
        public int reserved;
        public int available;
    }

    public static class Change {
        public int drinkId;
        public int delta;
        public int previousQuantity;
        public int newQuantity;
        public String drinkName;
    }

    public static class AdjustmentResult {
        public String operationId;
        public List<Change> changes = List.of();
        public boolean duplicate;
    }

    public static class Reservation {
        public int id;
        public int requestId;
        public Map<String, Integer> quantities = new HashMap<>();
        public String status;
        public String createdAt;
        public String consumedAt;
        public String releasedAt;
        public String reactivatedAt;
    }

    private static class Operation {
        String id;
        String operationId;
        String signature;
        String status;
        String appliedAt;
        Integer reservationId;
        Integer restoreReservationId;
        List<Change> changes;
    }

    @FunctionalInterface
    public interface InventoryWork<T> {
        T run() throws Exception;
    }

    public <T> T withLock(InventoryWork<T> work) throws Exception {
        JVM_LOCK.lock();
        try {
            Path lockPath = Path.of(System.getProperty("java.io.tmpdir"), "storedrink-inventory.lock");
            try (FileChannel channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock ignored = channel.lock()) {
                return work.run();
            }
        } finally {
            JVM_LOCK.unlock();
        }
    }

    public int getAvailable(int drinkId) throws Exception {
        return getAllAvailable().getOrDefault(drinkId, 0);
    }

    public Map<Integer, Integer> getAllAvailable() throws Exception {
        Map<Integer, Integer> held = heldQuantities(null);
        Map<Integer, Integer> result = new HashMap<>();
        for (Drink drink : new DrinkService().getAll()) {
            int stock = Math.max(0, drink.getQuantity());
            result.put(drink.getId(), Math.max(0, stock - held.getOrDefault(drink.getId(), 0)));
        }
        return result;
    }

    /** Must be called inside withLock. A hold reserves availability but does not deduct stock. */
    public void reserveLocked(int requestId, Map<Integer, Integer> quantities) throws Exception {
        if (requestId <= 0 || quantities == null || quantities.isEmpty())
            throw new IllegalArgumentException("Thông tin giữ chỗ tồn kho không hợp lệ.");
        Reservation existing = findReservation(requestId);
        Map<String, Integer> normalized = toStringKeys(quantities);
        if (existing != null) {
            if ("ACTIVE".equalsIgnoreCase(existing.status) && existing.quantities.equals(normalized)) return;
            throw new IllegalStateException("Yêu cầu này đã có một giữ chỗ tồn kho khác.");
        }

        Map<Integer, Integer> available = getAllAvailable();
        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0)
                throw new IllegalArgumentException("Số lượng đặt món không hợp lệ.");
            int current = available.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() > current) {
                Drink drink = new DrinkService().getAll().stream()
                        .filter(item -> item.getId() == entry.getKey()).findFirst().orElse(null);
                String name = drink == null ? "Đồ uống" : drink.getName();
                throw new IllegalStateException(name + " chỉ còn " + current
                        + " cốc có thể đặt; yêu cầu cần " + entry.getValue() + " cốc.");
            }
        }

        Reservation reservation = new Reservation();
        reservation.id = requestId;
        reservation.requestId = requestId;
        reservation.quantities = normalized;
        reservation.status = "ACTIVE";
        reservation.createdAt = LocalDateTime.now().toString();
        try {
            ApiClient.post("/inventoryReservations", gson.toJson(reservation));
        } catch (Exception writeFailure) {
            try {
                Reservation persisted = findReservation(requestId);
                if (persisted != null && "ACTIVE".equalsIgnoreCase(persisted.status)
                        && normalized.equals(persisted.quantities)) return;
            } catch (Exception verificationFailure) {
                writeFailure.addSuppressed(verificationFailure);
            }
            throw writeFailure;
        }
    }

    public void releaseReservation(int requestId) throws Exception {
        withLock(() -> {
            releaseReservationLocked(requestId);
            return null;
        });
    }

    /** Must be called inside withLock. */
    public void releaseReservationLocked(int requestId) throws Exception {
        Reservation reservation = findReservation(requestId);
        if (reservation == null || !"ACTIVE".equalsIgnoreCase(reservation.status)) return;
        Map<String, Object> patch = new HashMap<>();
        patch.put("status", "RELEASED");
        patch.put("releasedAt", LocalDateTime.now().toString());
        ApiClient.patch("/inventoryReservations/" + reservation.id, gson.toJson(patch));
    }

    public AdjustmentResult adjust(Map<Integer, Integer> deltas, Integer reservationId,
            Integer restoreReservationId) throws Exception {
        return adjust("stock-" + UUID.randomUUID(), deltas, reservationId, restoreReservationId);
    }

    public AdjustmentResult adjust(String operationId, Map<Integer, Integer> deltas,
            Integer reservationId, Integer restoreReservationId) throws Exception {
        if (deltas == null || deltas.isEmpty()) return new AdjustmentResult();
        return withLock(() -> adjustLocked(operationId, deltas, reservationId, restoreReservationId));
    }

    private AdjustmentResult adjustLocked(String operationId, Map<Integer, Integer> deltas,
            Integer reservationId, Integer restoreReservationId) throws Exception {
        Map<Integer, Integer> normalized = new TreeMap<>();
        deltas.forEach((id, delta) -> {
            if (id == null || id <= 0 || delta == null) throw new IllegalArgumentException("Thay đổi tồn kho không hợp lệ.");
            if (delta != 0) normalized.merge(id, delta, Integer::sum);
        });
        normalized.entrySet().removeIf(entry -> entry.getValue() == 0);
        if (normalized.isEmpty()) return new AdjustmentResult();

        String signature = reservationId + "|" + restoreReservationId + "|" + normalized;
        Operation previousOperation = findOperation(operationId);
        if (previousOperation != null) {
            if (!signature.equals(previousOperation.signature) || !"SUCCESS".equalsIgnoreCase(previousOperation.status))
                throw new IllegalStateException("Mã giao dịch tồn kho đã tồn tại với nội dung khác.");
            AdjustmentResult duplicate = new AdjustmentResult();
            duplicate.operationId = operationId;
            duplicate.changes = previousOperation.changes == null ? List.of() : previousOperation.changes;
            duplicate.duplicate = true;
            return duplicate;
        }

        Reservation consumed = reservationId == null ? null : findReservation(reservationId);
        if (consumed != null && !"ACTIVE".equalsIgnoreCase(consumed.status))
            throw new IllegalStateException("Giữ chỗ tồn kho của yêu cầu không còn hiệu lực.");
        if (consumed != null && !matchesReservation(normalized, consumed.quantities, -1))
            throw new IllegalStateException("Số món trong order không khớp lượng tồn kho đã giữ chỗ.");

        Reservation reactivated = restoreReservationId == null ? null : findReservation(restoreReservationId);
        if (reactivated != null && "ACTIVE".equalsIgnoreCase(reactivated.status))
            throw new IllegalStateException("Order này đang giữ chỗ, không thể hoàn kho lần nữa.");
        if (reactivated != null && !matchesReservation(normalized, reactivated.quantities, 1))
            throw new IllegalStateException("Số món hoàn kho không khớp lượng giữ chỗ ban đầu.");

        Map<Integer, Integer> held = heldQuantities(consumed == null ? null : reservationId);
        List<Drink> drinks = new DrinkService().getAll();
        Map<Integer, Drink> byId = drinks.stream().collect(Collectors.toMap(Drink::getId, drink -> drink));
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : normalized.entrySet()) {
            Drink drink = byId.get(entry.getKey());
            if (drink == null) throw new IllegalStateException("Không tìm thấy đồ uống mã " + entry.getKey());
            int previous = drink.getQuantity();
            int next = previous + entry.getValue();
            int holdAfter = held.getOrDefault(entry.getKey(), 0)
                    + (reactivated == null ? 0 : reactivated.quantities.getOrDefault(String.valueOf(entry.getKey()), 0));
            if (next < 0) throw new IllegalStateException(drink.getName() + " chỉ còn " + previous
                    + " cốc trong kho, không đủ cho order này.");
            if (next < holdAfter) throw new IllegalStateException(drink.getName()
                    + " không đủ tồn kho khả dụng vì đã có yêu cầu khác giữ chỗ.");
            Change change = new Change();
            change.drinkId = drink.getId();
            change.delta = entry.getValue();
            change.previousQuantity = previous;
            change.newQuantity = next;
            change.drinkName = drink.getName();
            changes.add(change);
        }

        List<Change> appliedStock = new ArrayList<>();
        boolean reservationChanged = false;
        try {
            for (Change change : changes) {
                ApiClient.patch("/drinks/" + change.drinkId,
                        gson.toJson(Map.of("quantity", change.newQuantity)));
                appliedStock.add(change);
            }
            if (consumed != null) {
                patchReservation(consumed.id, "CONSUMED", "consumedAt");
                reservationChanged = true;
            }
            if (reactivated != null) {
                patchReservation(reactivated.id, "ACTIVE", "reactivatedAt");
                reservationChanged = true;
            }
            Operation operation = new Operation();
            operation.id = operationId;
            operation.operationId = operationId;
            operation.signature = signature;
            operation.status = "SUCCESS";
            operation.appliedAt = LocalDateTime.now().toString();
            operation.reservationId = reservationId;
            operation.restoreReservationId = restoreReservationId;
            operation.changes = changes;
            try {
                ApiClient.post("/inventoryOperations", gson.toJson(operation));
            } catch (Exception writeFailure) {
                Operation persisted = findOperation(operationId);
                if (persisted == null || !signature.equals(persisted.signature)) throw writeFailure;
            }
        } catch (Exception failure) {
            if (reservationChanged) {
                try {
                    if (consumed != null) patchReservation(consumed.id, consumed.status, "releasedAt");
                    if (reactivated != null) patchReservation(reactivated.id, reactivated.status, "releasedAt");
                } catch (Exception rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            for (int index = appliedStock.size() - 1; index >= 0; index--) {
                Change change = appliedStock.get(index);
                try {
                    ApiClient.patch("/drinks/" + change.drinkId,
                            gson.toJson(Map.of("quantity", change.previousQuantity)));
                } catch (Exception rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            throw failure;
        }

        AdjustmentResult result = new AdjustmentResult();
        result.operationId = operationId;
        result.changes = changes;
        return result;
    }

    private void patchReservation(int id, String status, String timestampField) throws Exception {
        ApiClient.patch("/inventoryReservations/" + id, gson.toJson(Map.of(
                "status", status, timestampField, LocalDateTime.now().toString())));
    }

    private Map<Integer, Integer> heldQuantities(Integer excludeRequestId) throws Exception {
        Map<Integer, Integer> held = new HashMap<>();
        for (Reservation reservation : getReservations()) {
            if (!"ACTIVE".equalsIgnoreCase(reservation.status)
                    || (excludeRequestId != null && reservation.requestId == excludeRequestId)) continue;
            if (reservation.quantities == null) continue;
            reservation.quantities.forEach((drinkId, quantity) -> {
                try {
                    int parsedId = Integer.parseInt(drinkId);
                    if (quantity != null && quantity > 0) held.merge(parsedId, quantity, Integer::sum);
                } catch (NumberFormatException ignored) { }
            });
        }
        return held;
    }

    private List<Reservation> getReservations() throws Exception {
        Type type = new TypeToken<List<Reservation>>() {}.getType();
        List<Reservation> reservations = gson.fromJson(ApiClient.get("/inventoryReservations"), type);
        return reservations == null ? List.of() : reservations.stream().filter(java.util.Objects::nonNull).toList();
    }

    private Reservation findReservation(int requestId) throws Exception {
        return getReservations().stream().filter(row -> row.requestId == requestId || row.id == requestId)
                .findFirst().orElse(null);
    }

    private List<Operation> getOperations() throws Exception {
        Type type = new TypeToken<List<Operation>>() {}.getType();
        List<Operation> operations = gson.fromJson(ApiClient.get("/inventoryOperations"), type);
        return operations == null ? List.of() : operations.stream().filter(java.util.Objects::nonNull).toList();
    }

    private Operation findOperation(String operationId) throws Exception {
        if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("Thiếu mã giao dịch tồn kho.");
        return getOperations().stream().filter(row -> operationId.equals(row.operationId)
                || operationId.equals(row.id)).findFirst().orElse(null);
    }

    private Map<String, Integer> toStringKeys(Map<Integer, Integer> source) {
        Map<String, Integer> result = new TreeMap<>();
        source.forEach((id, quantity) -> result.put(String.valueOf(id), quantity));
        return result;
    }

    private boolean matchesReservation(Map<Integer, Integer> deltas, Map<String, Integer> quantities, int sign) {
        if (quantities == null) return false;
        Map<Integer, Integer> expected = new TreeMap<>();
        quantities.forEach((id, quantity) -> {
            try { expected.put(Integer.parseInt(id), sign * quantity); }
            catch (NumberFormatException ignored) { }
        });
        return new TreeMap<>(deltas).equals(expected);
    }
}
