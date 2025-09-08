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
    private final MemOSConfigManager configManager;

    public MemOSClient(MemoryProperties properties, MemOSConfigManager configManager) {
        this.properties = properties;
        this.configManager = configManager;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(properties.getMemosTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getMemosTimeoutMs()))
                .build();
    }

    public boolean isEnabled() {
        return properties.isEnabled() && properties.isMemosEnabled() && properties.getMemosUrl() != null;
    }

    public String buildMemoryContext(String userId, String query) {
        if (!isEnabled()) {
            logger.debug("MemOS search disabled");
            return null;
        }
        
        try {
            // 1. 确保用户已配置（优先产品 API）
            if (!configManager.ensureUserConfigured(userId)) {
                logger.warn("Failed to configure MemOS for user: {}", userId);
                return null;
            }

            // 2. 优先产品 API 搜索
            String cubeId = configManager.getUserCubeId(userId);
            String product = productSearch(userId, cubeId, query, properties.getMemosTopK());
            if (product != null) return product;

            // 3. 回退根 API 搜索
            return performSearch(userId, query);
            
        } catch (Exception e) {
            logger.warn("MemOS buildMemoryContext error for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
    
    /**
     * 执行 MemOS 搜索
     */
    private String performSearch(String userId, String query) {
        try {
            String url = properties.getMemosUrl().replaceAll("/$", "") + "/search";
            int topK = Math.max(1, properties.getMemosTopK());
            
            // 构建正确的搜索请求
            String json = String.format("""
                {
                  "query": "%s",
                  "user_id": "%s",
                  "install_cube_ids": ["%s_cube"],
                  "top_k": %d
                }
                """, escape(query), escape(userId), escape(userId), topK);
                    
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();
                    
            try (Response resp = http.newCall(request).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    String body = resp.body() != null ? resp.body().string() : "";
                    logger.warn("MemOS search failed for user {}: status={} body={}", userId, resp.code(), body);
                    return null;
                }
                String body = resp.body().string();
                return parseSearchResponse(body);
            }
            
        } catch (Exception e) {
            logger.warn("MemOS search error for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
    
    /** 使用产品 API 搜索 */
    private String productSearch(String userId, String memCubeId, String query, int topK) {
        try {
            String base = properties.getMemosUrl().replaceAll("/$", "");
            String url = base + "/product/search";
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"query\":\"").append(escape(query)).append("\",");
            sb.append("\"user_id\":\"").append(escape(userId)).append("\"");
            if (memCubeId != null && !memCubeId.isBlank()) {
                sb.append(",\"mem_cube_id\":\"").append(escape(memCubeId)).append("\"");
            }
            sb.append(",\"top_k\":").append(Math.max(1, topK));
            sb.append("}");

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(sb.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response resp = http.newCall(request).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    String body = resp.body() != null ? resp.body().string() : "";
                    logger.info("MemOS product search not available or failed: status={} body={}", resp.code(), body);
                    return null;
                }
                String body = resp.body().string();
                return parseSearchResponse(body);
            }
        } catch (Exception e) {
            logger.info("MemOS product search error: {}", e.toString());
            return null;
        }
    }
    
    /**
     * 解析搜索响应
     */
    private String parseSearchResponse(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode data = root.get("data");
            if (data == null) {
                return null;
            }
            
            List<String> memories = new ArrayList<>();
            
            // 处理文本记忆（兼容两种返回结构）
            if (data.has("text_mem") && data.get("text_mem").isArray()) {
                for (JsonNode memNode : data.get("text_mem")) {
                    // 1) 直接是记忆节点
                    String content = extractMemoryContent(memNode);
                    if (content != null && !content.isBlank()) {
                        memories.add(content);
                        continue;
                    }
                    // 2) 产品API返回形如 { cube_id, memories: [ { id, memory, metadata... }, ... ] }
                    if (memNode.has("memories") && memNode.get("memories").isArray()) {
                        for (JsonNode inner : memNode.get("memories")) {
                            String c = extractMemoryContent(inner);
                            if (c != null && !c.isBlank()) {
                                memories.add(c);
                            }
                        }
                    }
                }
            }
            
            // 如果没有找到记忆，返回 null
            if (memories.isEmpty()) {
                return null;
            }
            
            // 构建记忆上下文
            StringBuilder context = new StringBuilder("Relevant User Memories:\n");
            for (int i = 0; i < memories.size(); i++) {
                context.append(String.format("%d. %s\n", i + 1, memories.get(i).trim()));
            }
            
            return context.toString();
            
        } catch (Exception e) {
            logger.warn("Failed to parse MemOS search response: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从记忆节点中提取内容
     */
    private String extractMemoryContent(JsonNode memNode) {
        // 尝试多种可能的字段名
        String[] contentFields = {"content", "text", "memory", "description"};
        
        for (String field : contentFields) {
            if (memNode.has(field)) {
                String content = memNode.get(field).asText();
                if (!content.isBlank()) {
                    return content;
                }
            }
        }
        
        // 如果是字符串节点，直接返回
        if (memNode.isTextual()) {
            return memNode.asText();
        }
        
        return null;
    }
    
    /**
     * 异步存储记忆到 MemOS
     */
    public void addMemoryAsync(String userId, String content) {
        if (!isEnabled()) {
            logger.debug("MemOS storage disabled");
            return;
        }
        
        Thread.startVirtualThread(() -> addMemory(userId, content));
    }
    
    /**
     * 存储记忆到 MemOS
     */
    public void addMemory(String userId, String content) {
        try {
            // 1) 确保用户已配置
            if (!configManager.ensureUserConfigured(userId)) {
                logger.warn("Failed to configure MemOS for user: {}", userId);
                return;
            }

            // 2) 优先产品 API 写入
            String cubeId = configManager.getUserCubeId(userId);
            if (tryProductAdd(userId, cubeId, content)) return;

            // 3) 回退根 API 写入（要求已配置可访问的 cube）
            tryRootAdd(userId, content);

        } catch (Exception e) {
            logger.warn("MemOS addMemory error for user {}: {}", userId, e.getMessage());
        }
    }

    private boolean tryProductAdd(String userId, String memCubeId, String content) {
        try {
            String base = properties.getMemosUrl().replaceAll("/$", "");
            String url = base + "/product/add";
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"user_id\":\"").append(escape(userId)).append("\"");
            if (memCubeId != null && !memCubeId.isBlank()) {
                sb.append(",\"mem_cube_id\":\"").append(escape(memCubeId)).append("\"");
            }
            sb.append(",\"memory_content\":\"").append(escape(content)).append("\"");
            sb.append("}");

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(sb.toString(), MediaType.parse("application/json")))
                    .build();
            try (Response resp = http.newCall(request).execute()) {
                String body = resp.body() != null ? resp.body().string() : "";
                if (resp.isSuccessful()) {
                    logger.debug("MemOS (product) memory stored: user={} cube={} resp={}", userId, memCubeId, body);
                    return true;
                } else {
                    logger.info("MemOS (product) add failed: status={} body={}", resp.code(), body);
                    return false;
                }
            }
        } catch (Exception e) {
            logger.info("MemOS (product) add error: {}", e.toString());
            return false;
        }
    }

    private void tryRootAdd(String userId, String content) {
        try {
            String url = properties.getMemosUrl().replaceAll("/$", "") + "/memories";
            String json = String.format("{" +
                    "\"user_id\":\"%s\"," +
                    "\"memory_content\":\"%s\"" +
                    "}", escape(userId), escape(content));
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();
            try (Response resp = http.newCall(request).execute()) {
                String body = resp.body() != null ? resp.body().string() : "";
                if (resp.isSuccessful()) {
                    logger.debug("MemOS (root) memory stored: user={} resp={}", userId, body);
                } else {
                    logger.warn("MemOS (root) add failed: status={} body={}", resp.code(), body);
                }
            }
        } catch (Exception e) {
            logger.info("MemOS (root) add error: {}", e.toString());
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
