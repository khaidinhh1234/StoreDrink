package service;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import model.Topping;
import utils.ApiClient;

public class ToppingService {
    private final Gson gson = new Gson();

    private static class Link {
        int id;
        int drinkId;
        int toppingId;
    }

    public List<Topping> getActive() throws Exception {
        return getAll().stream().filter(Topping::isActive).toList();
    }

    public List<Topping> getAll() throws Exception {
        Type type = new TypeToken<List<Topping>>() {}.getType();
        List<Topping> toppings = gson.fromJson(ApiClient.get("/toppings"), type);
        return toppings == null ? List.of() : toppings;
    }

    public Set<Integer> getIdsForDrink(int drinkId) throws Exception {
        Set<Integer> ids = new HashSet<>();
        for (Link link : getLinks(drinkId)) ids.add(link.toppingId);
        return ids;
    }

    public List<Topping> getForDrink(int drinkId) throws Exception {
        Set<Integer> ids = getIdsForDrink(drinkId);
        return getAll().stream().filter(topping -> ids.contains(topping.getId())).toList();
    }

    public void setForDrink(int drinkId, List<Topping> selected) throws Exception {
        if (drinkId <= 0) throw new IllegalArgumentException("Mã đồ uống không hợp lệ.");
        List<Topping> requested = selected == null ? List.of() : List.copyOf(selected);
        if (requested.size() > 3) throw new IllegalArgumentException("Mỗi món chỉ được thêm tối đa 3 topping.");
        Set<String> names = new HashSet<>();
        for (Topping topping : requested) {
            String name = topping.getName() == null ? "" : topping.getName().trim();
            if (name.isBlank() || !Double.isFinite(topping.getPrice()) || topping.getPrice() < 0
                    || !names.add(name.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("Tên topping phải riêng biệt và giá topping phải hợp lệ.");
            }
        }

        List<Topping> available = getAll();
        List<Integer> requestedIds = new ArrayList<>();
        for (Topping option : requested) {
            Topping match = available.stream()
                    .filter(topping -> topping.getName().trim().equalsIgnoreCase(option.getName().trim())
                            && Math.abs(topping.getPrice() - option.getPrice()) < 0.001)
                    .findFirst().orElse(null);
            if (match != null && !match.isActive()) {
                match.setActive(true);
                ApiClient.patch("/toppings/" + match.getId(), gson.toJson(java.util.Map.of("active", true)));
            }
            if (match == null) {
                option.setActive(true);
                try {
                    String response = ApiClient.post("/toppings", gson.toJson(option));
                    match = gson.fromJson(response, Topping.class);
                } catch (Exception writeFailure) {
                    try {
                        match = findMatching(option);
                    } catch (Exception verifyFailure) {
                        writeFailure.addSuppressed(verifyFailure);
                    }
                    if (match == null) throw writeFailure;
                }
                if (match == null || match.getId() <= 0) {
                    match = findMatching(option);
                }
                if (match == null || match.getId() <= 0)
                    throw new IllegalStateException("Không lấy được mã topping vừa tạo.");
                available = new ArrayList<>(available);
                available.add(match);
            }
            requestedIds.add(match.getId());
        }

        List<Link> previous = getLinks(drinkId);
        try {
            reconcileLinks(drinkId, requestedIds);
            List<Link> savedLinks = getLinks(drinkId);
            for (Integer toppingId : requestedIds) {
                long count = savedLinks.stream().filter(link -> link.toppingId == toppingId).count();
                if (count != 1) throw new IllegalStateException(
                        "Không xác nhận được topping " + toppingId + " đã gắn đúng một lần vào đồ uống.");
            }
        } catch (Exception updateFailure) {
            try {
                reconcileLinks(drinkId, previous.stream().filter(java.util.Objects::nonNull)
                        .map(link -> link.toppingId).filter(id -> id > 0).distinct().toList());
            } catch (Exception rollbackFailure) {
                updateFailure.addSuppressed(rollbackFailure);
            }
            throw updateFailure;
        }
    }

    private List<Link> getLinks(int drinkId) throws Exception {
        Type type = new TypeToken<List<Link>>() {}.getType();
        List<Link> links = gson.fromJson(ApiClient.get("/drinkToppings?drinkId=" + drinkId), type);
        if (links == null) return new ArrayList<>();
        return links.stream().filter(java.util.Objects::nonNull).toList();
    }

    private void deleteLinks(List<Link> links) throws Exception {
        for (Link link : links) if (link != null && link.id > 0)
            ApiClient.delete("/drinkToppings/" + link.id);
    }

    private void reconcileLinks(int drinkId, List<Integer> targetIds) throws Exception {
        Set<Integer> desired = new HashSet<>(targetIds);
        Set<Integer> kept = new HashSet<>();
        List<Link> remove = new ArrayList<>();
        for (Link link : getLinks(drinkId)) {
            if (link.id <= 0 || link.toppingId <= 0 || !desired.contains(link.toppingId)
                    || !kept.add(link.toppingId)) remove.add(link);
        }
        deleteLinks(remove);
        for (Integer toppingId : desired) if (!kept.contains(toppingId)) createLink(drinkId, toppingId);
    }

    private void createLink(int drinkId, int toppingId) throws Exception {
        Exception lastFailure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            List<Link> current = getLinks(drinkId);
            if (current.stream().anyMatch(link -> link.toppingId == toppingId)) return;
            try {
                ApiClient.post("/drinkToppings", gson.toJson(java.util.Map.of(
                        "drinkId", drinkId, "toppingId", toppingId)));
            } catch (Exception writeFailure) {
                lastFailure = writeFailure;
                try {
                    if (getLinks(drinkId).stream().anyMatch(link -> link.toppingId == toppingId)) return;
                } catch (Exception verifyFailure) {
                    writeFailure.addSuppressed(verifyFailure);
                    throw writeFailure;
                }
            }
        }
        if (lastFailure != null) throw lastFailure;
        if (getLinks(drinkId).stream().noneMatch(link -> link.toppingId == toppingId))
            throw new IllegalStateException("Không xác nhận được topping đã gắn vào đồ uống.");
    }

    private Topping findMatching(Topping requested) throws Exception {
        return getAll().stream()
                .filter(topping -> topping.getName().trim().equalsIgnoreCase(requested.getName().trim())
                        && Math.abs(topping.getPrice() - requested.getPrice()) < 0.001)
                .max(java.util.Comparator.comparingInt(Topping::getId)).orElse(null);
    }
}
