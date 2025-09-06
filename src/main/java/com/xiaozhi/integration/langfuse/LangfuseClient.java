package com.xiaozhi.integration.langfuse;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Langfuse HTTP API 客户端封装类
 * 基于 HTTP REST API 实现与 Langfuse 的交互
 */
@Component
public class LangfuseClient {
    
    private static final Logger logger = LoggerFactory.getLogger(LangfuseClient.class);
    private static final String API_PATH = "/api/public/ingestion";
    
    @Autowired
    private LangfuseProperties properties;
    
    private OkHttpClient httpClient;
    private Gson gson;
    private ExecutorService executorService;
    private String authHeader;
    
    /**
     * 初始化 HTTP 客户端
     */
    @PostConstruct
    public void initialize() {
        if (!properties.isEnabled()) {
            logger.info("Langfuse 已禁用");
            return;
        }
        
        try {
            logger.info("正在初始化 Langfuse HTTP 客户端...");
            logger.info("Langfuse 配置: {}", properties);
            
            // 初始化 HTTP 客户端
            this.httpClient = new OkHttpClient.Builder()
                    .connectTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
                    .readTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
                    .writeTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
                    .build();
            
            this.gson = new Gson();
            this.executorService = Executors.newVirtualThreadPerTaskExecutor();
            
            // 构建认证头 - Langfuse 使用 Bearer token 认证
            this.authHeader = "Bearer " + properties.getSecretKey();
            
            logger.info("Langfuse HTTP 客户端初始化成功");
        } catch (Exception e) {
            logger.error("Langfuse HTTP 客户端初始化失败: {}", e.getMessage(), e);
            // 不抛出异常，避免影响应用启动
            this.httpClient = null;
        }
    }
    
    /**
     * 销毁客户端
     */
    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            try {
                logger.info("正在关闭 Langfuse 客户端...");
                executorService.shutdown();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
                logger.info("Langfuse 客户端已关闭");
            } catch (Exception e) {
                logger.error("关闭 Langfuse 客户端时发生错误: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * 检查客户端是否可用
     */
    public boolean isAvailable() {
        return properties.isEnabled() && httpClient != null;
    }
    
    /**
     * 创建追踪
     */
    public CompletableFuture<String> createTrace(String name, String userId, String sessionId, 
                                                Map<String, Object> metadata) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String traceId = UUID.randomUUID().toString();
                
                // Langfuse ingestion API 格式
                JsonObject event = new JsonObject();
                event.addProperty("id", traceId);
                event.addProperty("type", "trace-create");
                event.addProperty("timestamp", Instant.now().toString());
                
                JsonObject body = new JsonObject();
                body.addProperty("id", traceId);
                body.addProperty("name", name);
                if (userId != null) {
                    body.addProperty("userId", userId);
                }
                if (sessionId != null) {
                    body.addProperty("sessionId", sessionId);
                }
                if (metadata != null && !metadata.isEmpty()) {
                    body.add("metadata", gson.toJsonTree(metadata));
                }
                
                event.add("body", body);
                
                // 包装为事件数组
                com.google.gson.JsonArray events = new com.google.gson.JsonArray();
                events.add(event);
                
                JsonObject requestBody = new JsonObject();
                requestBody.add("batch", events);
                
                boolean success = sendRequest("", requestBody);
                return success ? traceId : null;
                
            } catch (Exception e) {
                logger.error("创建追踪失败: {}", e.getMessage(), e);
                return null;
            }
        }, executorService);
    }
    
    /**
     * 创建 Span
     */
    public CompletableFuture<String> createSpan(String traceId, String name, Object input, 
                                               Map<String, Object> metadata) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String spanId = UUID.randomUUID().toString();
                
                // Langfuse ingestion API 格式
                JsonObject event = new JsonObject();
                event.addProperty("id", spanId);
                event.addProperty("type", "span-create");
                event.addProperty("timestamp", Instant.now().toString());
                
                JsonObject body = new JsonObject();
                body.addProperty("id", spanId);
                body.addProperty("traceId", traceId);
                body.addProperty("name", name);
                body.addProperty("startTime", Instant.now().toString());
                
                if (input != null) {
                    body.add("input", gson.toJsonTree(input));
                }
                
                if (metadata != null && !metadata.isEmpty()) {
                    body.add("metadata", gson.toJsonTree(metadata));
                }
                
                event.add("body", body);
                
                // 包装为事件数组
                com.google.gson.JsonArray events = new com.google.gson.JsonArray();
                events.add(event);
                
                JsonObject requestBody = new JsonObject();
                requestBody.add("batch", events);
                
                boolean success = sendRequest("", requestBody);
                return success ? spanId : null;
                
            } catch (Exception e) {
                logger.error("创建 Span 失败: {}", e.getMessage(), e);
                return null;
            }
        }, executorService);
    }
    
    /**
     * 结束 Span
     */
    public CompletableFuture<Void> endSpan(String spanId, Object output, Map<String, Object> metadata) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                // Langfuse ingestion API 格式
                JsonObject event = new JsonObject();
                event.addProperty("id", spanId);
                event.addProperty("type", "span-update");
                event.addProperty("timestamp", Instant.now().toString());
                
                JsonObject body = new JsonObject();
                body.addProperty("id", spanId);
                body.addProperty("endTime", Instant.now().toString());
                
                if (output != null) {
                    body.add("output", gson.toJsonTree(output));
                }
                
                if (metadata != null && !metadata.isEmpty()) {
                    body.add("metadata", gson.toJsonTree(metadata));
                }
                
                event.add("body", body);
                
                // 包装为事件数组
                com.google.gson.JsonArray events = new com.google.gson.JsonArray();
                events.add(event);
                
                JsonObject requestBody = new JsonObject();
                requestBody.add("batch", events);
                
                sendRequest("", requestBody);
                
            } catch (Exception e) {
                logger.error("结束 Span 失败: {}", e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 创建生成记录
     */
    public CompletableFuture<Void> createGeneration(String traceId, String model, Object input, 
                                                   String output, Map<String, Object> usage, 
                                                   Map<String, Object> metadata) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                String generationId = UUID.randomUUID().toString();
                
                // Langfuse ingestion API 格式
                JsonObject event = new JsonObject();
                event.addProperty("id", generationId);
                event.addProperty("type", "generation-create");
                event.addProperty("timestamp", Instant.now().toString());
                
                JsonObject body = new JsonObject();
                body.addProperty("id", generationId);
                body.addProperty("traceId", traceId);
                body.addProperty("name", "llm-generation");
                if (model != null) {
                    body.addProperty("model", model);
                }
                body.addProperty("startTime", Instant.now().toString());
                body.addProperty("endTime", Instant.now().toString());
                
                if (input != null) {
                    body.add("input", gson.toJsonTree(input));
                }
                
                if (output != null) {
                    body.addProperty("output", output);
                }
                
                if (usage != null && !usage.isEmpty()) {
                    body.add("usage", gson.toJsonTree(usage));
                }
                
                if (metadata != null && !metadata.isEmpty()) {
                    body.add("metadata", gson.toJsonTree(metadata));
                }
                
                event.add("body", body);
                
                // 包装为事件数组
                com.google.gson.JsonArray events = new com.google.gson.JsonArray();
                events.add(event);
                
                JsonObject requestBody = new JsonObject();
                requestBody.add("batch", events);
                
                sendRequest("", requestBody);
                
            } catch (Exception e) {
                logger.error("创建生成记录失败: {}", e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * 刷新待发送的数据
     * 在 HTTP API 版本中，这是一个空操作，因为请求是立即发送的
     */
    public void flush() {
        // HTTP API 版本中的空操作
        logger.debug("Langfuse HTTP 客户端刷新请求（空操作）");
    }
    
    /**
     * 发送 HTTP 请求
     */
    private boolean sendRequest(String endpoint, JsonObject body) {
        try {
            String url = properties.getHost() + API_PATH + endpoint;
            
            RequestBody requestBody = RequestBody.create(
                    body.toString(),
                    MediaType.parse("application/json")
            );
            
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.info("成功发送请求到 Langfuse: {}", endpoint);
                    return true;
                } else {
                    logger.error("Langfuse 请求失败: {} - {}, URL: {}, Body: {}", 
                        response.code(), response.message(), url, body.toString());
                    return false;
                }
            }
            
        } catch (IOException e) {
            logger.error("发送 Langfuse 请求时发生 IO 错误: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            logger.error("发送 Langfuse 请求时发生未知错误: {}", e.getMessage(), e);
            return false;
        }
    }
}
