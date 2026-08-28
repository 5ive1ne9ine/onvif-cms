package com.mage.onvifcms.mage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mage.onvifcms.api.ApiException;
import com.mage.onvifcms.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MageClient {
    private static final String SYSTEM_PROMPT = """
            你是视频监控分析器。输入是按时间顺序从同一视频流采样的连续画面。
            结合用户规则判断是否发生了值得记录的事件。只输出一个 JSON 对象，不要 Markdown：
            {"event":true或false,"type":"事件类型","severity":"LOW|MEDIUM|HIGH|CRITICAL","confidence":0到1,"summary":"简洁中文说明"}
            没有明确事件时 event 必须为 false，不要臆测。
            """;

    private final AppProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public MageClient(AppProperties properties, HttpClient httpClient, ObjectMapper mapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    public MageAnalysis analyze(List<byte[]> frames, String rulePrompt) {
        if (!properties.mage().enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "视觉模型检测已被 MAGE_ENABLED 关闭");
        }
        return "ollama".equalsIgnoreCase(properties.mage().provider())
                ? analyzeWithOllama(frames, rulePrompt)
                : analyzeWithOpenAi(frames, rulePrompt);
    }

    private MageAnalysis analyzeWithOpenAi(List<byte[]> frames, String rulePrompt) {
        try {
            List<Map<String, Object>> content = new ArrayList<>();
            for (byte[] frame : frames) {
                content.add(Map.of("type", "image_url", "image_url", Map.of(
                        "url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(frame))));
            }
            content.add(Map.of("type", "text", "text", "检测规则：" + rulePrompt));

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", properties.mage().model());
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", content)));
            requestBody.put("temperature", 0.1);
            requestBody.put("max_tokens", 256);
            requestBody.put("stream", false);

            HttpRequest request = HttpRequest.newBuilder(chatEndpoint())
                    .timeout(Duration.ofSeconds(properties.mage().requestTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "视觉模型服务返回 HTTP " + response.statusCode() + "：" + concise(response.body()));
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            String modelOutput = contentNode.isTextual() ? contentNode.asText() : contentNode.toString();
            return parseModelOutput(modelOutput);
        } catch (ApiException exception) {
            throw exception;
        } catch (java.net.ConnectException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "无法连接视觉模型服务 " + properties.mage().baseUrl());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "视觉模型分析失败：" + exception.getMessage());
        }
    }

    private MageAnalysis analyzeWithOllama(List<byte[]> frames, String rulePrompt) {
        try {
            List<String> images = frames.stream()
                    .map(frame -> Base64.getEncoder().encodeToString(frame))
                    .toList();
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", properties.mage().model());
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", "检测规则：" + rulePrompt, "images", images)));
            requestBody.put("stream", false);
            requestBody.put("think", false);
            requestBody.put("format", detectionSchema());
            requestBody.put("options", Map.of("temperature", 0.1, "num_predict", 256));

            HttpRequest request = HttpRequest.newBuilder(ollamaChatEndpoint())
                    .timeout(Duration.ofSeconds(properties.mage().requestTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "Ollama 返回 HTTP " + response.statusCode() + "：" + concise(response.body()));
            }
            JsonNode root = mapper.readTree(response.body());
            return parseModelOutput(root.path("message").path("content").asText());
        } catch (ApiException exception) {
            throw exception;
        } catch (java.net.ConnectException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "无法连接 Ollama 服务 " + properties.mage().baseUrl());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Ollama 分析失败：" + exception.getMessage());
        }
    }

    public boolean available() {
        try {
            HttpRequest request = HttpRequest.newBuilder(modelsEndpoint())
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    MageAnalysis parseModelOutput(String raw) {
        try {
            String json = extractJson(raw);
            JsonNode node = mapper.readTree(json);
            return new MageAnalysis(node.path("event").asBoolean(false), node.path("type").asText("其他"),
                    node.path("severity").asText("LOW"), node.path("confidence").asDouble(0),
                    node.path("summary").asText(""), raw);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "视觉模型未返回约定的 JSON：" + concise(raw));
        }
    }

    private URI chatEndpoint() {
        String base = properties.mage().baseUrl().replaceAll("/+$", "");
        return URI.create(base + "/chat/completions");
    }

    private URI modelsEndpoint() {
        String base = properties.mage().baseUrl().replaceAll("/+$", "");
        return URI.create(base + "/models");
    }

    private URI ollamaChatEndpoint() {
        String base = properties.mage().baseUrl().replaceAll("/+$", "").replaceFirst("/v1$", "");
        return URI.create(base + "/api/chat");
    }

    private Map<String, Object> detectionSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("event", Map.of("type", "boolean"));
        properties.put("type", Map.of("type", "string"));
        properties.put("severity", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH", "CRITICAL")));
        properties.put("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1));
        properties.put("summary", Map.of("type", "string"));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("event", "type", "severity", "confidence", "summary"));
    }

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < start) return "{}";
        return raw.substring(start, end + 1);
    }

    private String concise(String value) {
        if (value == null) return "无响应内容";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "…";
    }
}
