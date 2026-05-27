package com.nodecraft.gui.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Remote AI planner client with OpenAI-compatible and Anthropic-compatible request formats.
 */
public class AiRemotePlannerService {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final int MAX_ATTEMPTS = 3;

    public record PlannerConfig(
            String apiBaseUrl,
            String apiKey,
            String model,
            String systemPrompt,
            int timeoutSeconds
    ) {
    }

    public record RemotePlanResult(
            boolean success,
            String modelContent,
            String errorMessage,
            int statusCode,
            String rawResponse,
            String errorCategory,
            int attempts
    ) {
        public static RemotePlanResult ok(String content, int statusCode, String rawResponse, int attempts) {
            return new RemotePlanResult(true, content, "", statusCode, rawResponse, "none", attempts);
        }

        public static RemotePlanResult fail(String error, int statusCode, String rawResponse, String category, int attempts) {
            return new RemotePlanResult(false, "", error, statusCode, rawResponse, category, attempts);
        }
    }

    public CompletableFuture<RemotePlanResult> requestPlanAsync(PlannerConfig config, String userPrompt) {
        return CompletableFuture.supplyAsync(() -> requestPlan(config, userPrompt), EXECUTOR);
    }

    private RemotePlanResult requestPlan(PlannerConfig config, String userPrompt) {
        if (config == null) {
            return RemotePlanResult.fail("Remote planner config is null.", -1, "", "config", 1);
        }
        if (isBlank(config.apiBaseUrl())) {
            return RemotePlanResult.fail("API base URL is empty.", -1, "", "config", 1);
        }
        if (isBlank(config.apiKey())) {
            return RemotePlanResult.fail("API key is empty.", -1, "", "config", 1);
        }
        if (isBlank(config.model())) {
            return RemotePlanResult.fail("Model is empty.", -1, "", "config", 1);
        }

        String baseUrl = config.apiBaseUrl().trim();
        int timeoutSeconds = Math.max(5, Math.min(600, config.timeoutSeconds()));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 30)))
                .build();

        RemotePlanResult lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                RemotePlanResult result = isAnthropicEndpoint(baseUrl)
                        ? requestAnthropic(client, config, userPrompt, timeoutSeconds, attempt)
                        : requestOpenAICompatible(client, config, userPrompt, timeoutSeconds, attempt);

                if (result.success()) {
                    return result;
                }

                lastFailure = result;
                if (!isRetryable(result) || attempt >= MAX_ATTEMPTS) {
                    return result;
                }

                backoffSleep(attempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return RemotePlanResult.fail("Remote planner request was canceled.", -1, "", "canceled", attempt);
            } catch (IOException ioe) {
                lastFailure = RemotePlanResult.fail(
                        "Network error: " + ioe.getMessage(),
                        -1,
                        "",
                        "network",
                        attempt
                );
                if (attempt >= MAX_ATTEMPTS) {
                    return lastFailure;
                }
                try {
                    backoffSleep(attempt);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                return RemotePlanResult.fail("Remote planner request failed: " + e.getMessage(), -1, "", "unknown", attempt);
            }
        }

        return lastFailure != null
                ? lastFailure
                : RemotePlanResult.fail("Remote planner request failed with unknown cause.", -1, "", "unknown", MAX_ATTEMPTS);
    }

    private RemotePlanResult requestOpenAICompatible(HttpClient client, PlannerConfig config, String userPrompt, int timeoutSeconds, int attempt)
            throws IOException, InterruptedException {
        String endpoint = normalizeEndpoint(config.apiBaseUrl(), "/chat/completions");

        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.addProperty("temperature", 0.1);

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", config.systemPrompt());
        messages.add(system);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userPrompt);
        messages.add(user);

        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            return RemotePlanResult.fail(
                    "HTTP " + response.statusCode() + ": " + truncate(response.body()),
                    response.statusCode(),
                    response.body(),
                    classifyErrorCategory(response.statusCode()),
                    attempt
            );
        }

        String content = extractOpenAIContent(response.body());
        if (isBlank(content)) {
            return RemotePlanResult.fail(
                    "OpenAI-compatible response did not include message content.",
                    response.statusCode(),
                    response.body(),
                    "response-format",
                    attempt
            );
        }
        return RemotePlanResult.ok(content, response.statusCode(), response.body(), attempt);
    }

    private RemotePlanResult requestAnthropic(HttpClient client, PlannerConfig config, String userPrompt, int timeoutSeconds, int attempt)
            throws IOException, InterruptedException {
        String endpoint = normalizeAnthropicEndpoint(config.apiBaseUrl());

        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.addProperty("temperature", 0.1);
        body.addProperty("max_tokens", 1400);
        body.addProperty("system", config.systemPrompt());

        JsonArray messages = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");

        JsonArray content = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", userPrompt);
        content.add(textPart);
        user.add("content", content);

        messages.add(user);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("x-api-key", config.apiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            return RemotePlanResult.fail(
                    "HTTP " + response.statusCode() + ": " + truncate(response.body()),
                    response.statusCode(),
                    response.body(),
                    classifyErrorCategory(response.statusCode()),
                    attempt
            );
        }

        String result = extractAnthropicContent(response.body());
        if (isBlank(result)) {
            return RemotePlanResult.fail(
                    "Anthropic response did not include text content.",
                    response.statusCode(),
                    response.body(),
                    "response-format",
                    attempt
            );
        }
        return RemotePlanResult.ok(result, response.statusCode(), response.body(), attempt);
    }

    private boolean isRetryable(RemotePlanResult result) {
        if (result == null || result.success()) {
            return false;
        }
        return switch (result.errorCategory()) {
            case "network", "server", "rate-limit", "timeout" -> true;
            default -> false;
        };
    }

    private String classifyErrorCategory(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return "auth";
        }
        if (statusCode == 408) {
            return "timeout";
        }
        if (statusCode == 429) {
            return "rate-limit";
        }
        if (statusCode >= 500) {
            return "server";
        }
        if (statusCode >= 400) {
            return "request";
        }
        return "unknown";
    }

    private void backoffSleep(int attempt) throws InterruptedException {
        long delayMs = switch (attempt) {
            case 1 -> 400L;
            case 2 -> 900L;
            default -> 1600L;
        };
        Thread.sleep(delayMs);
    }

    private String extractOpenAIContent(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                return "";
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || !message.has("content")) {
                return "";
            }
            return message.get("content").getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private String extractAnthropicContent(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray content = root.getAsJsonArray("content");
            if (content == null || content.isEmpty()) {
                return "";
            }
            for (int i = 0; i < content.size(); i++) {
                JsonObject part = content.get(i).getAsJsonObject();
                if (part.has("type") && "text".equals(part.get("type").getAsString()) && part.has("text")) {
                    return part.get("text").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private boolean isAnthropicEndpoint(String baseUrl) {
        String normalized = baseUrl.toLowerCase(Locale.ROOT);
        return normalized.contains("anthropic") || normalized.endsWith("/v1/messages") || normalized.endsWith("/messages");
    }

    private String normalizeEndpoint(String baseUrl, String suffix) {
        String normalized = trimTrailingSlash(baseUrl);
        if (normalized.endsWith(suffix)) {
            return normalized;
        }
        return normalized + suffix;
    }

    private String normalizeAnthropicEndpoint(String baseUrl) {
        String normalized = trimTrailingSlash(baseUrl);
        if (normalized.endsWith("/messages") || normalized.endsWith("/v1/messages")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/messages";
        }
        return normalized + "/v1/messages";
    }

    private String trimTrailingSlash(String text) {
        if (text == null) {
            return "";
        }
        String result = text.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 400 ? text.substring(0, 400) + "..." : text;
    }
}
