package com.nodecraft.gui.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Remote AI planner client with OpenAI-compatible and Anthropic-compatible request formats.
 */
public class AiRemotePlannerService {

    private static final int MAX_ATTEMPTS = 3;
    private static final String OPENAI_MAX_TOKENS_FIELD = "max_tokens";
    private static final String OPENAI_MAX_COMPLETION_TOKENS_FIELD = "max_completion_tokens";
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public record PlannerConfig(
            String apiBaseUrl,
            String apiKey,
            String model,
            String providerStrategy,
            String systemPrompt,
            int maxOutputTokens,
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
            boolean structuredPayload,
            int attempts
    ) {
        public static RemotePlanResult ok(String content, int statusCode, String rawResponse, int attempts) {
            return new RemotePlanResult(true, content, "", statusCode, rawResponse, "none", false, attempts);
        }

        public static RemotePlanResult okStructured(String content, int statusCode, String rawResponse, int attempts) {
            return new RemotePlanResult(true, content, "", statusCode, rawResponse, "none", true, attempts);
        }

        public static RemotePlanResult fail(String error, int statusCode, String rawResponse, String category, int attempts) {
            return new RemotePlanResult(false, "", error, statusCode, rawResponse, category, false, attempts);
        }
    }

    public record ConversationMessage(String role, String content) {
    }

    public CompletableFuture<RemotePlanResult> requestPlanAsync(PlannerConfig config, String userPrompt) {
        List<ConversationMessage> conversation = new ArrayList<>();
        conversation.add(new ConversationMessage("user", userPrompt));
        return requestPlanAsync(config, conversation);
    }

    public CompletableFuture<RemotePlanResult> requestPlanAsync(PlannerConfig config, List<ConversationMessage> conversation) {
        if (executor.isShutdown()) {
            return CompletableFuture.completedFuture(
                    RemotePlanResult.fail("Remote planner is shut down.", -1, "", "canceled", 1)
            );
        }
        return CompletableFuture.supplyAsync(() -> requestPlan(config, conversation), executor);
    }

    public CompletableFuture<RemotePlanResult> testConnectionAsync(PlannerConfig config) {
        if (executor.isShutdown()) {
            return CompletableFuture.completedFuture(
                    RemotePlanResult.fail("Remote planner is shut down.", -1, "", "canceled", 1)
            );
        }
        return CompletableFuture.supplyAsync(() -> testConnection(config), executor);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private RemotePlanResult testConnection(PlannerConfig config) {
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

        try {
            boolean useAnthropic = shouldUseAnthropic(config, baseUrl);
            HttpResponse<String> response = useAnthropic
                    ? sendAnthropicConnectionTest(client, config, timeoutSeconds)
                    : sendOpenAIConnectionTest(client, config, timeoutSeconds);

            if (response.statusCode() >= 300) {
                return RemotePlanResult.fail(
                        "HTTP " + response.statusCode() + ": " + truncate(response.body()),
                        response.statusCode(),
                        response.body(),
                        classifyErrorCategory(response.statusCode()),
                        1
                );
            }
            return RemotePlanResult.ok("Connection test succeeded.", response.statusCode(), response.body(), 1);
        } catch (IOException e) {
            return RemotePlanResult.fail("Network error: " + e.getMessage(), -1, "", "network", 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RemotePlanResult.fail("Connection test was canceled.", -1, "", "canceled", 1);
        } catch (Exception e) {
            return RemotePlanResult.fail("Connection test failed: " + e.getMessage(), -1, "", "unknown", 1);
        }
    }

    private HttpResponse<String> sendOpenAIConnectionTest(
            HttpClient client,
            PlannerConfig config,
            int timeoutSeconds
    ) throws IOException, InterruptedException {
        String endpoint = normalizeEndpoint(config.apiBaseUrl(), "/chat/completions");

        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.addProperty("temperature", 0.0);
        body.addProperty(OPENAI_MAX_TOKENS_FIELD, 1);

        JsonArray messages = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", "ping");
        messages.add(user);
        body.add("messages", messages);

        HttpResponse<String> response = sendOpenAIRequest(client, endpoint, config.apiKey(), body, timeoutSeconds);
        if (response.statusCode() == 400 && isOpenAITokenFieldCompatibilityError(response.body())) {
            body.remove(OPENAI_MAX_TOKENS_FIELD);
            body.addProperty(OPENAI_MAX_COMPLETION_TOKENS_FIELD, 1);
            return sendOpenAIRequest(client, endpoint, config.apiKey(), body, timeoutSeconds);
        }
        return response;
    }

    private HttpResponse<String> sendAnthropicConnectionTest(
            HttpClient client,
            PlannerConfig config,
            int timeoutSeconds
    ) throws IOException, InterruptedException {
        String endpoint = normalizeAnthropicEndpoint(config.apiBaseUrl());

        JsonObject body = getJsonObject(config);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("x-api-key", config.apiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static @NonNull JsonObject getJsonObject(PlannerConfig config) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.addProperty("max_tokens", 8);

        JsonArray messages = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("role", "user");
        JsonArray content = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", "ping");
        content.add(textPart);
        item.add("content", content);
        messages.add(item);
        body.add("messages", messages);
        return body;
    }

    private RemotePlanResult requestPlan(PlannerConfig config, List<ConversationMessage> conversation) {
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

        List<ConversationMessage> normalizedConversation = normalizeConversation(conversation);
        if (normalizedConversation.isEmpty()) {
            return RemotePlanResult.fail("Conversation payload is empty.", -1, "", "request", 1);
        }

        String baseUrl = config.apiBaseUrl().trim();
        int timeoutSeconds = Math.max(5, Math.min(600, config.timeoutSeconds()));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 30)))
                .build();

        RemotePlanResult lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                boolean useAnthropic = shouldUseAnthropic(config, baseUrl);
                RemotePlanResult result = useAnthropic
                        ? requestAnthropic(client, config, normalizedConversation, timeoutSeconds, attempt)
                        : requestOpenAICompatible(client, config, normalizedConversation, timeoutSeconds, attempt);

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

        private RemotePlanResult requestOpenAICompatible(
            HttpClient client,
            PlannerConfig config,
            List<ConversationMessage> conversation,
            int timeoutSeconds,
            int attempt
        )
            throws IOException, InterruptedException {
        String endpoint = normalizeEndpoint(config.apiBaseUrl(), "/chat/completions");

        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.addProperty("temperature", 0.1);
        int maxTokens = resolveOpenAICompatibleMaxTokens(config, conversation);
        body.addProperty(OPENAI_MAX_TOKENS_FIELD, maxTokens);

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", config.systemPrompt());
        messages.add(system);

        for (ConversationMessage message : conversation) {
            JsonObject item = new JsonObject();
            item.addProperty("role", normalizeRoleForOpenAI(message.role()));
            item.addProperty("content", nullToEmpty(message.content()));
            messages.add(item);
        }

        body.add("messages", messages);

        HttpResponse<String> response = sendOpenAIRequest(client, endpoint, config.apiKey(), body, timeoutSeconds);
        if (response.statusCode() >= 300) {
            if (response.statusCode() == 400 && isOpenAITokenFieldCompatibilityError(response.body())) {
            body.remove(OPENAI_MAX_TOKENS_FIELD);
            body.addProperty(OPENAI_MAX_COMPLETION_TOKENS_FIELD, maxTokens);
            HttpResponse<String> fallbackResponse = sendOpenAIRequest(client, endpoint, config.apiKey(), body, timeoutSeconds);
            if (fallbackResponse.statusCode() < 300) {
                String fallbackContent = extractOpenAIContent(fallbackResponse.body());
                if (!isBlank(fallbackContent)) {
                return RemotePlanResult.ok(fallbackContent, fallbackResponse.statusCode(), fallbackResponse.body(), attempt);
                }
                return RemotePlanResult.fail(
                    "OpenAI-compatible fallback response did not include message content.",
                    fallbackResponse.statusCode(),
                    fallbackResponse.body(),
                    "response-format",
                    attempt
                );
            }

            return RemotePlanResult.fail(
                "HTTP " + fallbackResponse.statusCode() + ": " + truncate(fallbackResponse.body()),
                fallbackResponse.statusCode(),
                fallbackResponse.body(),
                classifyErrorCategory(fallbackResponse.statusCode()),
                attempt
            );
            }

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

    private HttpResponse<String> sendOpenAIRequest(
            HttpClient client,
            String endpoint,
            String apiKey,
            JsonObject body,
            int timeoutSeconds
    ) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private boolean isOpenAITokenFieldCompatibilityError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        String body = responseBody.toLowerCase(Locale.ROOT);
        boolean mentionsTokenField = body.contains("max_tokens") || body.contains("max_completion_tokens");
        boolean indicatesInvalidField = body.contains("unknown")
                || body.contains("unsupported")
                || body.contains("invalid")
                || body.contains("not allowed")
                || body.contains("unrecognized");
        return mentionsTokenField && indicatesInvalidField;
    }

        private RemotePlanResult requestAnthropic(
            HttpClient client,
            PlannerConfig config,
            List<ConversationMessage> conversation,
            int timeoutSeconds,
            int attempt
        )
            throws IOException, InterruptedException {
        String endpoint = normalizeAnthropicEndpoint(config.apiBaseUrl());

        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.addProperty("temperature", 0.1);
        body.addProperty("max_tokens", resolveAnthropicMaxTokens(config, conversation));
        body.addProperty("system", config.systemPrompt());

        JsonArray tools = new JsonArray();
        tools.add(buildNodeGraphToolSchema());
        body.add("tools", tools);

        JsonObject toolChoice = new JsonObject();
        toolChoice.addProperty("type", "tool");
        toolChoice.addProperty("name", "create_node_graph");
        body.add("tool_choice", toolChoice);

        JsonArray messages = new JsonArray();
        for (ConversationMessage message : conversation) {
            // Anthropic tool-use protocol can reject follow-up turns if prior assistant
            // tool_use/tool_result blocks are not replayed in strict order.
            // Short-term safety: keep only user history in Anthropic requests.
            if (!"user".equals(normalizeRoleForOpenAI(message.role()))) {
                continue;
            }

            JsonObject item = new JsonObject();
            item.addProperty("role", normalizeRoleForAnthropic(message.role()));

            JsonArray content = new JsonArray();
            JsonObject textPart = new JsonObject();
            textPart.addProperty("type", "text");
            textPart.addProperty("text", nullToEmpty(message.content()));
            content.add(textPart);
            item.add("content", content);

            messages.add(item);
        }
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

        String toolInput = extractAnthropicToolInput(response.body());
        if (!isBlank(toolInput)) {
            return RemotePlanResult.okStructured(toolInput, response.statusCode(), response.body(), attempt);
        }

        String result = toolInput;
        if (isBlank(result)) {
            // Fallback for legacy providers or non-tool responses.
            result = extractAnthropicContent(response.body());
        }
        if (isBlank(result)) {
            return RemotePlanResult.fail(
                    "Anthropic response did not include tool input or text content.",
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

    private String extractAnthropicToolInput(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray content = root.getAsJsonArray("content");
            if (content == null || content.isEmpty()) {
                return "";
            }

            for (int i = 0; i < content.size(); i++) {
                JsonObject part = content.get(i).getAsJsonObject();
                if (part.has("type")
                        && "tool_use".equals(part.get("type").getAsString())
                        && part.has("name")
                        && "create_node_graph".equals(part.get("name").getAsString())
                        && part.has("input")) {
                    return part.get("input").toString();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private JsonObject buildNodeGraphToolSchema() {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", "create_node_graph");
        tool.addProperty("description", "Create a node graph from user's description");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject description = new JsonObject();
        description.addProperty("type", "string");
        description.addProperty("description", "Short summary of the planned graph.");
        properties.add("description", description);

        JsonObject nodes = getJsonObject();
        properties.add("nodes", nodes);

        JsonObject connections = getConnections();
        properties.add("connections", connections);

        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("nodes");
        required.add("connections");
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);

        tool.add("input_schema", schema);
        return tool;
    }

    private static @NonNull JsonObject getConnections() {
        JsonObject endpoint = getEndpoint();

        JsonObject connectionItem = new JsonObject();
        connectionItem.addProperty("type", "object");
        JsonObject connectionProps = new JsonObject();
        connectionProps.add("from", endpoint);
        connectionProps.add("to", endpoint);
        connectionItem.add("properties", connectionProps);
        JsonArray connectionRequired = new JsonArray();
        connectionRequired.add("from");
        connectionRequired.add("to");
        connectionItem.add("required", connectionRequired);
        connectionItem.addProperty("additionalProperties", false);

        JsonObject connections = new JsonObject();
        connections.addProperty("type", "array");
        connections.add("items", connectionItem);
        return connections;
    }

    private static @NonNull JsonObject getEndpoint() {
        JsonObject endpoint = new JsonObject();
        endpoint.addProperty("type", "object");
        JsonObject endpointProps = new JsonObject();
        JsonObject endpointNodeId = new JsonObject();
        endpointNodeId.addProperty("type", "string");
        JsonObject endpointPort = new JsonObject();
        endpointPort.addProperty("type", "string");
        endpointProps.add("nodeId", endpointNodeId);
        endpointProps.add("port", endpointPort);
        endpoint.add("properties", endpointProps);
        JsonArray endpointRequired = new JsonArray();
        endpointRequired.add("nodeId");
        endpointRequired.add("port");
        endpoint.add("required", endpointRequired);
        endpoint.addProperty("additionalProperties", false);
        return endpoint;
    }

    private static @NonNull JsonObject getJsonObject() {
        JsonObject nodeItem = new JsonObject();
        nodeItem.addProperty("type", "object");
        JsonObject nodeProperties = getNodeProperties();
        nodeItem.add("properties", nodeProperties);
        JsonArray nodeRequired = new JsonArray();
        nodeRequired.add("id");
        nodeRequired.add("type");
        nodeItem.add("required", nodeRequired);
        nodeItem.addProperty("additionalProperties", false);

        JsonObject nodes = new JsonObject();
        nodes.addProperty("type", "array");
        nodes.add("items", nodeItem);
        return nodes;
    }

    private static @NonNull JsonObject getNodeProperties() {
        JsonObject nodeProperties = new JsonObject();
        JsonObject nodeId = new JsonObject();
        nodeId.addProperty("type", "string");
        JsonObject nodeType = new JsonObject();
        nodeType.addProperty("type", "string");
        JsonObject nodeParams = new JsonObject();
        nodeParams.addProperty("type", "object");
        JsonObject nodePosition = getObject();

        nodeProperties.add("id", nodeId);
        nodeProperties.add("type", nodeType);
        nodeProperties.add("params", nodeParams);
        nodeProperties.add("position", nodePosition);
        return nodeProperties;
    }

    private static @NonNull JsonObject getObject() {
        JsonObject nodePosition = new JsonObject();
        nodePosition.addProperty("type", "object");
        JsonObject nodePositionProperties = new JsonObject();
        JsonObject posX = new JsonObject();
        posX.addProperty("type", "number");
        JsonObject posY = new JsonObject();
        posY.addProperty("type", "number");
        nodePositionProperties.add("x", posX);
        nodePositionProperties.add("y", posY);
        nodePosition.add("properties", nodePositionProperties);
        JsonArray nodePositionRequired = new JsonArray();
        nodePositionRequired.add("x");
        nodePositionRequired.add("y");
        nodePosition.add("required", nodePositionRequired);
        nodePosition.addProperty("additionalProperties", false);
        return nodePosition;
    }

    private boolean shouldUseAnthropic(PlannerConfig config, String baseUrl) {
        String strategy = normalizeProviderStrategy(config.providerStrategy());
        return switch (strategy) {
            case "ANTHROPIC" -> true;
            case "OPENAI_COMPAT" -> false;
            default -> isAnthropicEndpoint(baseUrl, config.model());
        };
    }

    private String normalizeProviderStrategy(String providerStrategy) {
        if (providerStrategy == null || providerStrategy.isBlank()) {
            return "AUTO";
        }
        String normalized = providerStrategy.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "OPENAI_COMPAT" -> "OPENAI_COMPAT";
            case "ANTHROPIC" -> "ANTHROPIC";
            default -> "AUTO";
        };
    }

    private boolean isAnthropicEndpoint(String baseUrl, String model) {
        String normalized = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        String normalizedModel = model == null ? "" : model.toLowerCase(Locale.ROOT);

        // Prefer explicit provider signals and avoid path-based false positives
        // because many non-Anthropic gateways may expose "/messages" endpoints.
        if (normalized.contains("anthropic")) {
            return true;
        }

        return normalizedModel.startsWith("claude");
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

    private int resolveAnthropicMaxTokens(PlannerConfig config, List<ConversationMessage> conversation) {
        int configured = Math.max(512, Math.min(4096, config.maxOutputTokens()));
        int estimated = estimateRequiredTokens(conversation, config.systemPrompt());
        return Math.max(configured, estimated);
    }

    private int resolveOpenAICompatibleMaxTokens(PlannerConfig config, List<ConversationMessage> conversation) {
        int configured = Math.max(512, Math.min(4096, config.maxOutputTokens()));
        int estimated = estimateRequiredTokens(conversation, config.systemPrompt());
        return Math.max(configured, estimated);
    }

    private int estimateRequiredTokens(List<ConversationMessage> conversation, String systemPrompt) {
        int textChars = systemPrompt == null ? 0 : systemPrompt.length();
        if (conversation != null) {
            for (ConversationMessage message : conversation) {
                if (message != null && message.content() != null) {
                    textChars += message.content().length();
                }
            }
        }

        // Rough heuristic: ~4 chars/token + fixed JSON tool/schema overhead.
        int estimated = (textChars / 4) + 900;
        return Math.max(1400, Math.min(estimated, 4096));
    }

    private List<ConversationMessage> normalizeConversation(List<ConversationMessage> conversation) {
        List<ConversationMessage> normalized = new ArrayList<>();
        if (conversation == null) {
            return normalized;
        }

        for (ConversationMessage message : conversation) {
            if (message == null || isBlank(message.content())) {
                continue;
            }
            normalized.add(new ConversationMessage(normalizeRoleForOpenAI(message.role()), message.content()));
        }
        return normalized;
    }

    private String normalizeRoleForOpenAI(String role) {
        if (role == null) {
            return "user";
        }
        String value = role.toLowerCase(Locale.ROOT);
        if ("assistant".equals(value) || "system".equals(value) || "user".equals(value)) {
            return value;
        }
        return "user";
    }

    private String normalizeRoleForAnthropic(String role) {
        String value = normalizeRoleForOpenAI(role);
        return "assistant".equals(value) ? "assistant" : "user";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 400 ? text.substring(0, 400) + "..." : text;
    }
}
