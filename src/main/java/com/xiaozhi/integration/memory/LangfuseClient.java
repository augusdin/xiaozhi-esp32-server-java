package com.xiaozhi.integration.memory;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class LangfuseClient {
    private static final Logger logger = LoggerFactory.getLogger(LangfuseClient.class);
    private final MemoryProperties properties;
    private final OkHttpClient http;

    public LangfuseClient(MemoryProperties properties) {
        this.properties = properties;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(properties.getLangfuseTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getLangfuseTimeoutMs()))
                .build();
    }

    public boolean isEnabled() {
        return properties.isLangfuseEnabled() && properties.getLangfuseHost() != null
                && properties.getLangfusePublicKey() != null && properties.getLangfuseSecretKey() != null;
    }

    public void track(String path, String jsonBody) {
        if (!isEnabled()) return;
        try {
            Request request = new Request.Builder()
                    .url(properties.getLangfuseHost().replaceAll("/$", "") + path)
                    .header("X-PUBLIC-KEY", properties.getLangfusePublicKey())
                    .header("X-SECRET-KEY", properties.getLangfuseSecretKey())
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();
            try (Response resp = http.newCall(request).execute()) {
                if (!resp.isSuccessful()) {
                    logger.warn("Langfuse track failed: status={}", resp.code());
                }
            }
        } catch (Exception e) {
            logger.warn("Langfuse track error: {}", e.toString());
        }
    }
}

