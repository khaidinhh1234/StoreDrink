package service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import model.Drink;
import utils.ApiClient;

import java.lang.reflect.Type;
import java.util.List;

public class DrinkService {
    private final Gson gson = new Gson();

    public List<Drink> getAll() throws Exception {

        String json = ApiClient.get("/drinks");
        Type type = new TypeToken<List<Drink>>() {
        }.getType();

        return gson.fromJson(json, type);
    }

    public void update(Drink drink) throws Exception {
        try {
            ApiClient.put("/drinks/" + drink.getId(), gson.toJson(drink));
        } catch (Exception writeFailure) {
            try {
                Drink persisted = gson.fromJson(ApiClient.get("/drinks/" + drink.getId()), Drink.class);
                if (persisted != null && gson.toJsonTree(persisted).equals(gson.toJsonTree(drink))) return;
            } catch (Exception verifyFailure) {
                writeFailure.addSuppressed(verifyFailure);
            }
            throw writeFailure;
        }
    }

    public Drink create(Drink drink) throws Exception {
        java.util.Set<Integer> beforeIds = getAll().stream().map(Drink::getId)
                .collect(java.util.stream.Collectors.toSet());
        try {
            String response = ApiClient.post("/drinks", gson.toJson(drink));
            Drink created = gson.fromJson(response, Drink.class);
            if (created != null && created.getId() > 0) return created;
        } catch (Exception writeFailure) {
            try {
                Drink created = getAll().stream().filter(candidate -> !beforeIds.contains(candidate.getId()))
                        .filter(candidate -> sameNewDrink(candidate, drink))
                        .max(java.util.Comparator.comparingInt(Drink::getId)).orElse(null);
                if (created != null) return created;
            } catch (Exception verifyFailure) {
                writeFailure.addSuppressed(verifyFailure);
            }
            throw writeFailure;
        }
        Drink created = getAll().stream().filter(candidate -> !beforeIds.contains(candidate.getId()))
                .filter(candidate -> sameNewDrink(candidate, drink))
                .max(java.util.Comparator.comparingInt(Drink::getId)).orElse(null);
        if (created != null) return created;
        throw new IllegalStateException("Máy chủ không trả về mã đồ uống vừa tạo và không tìm thấy bản ghi đã lưu.");
    }

    private boolean sameNewDrink(Drink a, Drink b) {
        return a != null && b != null && java.util.Objects.equals(a.getName(), b.getName())
                && Math.abs(a.getPrice() - b.getPrice()) < 0.001
                && a.getQuantity() == b.getQuantity()
                && a.getCategoryId() == b.getCategoryId()
                && java.util.Objects.equals(a.getImage(), b.getImage())
                && java.util.Objects.equals(a.getDescription(), b.getDescription());
    }

}
