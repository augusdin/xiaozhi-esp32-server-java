package com.xiaozhi.integration.memory;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class Mem0Client {
    private static final Logger logger = LoggerFactory.getLogger(Mem0Client.class);
    private final MemoryProperties properties;
    private final OkHttpClient http;

    public Mem0Client(MemoryProperties properties) {
        this.properties = properties;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(properties.getMem0TimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getMem0TimeoutMs()))
                .build();
    }

    public boolean isEnabled() {
        return properties.isEnabled() && properties.isMem0Enabled() && properties.getMem0Url() != null;
    }

    public void addMemoryAsync(String userId, String text) {
        if (!isEnabled()) return;
        Thread.startVirtualThread(() -> addMemory(userId, text));
    }

    public void addMemory(String userId, String text) {
        try {
            String url = properties.getMem0Url().replaceAll("/$", "") + "/api/v1/memories";
            String json = "{\"user_id\":\"" + escape(userId) + "\",\"text\":\"" + escape(text) + "\",\"app\":\"xiaozhi\"}";
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();
            try (Response resp = http.newCall(request).execute()) {
                if (!resp.isSuccessful()) {
                    logger.warn("Mem0 addMemory failed: status={}", resp.code());
                }
            }
        } catch (Exception e) {
            logger.warn("Mem0 addMemory error: {}", e.toString());
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

