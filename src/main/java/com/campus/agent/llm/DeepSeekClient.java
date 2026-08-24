package com.campus.agent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** 通过 DeepSeek chat/completions API 调用模型。 */
public class DeepSeekClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public DeepSeekClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return request(systemPrompt, userPrompt, null);
    }

    @Override
    public String chatJson(String systemPrompt, String userPrompt) {
        return request(systemPrompt, userPrompt, "json_object");
    }

    @Override
    public void chatStream(String systemPrompt, String userPrompt, java.util.function.Consumer<String> onDelta){
        try{
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            buildStreamBody(model, systemPrompt, userPrompt),
                            StandardCharsets.UTF_8))
                    .build();
        HttpResponse<Stream<String>> resp = http.send(req, HttpResponse.BodyHandlers.ofLines());
        if(resp.statusCode() != 200){
            throw new LlmException("API 返回 " + resp.statusCode());
        }
        resp.body().forEach(line ->{
            if(!line.startsWith("data: ")) return;
            line = line.substring(6);
            line = line.trim();
            if(line.equals("[DONE]")) return;
            else{
                String delta = parseStreamDelta(line);
                if(delta != null && !delta.isEmpty()){
                    onDelta.accept(delta);
                }
            }
        });
        } catch (IOException e) {
            throw new LlmException("请求失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("请求被中断", e);
        }
    }

    static String buildStreamBody(String model, String systemPrompt, String userPrompt) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("stream", true);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        return MAPPER.writeValueAsString(body);
    }

    static String parseStreamDelta(String payload) {
        try {
            JsonNode n = MAPPER.readTree(payload);
            JsonNode delta = n.path("choices").path(0).path("delta").path("content");
            return delta.isMissingNode() || delta.isNull() ? null : delta.asText();
        } catch (IOException e) {
            return null;
        }
    }

    private String request(String systemPrompt, String userPrompt, String responseFormat) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            buildBody(model, systemPrompt, userPrompt, responseFormat),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                String snippet = resp.body();
                if (snippet.length() > 200) snippet = snippet.substring(0, 200);
                throw new LlmException("API 返回 " + resp.statusCode() + ": " + snippet);
            }
            return parseContent(resp.body());
        } catch (IOException e) {
            throw new LlmException("调用 DeepSeek API 失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("调用 DeepSeek API 被中断: " + e.getMessage(), e);
        }
    }

    /** 构造 chat/completions 请求体 JSON。 */
    static String buildBody(String model, String systemPrompt, String userPrompt, String responseFormat) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        if (responseFormat != null) {
            body.put("response_format", Map.of("type", responseFormat));
        }
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new LlmException("构造请求体失败: " + e.getMessage(), e);
        }
    }

    /** 从响应体取出 choices[0].message.content。 */
    static String parseContent(String responseBody) {
        final JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (IOException e) {
            throw new LlmException("响应不是合法 JSON: " + e.getMessage());
        }
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new LlmException("响应缺少 choices[0].message.content");
        }
        return content.asText();
    }
}
