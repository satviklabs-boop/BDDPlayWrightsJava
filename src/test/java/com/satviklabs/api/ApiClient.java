package com.satviklabs.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import com.satviklabs.config.ConfigLoader;

import java.util.HashMap;
import java.util.Map;

/**
 * Thin HTTP wrapper around Playwright's APIRequestContext.
 *
 * It performs real HTTP calls with no browser overhead, sends the API key
 * header on every request, and exposes the raw status/body so step definitions
 * can assert on them.
 */
public class ApiClient {

    private final APIRequestContext context;

    public ApiClient(Playwright playwright) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        String apiKey = ConfigLoader.apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("x-api-key", apiKey);
        }

        this.context = playwright.request().newContext(
                new APIRequest.NewContextOptions()
                        .setBaseURL(ConfigLoader.apiBaseUrl())
                        .setExtraHTTPHeaders(headers));
    }

    // ------------------------------------------------------------- verbs

    public APIResponse get(String path) {
        return context.get(path);
    }

    public APIResponse post(String path, Object payload) {
        return context.post(path, RequestOptions.create().setData(payload));
    }

    public APIResponse put(String path, Object payload) {
        return context.put(path, RequestOptions.create().setData(payload));
    }

    public APIResponse patch(String path, Object payload) {
        return context.patch(path, RequestOptions.create().setData(payload));
    }

    public APIResponse delete(String path) {
        return context.delete(path);
    }

    // ------------------------------------------------------------ helpers

    /** Parses the response body as a JSON object. Throws if it is not valid JSON. */
    public JsonObject bodyAsJson(APIResponse response) {
        String text = response.text();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException(
                    "Response body was empty (HTTP " + response.status() + ")");
        }
        return JsonParser.parseString(text).getAsJsonObject();
    }

    public String bodyAsText(APIResponse response) {
        return response.text();
    }

    /** Case-insensitive header lookup, since servers vary in casing. */
    public String header(APIResponse response, String name) {
        return response.headers().get(name.toLowerCase());
    }

    public void dispose() {
        context.dispose();
    }
}
