package com.xiaozhi.integration.memory;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MemOS 配置管理器 - 处理用户注册和 MemCube 初始化
 */
@Service
public class MemOSConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(MemOSConfigManager.class);
    
    private final MemoryProperties properties;
    private final OkHttpClient http;
    
    // 缓存已配置的用户，避免重复初始化
    private final Map<String, Boolean> configuredUsers = new ConcurrentHashMap<>();
    // 产品化 API 下缓存用户的 mem_cube_id
    private final Map<String, String> userCubeIds = new ConcurrentHashMap<>();

    public String getUserCubeId(String userId) {
        return userCubeIds.get(userId);
    }

    public MemOSConfigManager(MemoryProperties properties) {
        this.properties = properties;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(properties.getMemosTimeoutMs() * 2))
                .readTimeout(Duration.ofMillis(properties.getMemosTimeoutMs() * 2))
                .build();
    }

    /**
     * 确保用户已在 MemOS 中配置
     */
    public boolean ensureUserConfigured(String userId) {
        if (configuredUsers.containsKey(userId)) {
            return true; // 已配置过
        }

        try {
            // 优先走产品化 API：/product/users/register
            if (tryProductRegister(userId)) {
                configuredUsers.put(userId, true);
                logger.info("MemOS (product) user configuration completed for: {} cubeId={}", userId, userCubeIds.get(userId));
                return true;
            }

            // 兼容回退：使用根 API（/configure + 需要额外的 /mem_cubes 注册，当前不自动注册）
            if (configureMOS(userId)) {
                configuredUsers.put(userId, true);
                logger.info("MemOS (root) user configured for: {} (mem_cubes registration required)", userId);
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("Failed to configure MemOS for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * 使用产品 API 注册用户，并缓存 mem_cube_id
     */
    private boolean tryProductRegister(String userId) {
        try {
            String base = properties.getMemosUrl().replaceAll("/$", "");
            String url = base + "/product/users/register";
            String json = String.format("{" +
                    "\"user_id\":\"%s\"," +
                    "\"user_name\":\"%s\"," +
                    "\"interests\":\"\"" +
                    "}", escape(userId), escape(userId));
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response resp = http.newCall(request).execute()) {
                String body = resp.body() != null ? resp.body().string() : "";
                if (!resp.isSuccessful()) {
                    // 404 代表产品 API 不可用，交由上层回退
                    logger.info("MemOS product register not available or failed: status={} body={}", resp.code(), body);
                    return false;
                }
                // 解析 data.mem_cube_id
                try {
                    com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
                    String cubeId = null;
                    if (root.has("data") && root.get("data").has("mem_cube_id")) {
                        cubeId = root.get("data").get("mem_cube_id").asText();
                    }
                    if (cubeId != null && !cubeId.isBlank()) {
                        userCubeIds.put(userId, cubeId);
                        return true;
                    }
                } catch (Exception ignore) {}
                logger.warn("MemOS product register succeeded but mem_cube_id not found. body={}", body);
                return true; // 依然视为配置成功
            }
        } catch (Exception e) {
            logger.info("MemOS product register error: {}", e.toString());
            return false;
        }
    }

    /**
     * 配置 MOS 系统
     */
    private boolean configureMOS(String userId) {
        try {
            String url = properties.getMemosUrl().replaceAll("/$", "") + "/configure";
            
            // 构建 MOS 配置
            String mosConfig = buildMOSConfig(userId);
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(mosConfig, MediaType.parse("application/json")))
                    .build();

            try (Response resp = http.newCall(request).execute()) {
                if (resp.isSuccessful()) {
                    logger.debug("MOS configured successfully for user: {}", userId);
                    return true;
                } else {
                    logger.warn("MOS configuration failed for user {}: status={}", userId, resp.code());
                    if (resp.body() != null) {
                        logger.debug("Response: {}", resp.body().string());
                    }
                    return false;
                }
            }
        } catch (Exception e) {
            logger.error("Error configuring MOS for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * 创建和注册 MemCube
     */
    private boolean createAndRegisterMemCube(String userId) {
        try {
            String url = properties.getMemosUrl().replaceAll("/$", "") + "/register";
            
            // 构建 MemCube 配置
            String cubeConfig = buildMemCubeConfig(userId);
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(cubeConfig, MediaType.parse("application/json")))
                    .build();

            try (Response resp = http.newCall(request).execute()) {
                if (resp.isSuccessful()) {
                    logger.debug("MemCube registered successfully for user: {}", userId);
                    return true;
                } else {
                    logger.warn("MemCube registration failed for user {}: status={}", userId, resp.code());
                    return false;
                }
            }
        } catch (Exception e) {
            logger.error("Error registering MemCube for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * 构建 MOS 配置 JSON
     */
    private String buildMOSConfig(String userId) {
        String apiKey = getOpenAIApiKey();
        String apiBase = getOpenAIApiBase();
        int topK = properties.getMemosTopK();

        return String.format("""
            {
              "user_id": "%s",
              "session_id": "%s_session",
              "enable_textual_memory": true,
              "enable_activation_memory": false,
              "top_k": %d,
              "chat_model": {
                "backend": "openai",
                "config": {
                  "model_name_or_path": "gpt-4o-mini",
                  "api_key": "%s",
                  "api_base": "%s",
                  "temperature": 0.5
                }
              },
              "mem_reader": {
                "backend": "simple_struct",
                "config": {
                  "llm": {
                    "backend": "openai",
                    "config": {
                      "model_name_or_path": "gpt-4o-mini",
                      "api_key": "%s",
                      "api_base": "%s",
                      "temperature": 0.5
                    }
                  },
                  "embedder": {
                    "backend": "universal_api",
                    "config": {
                      "provider": "openai",
                      "api_key": "%s",
                      "model_name_or_path": "text-embedding-3-small",
                      "base_url": "%s"
                    }
                  },
                  "chunker": {
                    "backend": "sentence",
                    "config": {
                      "tokenizer_or_token_counter": "gpt2",
                      "chunk_size": 512,
                      "chunk_overlap": 128,
                      "min_sentences_per_chunk": 1
                    }
                  }
                }
              }
            }
            """,
            escape(userId),
            escape(userId),
            topK,
            escape(apiKey),
            escape(apiBase),
            escape(apiKey),
            escape(apiBase),
            escape(apiKey),
            escape(apiBase)
        );
    }

    /**
     * 构建 MemCube 配置 JSON
     */
    private String buildMemCubeConfig(String userId) {
        return String.format("""
            {
              "user_id": "%s",
              "cube_id": "%s_cube",
              "config": {
                "text_mem": {
                  "backend": "tree_text",
                  "config": {
                    "graph_db": {
                      "backend": "neo4j",
                      "config": {
                        "uri": "bolt://107.173.38.186:7687",
                        "user": "neo4j",
                        "password": "12345678",
                        "db_name": "memorydb_%s",
                        "auto_create": true
                      }
                    }
                  }
                }
              }
            }
            """, 
            escape(userId), 
            escape(userId),
            escape(sanitizeDbName(userId))
        );
    }

    private String getOpenAIApiKey() {
        // 从环境变量获取，如果没有则使用默认值
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            // 使用 docker-compose.yml 中配置的默认值
            return "sk-iTpKfmPIH0mDuZ0o12C56dA6CaF5406eAf34EcBe4406A494";
        }
        return apiKey;
    }

    private String getOpenAIApiBase() {
        String apiBase = System.getenv("OPENAI_API_BASE");
        if (apiBase == null || apiBase.isBlank()) {
            // 使用 docker-compose.yml 中配置的默认值
            return "https://api.aihubmix.com/v1";
        }
        return apiBase;
    }

    /**
     * 清理用户ID用作数据库名称（移除特殊字符，确保安全）
     */
    private String sanitizeDbName(String userId) {
        if (userId == null) return "default";
        // 只保留字母、数字和下划线，替换其他字符为下划线
        return userId.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
