package utils;

import java.net.URI;
import java.net.http.*;
import java.net.URLEncoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class ApiClient {

    private static final String BASE_URL = "http://127.0.0.1:3000";

    public static volatile HttpClient client = createClient();

    private static HttpClient createClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public static String get(String endpoint) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .GET()
                .build();

        HttpResponse<String> response = sendWithRetry(request, "GET");

        ensureSuccess(response);
        return response.body();
    }

    public static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String put(String endpoint, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = sendWithRetry(request, "PUT");

        ensureSuccess(response);
        return response.body();
    }

    public static String post(String endpoint, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        ensureSuccess(response);
        return response.body();
    }

    public static void delete(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .DELETE()
                .build();

        HttpResponse<String> response = sendWithRetry(request, "DELETE");

        ensureSuccess(response);
    }

    private static void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "API trả về mã lỗi HTTP " + response.statusCode());
        }
    }

    /** Retries idempotent requests after a dropped/stale HTTP connection. */
    private static HttpResponse<String> sendWithRetry(HttpRequest request, String method) throws Exception {
        IOException lastConnectionError = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException exception) {
                lastConnectionError = exception;
                client = createClient();
                if (attempt < 2) {
                    try {
                        Thread.sleep(150L * (attempt + 1));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Đã dừng khi kết nối API " + method, interrupted);
                    }
                }
            }
        }
        throw new IOException("Không nhận được phản hồi từ API sau 3 lần thử " + method
                + ". Hãy kiểm tra JSON Server tại http://127.0.0.1:3000.", lastConnectionError);
    }
}
