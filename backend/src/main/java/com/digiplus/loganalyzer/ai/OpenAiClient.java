package com.digiplus.loganalyzer.ai;

import com.digiplus.loganalyzer.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin client for the OpenAI Chat Completions API using the JDK HttpClient
 * (no extra dependency). Returns the assistant message content, which we ask
 * the model to format as a strict JSON object.
 */
@Component
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private final AiProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public OpenAiClient(AiProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** True when the OpenAI provider is selected and an API key is present. */
    public boolean isConfigured() {
        AiProperties.OpenAi o = props.getOpenai();
        return "openai".equalsIgnoreCase(props.getProvider())
                && o.getApiKey() != null && !o.getApiKey().isBlank();
    }

    public String model() { return props.getOpenai().getModel(); }

    /**
     * Send a system+user prompt and return the assistant's raw content string
     * (expected to be a JSON object because we set response_format=json_object).
     */
    public String chatJson(String system, String user) throws Exception {
        AiProperties.OpenAi o = props.getOpenai();

        ObjectNode body = mapper.createObjectNode();
        body.put("model", o.getModel());
        body.put("temperature", 0.2);
        ObjectNode fmt = body.putObject("response_format");
        fmt.put("type", "json_object");
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(o.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(o.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + o.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("OpenAI API returned HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
        }
        JsonNode root = mapper.readTree(resp.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new RuntimeException("OpenAI API returned no content");
        }
        return content.asText();
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
