package service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import model.User;
import utils.ApiClient;

import java.lang.reflect.Type;
import java.util.List;

public class UserService {
    private final Gson gson = new Gson();

    public User authenticate(String username, String password) throws Exception {
        String json = ApiClient.get("/users?username=" + ApiClient.encodeQuery(username)
                + "&password=" + ApiClient.encodeQuery(password)
                + "&active=true");
        Type type = new TypeToken<List<User>>() {}.getType();
        List<User> users = gson.fromJson(json, type);

        if (users == null || users.isEmpty()) {
            return null;
        }
        return users.get(0);
    }

    public List<User> getAll() throws Exception {
        Type type = new TypeToken<List<User>>() {}.getType();
        return gson.fromJson(ApiClient.get("/users"), type);
    }

    public void update(User user) throws Exception {
        ApiClient.put("/users/" + user.getId(), gson.toJson(user));
    }
}
