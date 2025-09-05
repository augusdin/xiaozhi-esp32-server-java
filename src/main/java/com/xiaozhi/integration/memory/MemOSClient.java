package com.xiaozhi.integration.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class MemOSClient {
    private static final Logger logger = LoggerFactory.getLogger(MemOSClient.class);
    private final MemoryProperties properties;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public MemOSClient(MemoryProperties properties) {
        this.properties = properties;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(properties.getMemosTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getMemosTimeoutMs()))
                .build();
    }

    public boolean isEnabled() {
        return properties.isEnabled() && properties.isMemosEnabled() && properties.getMemosUrl() != null;
    }

    public String buildMemoryContext(String userId, String query) {
        if (!isEnabled()) return null;
        try {
            String url = properties.getMemosUrl().replaceAll("/$", "") + "/product/search";
            int topK = Math.max(1, properties.getMemosTopK());
            String json = String.format("{\"user_id\":\"%s\",\"query\":\"%s\",\"top_k\":%d}",
                    escape(userId), escape(query), topK);
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();
            try (Response resp = http.newCall(request).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    logger.warn("MemOS search failed: status={}", resp.code());
                    return null;
                }
                JsonNode root = mapper.readTree(resp.body().string());
                JsonNode data = root.get("data");
                if (data == null) return null;
                List<String> items = new ArrayList<>();
                // Prefer textual memories if present
                if (data.has("text_mem")) {
                    for (JsonNode n : data.get("text_mem")) {
                        if (n.isTextual()) items.add(n.asText());
                        else if (n.has("content")) items.add(n.get("content").asText(""));
                    }
                }
                // Fallback to generic list
                if (items.isEmpty() && data.isArray()) {
                    for (JsonNode n : data) items.add(n.asText(""));
                }
                if (items.isEmpty()) return null;
                StringBuilder sb = new StringBuilder("User Memories:\n");
                for (String it : items) {
                    if (it == null || it.isBlank()) continue;
                    sb.append("- ").append(it.replaceAll("\n", " ")).append("\n");
                }
                return sb.toString();
            }
        } catch (Exception e) {
            logger.warn("MemOS search error: {}", e.toString());
            return null;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

