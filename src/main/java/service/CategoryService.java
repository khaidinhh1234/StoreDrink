package service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import model.Category;
import utils.ApiClient;
import java.lang.reflect.Type;
import java.util.List;

public class CategoryService {
    private final Gson gson = new Gson();
    public List<Category> getAll() throws Exception {
        Type type = new TypeToken<List<Category>>() {}.getType();
        return gson.fromJson(ApiClient.get("/categories"), type);
    }
    public void create(Category category) throws Exception {
        ApiClient.post("/categories", gson.toJson(category));
    }
}
