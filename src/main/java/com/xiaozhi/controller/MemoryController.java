package com.xiaozhi.controller;

import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.integration.memory.MemOSClient;
import com.xiaozhi.integration.memory.MemoryProperties;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/memory")
public class MemoryController extends BaseController {
    private static final Logger logger = LoggerFactory.getLogger(MemoryController.class);

    private final MemoryProperties memoryProperties;
    private final MemOSClient memOSClient;

    public MemoryController(MemoryProperties memoryProperties, MemOSClient memOSClient) {
        this.memoryProperties = memoryProperties;
        this.memOSClient = memOSClient;
    }

    @GetMapping("/status")
    public AjaxResult status() {
        Map<String, Object> data = new HashMap<>();
        data.put("memory.enabled", memoryProperties.isEnabled());
        data.put("memos.enabled", memoryProperties.isMemosEnabled());
        data.put("memos.url", memoryProperties.getMemosUrl());
        data.put("mem0.enabled", memoryProperties.isMem0Enabled());
        data.put("mem0.url", memoryProperties.getMem0Url());
        return AjaxResult.success("ok", data);
    }

    @PostMapping("/test")
    public AjaxResult test(@RequestParam("userId") String userId,
                           @RequestParam(value = "query", required = false) String query) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> steps = new HashMap<>();
        data.put("steps", steps);
        data.put("memos.enabled", memoryProperties.isMemosEnabled());
        data.put("memos.url", memoryProperties.getMemosUrl());

        // Step 1: 简单连通性（/openapi.json）
        try {
            String base = memoryProperties.getMemosUrl();
            if (base != null) {
                String url = base.replaceAll("/$", "") + "/openapi.json";
                OkHttpClient http = new OkHttpClient.Builder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(5))
                        .build();
                Request req = new Request.Builder().url(url).build();
                try (Response resp = http.newCall(req).execute()) {
                    steps.put("openapi.status", resp.code());
                    steps.put("openapi.ok", resp.isSuccessful());
                }
            }
        } catch (Exception e) {
            steps.put("openapi.error", e.toString());
        }

        // 仅在启用时执行后续测试
        if (!memOSClient.isEnabled()) {
            return AjaxResult.error(400, "MemOS 未启用", data);
        }

        // Step 2: 写入测试记忆（同步）
        try {
            memOSClient.addMemory(userId, "User: ping\nAssistant: pong");
            steps.put("addMemory", "invoked");
        } catch (Exception e) {
            steps.put("addMemory.error", e.toString());
        }

        // Step 3: 检索上下文
        try {
            String q = (query == null || query.isBlank()) ? "ping" : query;
            String context = memOSClient.buildMemoryContext(userId, q);
            steps.put("search.ok", context != null);
            if (context != null) {
                steps.put("search.context.sample", context.length() > 200 ? context.substring(0, 200) + "..." : context);
            }
        } catch (Exception e) {
            steps.put("search.error", e.toString());
        }

        return AjaxResult.success("done", data);
    }
}

