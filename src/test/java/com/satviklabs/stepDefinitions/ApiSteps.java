package com.satviklabs.stepDefinitions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.playwright.APIResponse;
import com.satviklabs.baseClasses.ConfigLoader;
import com.satviklabs.core.Providers.ApiClient;
import com.satviklabs.core.Providers.Hooks;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NOTE ON RATE LIMITS
 *
 * The default API target (reqres.in) allows 40 requests/day per IP on its free
 * anonymous tier. When that limit is hit the API replies HTTP 429 with a
 * {@code rate_limit_exceeded} body. Rather than reporting a wall of confusing
 * assertion failures, {@link #theResponseStatusShouldBe(int)} recognises that
 * response and fails with an explicit, actionable message. A fresh API key
 * (see API_KEY in .env.example) raises the limit.
 */

import java.util.HashMap;
import java.util.Map;

/**
 * API step definitions backed by Playwright's APIRequestContext.
 *
 * The last response is held in scenario state so Given/When sets it up and
 * Then asserts on it.
 */
public class ApiSteps {

    private static final Logger log = LoggerFactory.getLogger(ApiSteps.class);

    private final Hooks hooks;
    private APIResponse lastResponse;

    public ApiSteps(Hooks hooks) {
        this.hooks = hooks;
    }

    private ApiClient client() {
        return hooks.apiClient();
    }

    // ---------------------------------------------------------------- When

    @When("I POST a login request with a valid email and password")
    public void iPostALoginRequestWithValidCredentials() {
        Map<String, String> payload = new HashMap<>();
        payload.put("email", ConfigLoader.apiUsername());
        payload.put("password", ConfigLoader.apiPassword());
        lastResponse = client().post("/api/login", payload);
        logResponse();
    }

    @When("I POST a login request with only an email")
    public void iPostALoginRequestWithOnlyEmail() {
        Map<String, String> payload = new HashMap<>();
        payload.put("email", ConfigLoader.apiUsername());
        lastResponse = client().post("/api/login", payload);
        logResponse();
    }

    @When("I GET the {string} endpoint")
    public void iGetTheEndpoint(String path) {
        lastResponse = client().get(path);
        logResponse();
    }

    @When("I POST to the {string} endpoint with name {string} and job {string}")
    public void iPostToTheEndpointWithNameAndJob(String path, String name, String job) {
        Map<String, String> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("job", job);
        lastResponse = client().post(path, payload);
        logResponse();
    }

    @When("I POST a create request with name {string} and job {string}")
    public void iPostACreateRequest(String name, String job) {
        Map<String, String> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("job", job);
        lastResponse = client().post("/api/users", payload);
        logResponse();
    }

    @When("I PUT an update request for {int} with name {string} and job {string}")
    public void iPutAnUpdateRequest(int id, String name, String job) {
        Map<String, String> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("job", job);
        lastResponse = client().put("/posts/" + id, payload);
        logResponse();
    }

    /** Path-explicit variant, so the step is reusable across any endpoint. */
    @When("I PUT to the {string} endpoint with name {string} and job {string}")
    public void iPutToTheEndpointWithNameAndJob(String path, String name, String job) {
        Map<String, String> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("job", job);
        lastResponse = client().put(path, payload);
        logResponse();
    }

    @When("I PATCH the {string} endpoint with name {string}")
    public void iPatchTheEndpointWithName(String path, String name) {
        Map<String, String> payload = new HashMap<>();
        payload.put("name", name);
        lastResponse = client().patch(path, payload);
        logResponse();
    }

    @When("I DELETE the {string} endpoint")
    public void iDeleteTheEndpoint(String path) {
        lastResponse = client().delete(path);
        logResponse();
    }

    // ---------------------------------------------------------------- Then

    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(int expected) {
        APIResponse response = requireResponse();
        int actual = response.status();

        if (actual == 429) {
            throw new AssertionError(
                    "The API returned HTTP 429 (rate limited) instead of " + expected + ".\n"
                    + "This is an environment limitation, not a defect in the test.\n"
                    + "The default target (reqres.in) allows 40 anonymous requests per day.\n"
                    + "Fix: set a fresh API_KEY in .env, or wait for the daily reset.\n"
                    + "Server said: " + safeBody());
        }

        Assertions.assertThat(actual)
                .as("Unexpected HTTP status. Body was: %s", safeBody())
                .isEqualTo(expected);
    }

    @Then("the response body should contain a {string} field")
    public void theResponseBodyShouldContainField(String field) {
        JsonObject json = client().bodyAsJson(requireResponse());
        Assertions.assertThat(json.has(field))
                .as("Expected response to contain field '%s'. Body: %s", field, safeBody())
                .isTrue();
        Assertions.assertThat(json.get(field).isJsonNull())
                .as("Field '%s' should carry a value, not null", field)
                .isFalse();
    }

    @Then("the response body should contain an {string} field")
    public void theResponseBodyShouldContainAnField(String field) {
        theResponseBodyShouldContainField(field);
    }

    @Then("the response body should not contain a {string} field")
    public void theResponseBodyShouldNotContainField(String field) {
        JsonObject json = client().bodyAsJson(requireResponse());
        Assertions.assertThat(json.has(field))
                .as("Response should not echo the '%s' field. Body: %s", field, safeBody())
                .isFalse();
    }

    /**
     * Asserts a non-empty JSON array is present. Handles both API conventions:
     * a wrapped object ({\code {"data":[...]}}) and a bare array root
     * ({\code [{...},{...}]}), since both are common and the Gherkin step should
     * not have to care which one the service uses.
     */
    @Then("the response should contain a non-empty {string} array")
    public void theResponseShouldContainNonEmptyArray(String field) {
        JsonElement root = client().bodyAsJsonElement(requireResponse());
        JsonArray array;

        if (root.isJsonArray()) {
            // Bare array at the root: the whole payload is the collection.
            array = root.getAsJsonArray();
        } else {
            JsonObject json = root.getAsJsonObject();
            Assertions.assertThat(json.has(field))
                    .as("Expected an array field '%s'. Body: %s", field, safeBody())
                    .isTrue();
            Assertions.assertThat(json.get(field).isJsonArray())
                    .as("Field '%s' should be a JSON array. Body: %s", field, safeBody())
                    .isTrue();
            array = json.getAsJsonArray(field);
        }

        Assertions.assertThat(array.size())
                .as("Array '%s' should contain at least one element", field)
                .isGreaterThan(0);
    }

    /** Uses dot notation, e.g. {@code data.id}, to walk into nested objects. */
    @Then("the response body field {string} should equal {string}")
    public void theResponseBodyFieldShouldEqual(String path, String expected) {
        JsonElement value = readPath(client().bodyAsJson(requireResponse()), path);
        Assertions.assertThat(value).as("Field '%s' missing from response: %s", path, safeBody())
                .isNotNull();
        Assertions.assertThat(value.getAsString())
                .as("Field '%s' had the wrong value", path)
                .isEqualTo(expected);
    }

    /**
     * Handles APIs that return a bare JSON array at the root, where there is no
     * wrapper field to inspect. Asserts the array is present and non-empty.
     */
    @Then("the response should be a non-empty JSON array")
    public void theResponseShouldBeANonEmptyJsonArray() {
        JsonElement root = client().bodyAsJsonElement(requireResponse());
        Assertions.assertThat(root.isJsonArray())
                .as("Expected the response root to be a JSON array. Body: %s", safeBody())
                .isTrue();
        Assertions.assertThat(root.getAsJsonArray().size())
                .as("Expected the JSON array to contain at least one element")
                .isGreaterThan(0);
    }

    /**
     * Asserts the body carries no data. Servers differ here: some return a
     * genuinely empty body, others an empty JSON object such as {@code {}}.
     * Both mean "no payload", so both are accepted.
     */
    @Then("the response body should be empty")
    public void theResponseBodyShouldBeEmpty() {
        String body = requireResponse().text();
        String trimmed = body == null ? "" : body.trim();
        Assertions.assertThat(trimmed)
                .as("Expected an empty response body. Body was: %s", safeBody())
                .satisfiesAnyOf(
                        value -> Assertions.assertThat((String) value).isEmpty(),
                        value -> Assertions.assertThat((String) value).isEqualTo("{}"));
    }

    /**
     * Accepts a body that is empty, whitespace, or an empty JSON object.
     * Reqres returns {@code {}} rather than a truly empty body for some 404s,
     * so asserting on a strict empty string would be testing the wrong thing.
     */
    @Then("the response body should be empty or an empty JSON object")
    public void theResponseBodyShouldBeEmptyOrEmptyJsonObject() {
        String body = requireResponse().text();
        String trimmed = body == null ? "" : body.trim();
        Assertions.assertThat(trimmed)
                .as("Expected no resource payload. Body was: %s", trimmed)
                .satisfiesAnyOf(
                        value -> Assertions.assertThat(value).isEmpty(),
                        value -> Assertions.assertThat(value).isEqualTo("{}"));
    }

    @Then("the response content type should be {string}")
    public void theResponseContentTypeShouldBe(String expected) {
        String contentType = client().header(requireResponse(), "content-type");
        Assertions.assertThat(contentType)
                .as("Missing or unexpected Content-Type header")
                .isNotNull()
                .contains(expected);
    }

    // ------------------------------------------------------------- helpers

    private APIResponse requireResponse() {
        Assertions.assertThat(lastResponse)
                .as("No HTTP call has been made yet in this scenario")
                .isNotNull();
        return lastResponse;
    }

    private JsonElement readPath(JsonObject root, String dottedPath) {
        JsonElement current = root;
        for (String segment : dottedPath.split("\\.")) {
            if (current == null || current.isJsonNull() || !current.isJsonObject()) {
                return null;
            }
            JsonObject obj = current.getAsJsonObject();
            if (!obj.has(segment)) {
                return null;
            }
            current = obj.get(segment);
        }
        return current;
    }

    private String safeBody() {
        try {
            return lastResponse == null ? "<no response>" : lastResponse.text();
        } catch (Exception e) {
            return "<unreadable>";
        }
    }

    private void logResponse() {
        if (lastResponse != null) {
            log.info("HTTP {} -> {}", lastResponse.url(), lastResponse.status());
        }
    }
}
