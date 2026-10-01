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
        ApiClient.put("/drinks/" + drink.getId(), gson.toJson(drink));
    }

    public void create(Drink drink) throws Exception {
        ApiClient.post("/drinks", gson.toJson(drink));
    }

    public void delete(int id) throws Exception {
        ApiClient.delete("/drinks/" + id);
    }
}
