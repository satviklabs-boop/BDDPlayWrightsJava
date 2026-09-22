package com.satviklabs.api;

import com.google.gson.JsonElement;
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
                        .setExtraHTTPHeaders(headers)
                        // Generous per-request budget: a slow public demo API over a
                        // shared runner should not look like a defect.
                        .setTimeout(ConfigLoader.apiTimeoutMs()));
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

    /**
     * Parses the response body as a JSON object.
     *
     * Throws if the body is not valid JSON, or if the root is a JSON array -
     * use {@link #bodyAsJsonElement} when the response may be either shape.
     */
    public JsonObject bodyAsJson(APIResponse response) {
        return bodyAsJsonElement(response).getAsJsonObject();
    }

    /**
     * Parses the response body into any JSON value: object, array, or primitive.
     *
     * Some APIs return a bare array at the root ({\code [{...},{...}]} rather than
     * {\code {"data":[...]}}), so callers that only need to inspect the payload
     * should use this instead of {@link #bodyAsJson}.
     */
    public JsonElement bodyAsJsonElement(APIResponse response) {
        String text = response.text();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException(
                    "Response body was empty (HTTP " + response.status() + ")");
        }
        try {
            return JsonParser.parseString(text);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Response body was not valid JSON (HTTP " + response.status()
                            + "): " + truncate(text), e);
        }
    }

    private String truncate(String text) {
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
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
