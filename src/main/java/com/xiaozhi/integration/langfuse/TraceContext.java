package com.xiaozhi.integration.langfuse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Langfuse 追踪上下文
 * 用于在整个对话会话中传递追踪信息
 */
public class TraceContext {
    
    /**
     * 会话ID到追踪上下文的映射
     */
    private static final Map<String, TraceContext> contexts = new ConcurrentHashMap<>();
    
    private final String sessionId;
    private String traceId;
    private String currentSpanId;
    private final Map<String, Object> metadata;
    private final long startTime;
    
    private TraceContext(String sessionId) {
        this.sessionId = sessionId;
        this.metadata = new HashMap<>();
        this.startTime = System.currentTimeMillis();
    }
    
    /**
     * 获取或创建会话的追踪上下文
     */
    public static TraceContext getOrCreate(String sessionId) {
        return contexts.computeIfAbsent(sessionId, TraceContext::new);
    }
    
    /**
     * 获取会话的追踪上下文
     */
    public static TraceContext get(String sessionId) {
        return contexts.get(sessionId);
    }
    
    /**
     * 清理会话的追踪上下文
     */
    public static void cleanup(String sessionId) {
        contexts.remove(sessionId);
    }
    
    /**
     * 清理所有追踪上下文
     */
    public static void cleanupAll() {
        contexts.clear();
    }
    
    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
    
    public String getCurrentSpanId() {
        return currentSpanId;
    }
    
    public void setCurrentSpanId(String currentSpanId) {
        this.currentSpanId = currentSpanId;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    /**
     * 获取会话持续时间（毫秒）
     */
    public long getDuration() {
        return System.currentTimeMillis() - startTime;
    }
    
    @Override
    public String toString() {
        return "TraceContext{" +
                "sessionId='" + sessionId + '\'' +
                ", traceId='" + traceId + '\'' +
                ", currentSpanId='" + currentSpanId + '\'' +
                ", duration=" + getDuration() + "ms" +
                '}';
    }
}
