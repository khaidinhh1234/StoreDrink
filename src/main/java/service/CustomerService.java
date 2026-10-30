package service;

import java.lang.reflect.Type;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import model.Customer;
import utils.ApiClient;

public class CustomerService {
    private final Gson gson = new Gson();

    public List<Customer> getAll() throws Exception {
        Type type = new TypeToken<List<Customer>>() { }.getType();
        List<Customer> customers = gson.fromJson(ApiClient.get("/customers"), type);
        return customers == null ? List.of() : customers;
    }

    public void create(Customer customer) throws Exception {
        customer.setId(getAll().stream().mapToInt(Customer::getId).max().orElse(0) + 1);
        ApiClient.post("/customers", gson.toJson(customer));
    }

    public void update(Customer customer) throws Exception {
        ApiClient.put("/customers/" + customer.getId(), gson.toJson(customer));
    }

    public void delete(int customerId) throws Exception {
        ApiClient.delete("/customers/" + customerId);
    }
}
