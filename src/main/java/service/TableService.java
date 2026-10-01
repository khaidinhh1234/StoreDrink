package service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import model.StoreTable;
import utils.ApiClient;
import java.lang.reflect.Type;
import java.util.List;

public class TableService {
    private final Gson gson = new Gson();
    public List<StoreTable> getAll() throws Exception {
        Type type = new TypeToken<List<StoreTable>>() {}.getType();
        return gson.fromJson(ApiClient.get("/tables"), type);
    }
    public void update(StoreTable table) throws Exception {
        ApiClient.put("/tables/" + table.getId(), gson.toJson(table));
    }
}
