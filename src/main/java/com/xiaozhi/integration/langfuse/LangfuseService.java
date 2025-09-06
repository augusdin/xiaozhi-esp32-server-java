package com.xiaozhi.integration.langfuse;

import com.xiaozhi.communication.common.ChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Langfuse 服务类
 * 提供对话追踪和分析功能
 */
@Service
public class LangfuseService {
    
    private static final Logger logger = LoggerFactory.getLogger(LangfuseService.class);
    
    @Autowired
    private LangfuseClient langfuseClient;
    
    /**
     * 开始一个新的对话追踪
     * 
     * @param session 聊天会话
     * @param userId 用户ID
     * @param sessionMetadata 会话元数据
     * @return 追踪ID
     */
    public CompletableFuture<String> startTrace(ChatSession session, String userId, Map<String, Object> sessionMetadata) {
        if (!langfuseClient.isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            String sessionId = session.getSessionId();
            TraceContext context = TraceContext.getOrCreate(sessionId);
            
            // 构建追踪元数据
            Map<String, Object> traceMetadata = new java.util.HashMap<>();
            traceMetadata.put("sessionId", sessionId);
            traceMetadata.put("deviceId", session.getSysDevice() != null ? session.getSysDevice().getDeviceId() : "unknown");
            traceMetadata.put("roleId", session.getSysDevice() != null ? session.getSysDevice().getRoleId() : "unknown");
            traceMetadata.put("startTime", System.currentTimeMillis());
            
            // 合并用户提供的元数据
            if (sessionMetadata != null) {
                traceMetadata.putAll(sessionMetadata);
            }
            
            // 创建追踪
            return langfuseClient.createTrace("xiaozhi-dialogue", userId, sessionId, traceMetadata)
                    .thenApply(traceId -> {
                        if (traceId != null) {
                            context.setTraceId(traceId);
                            logger.debug("开始新的对话追踪: sessionId={}, traceId={}", sessionId, traceId);
                        } else {
                            logger.warn("创建对话追踪失败: sessionId={}", sessionId);
                        }
                        return traceId;
                    });
            
        } catch (Exception e) {
            logger.error("开始对话追踪失败: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * 创建一个 Span
     * 
     * @param sessionId 会话ID
     * @param spanName Span名称
     * @param input 输入数据
     * @param metadata 元数据
     * @return Span ID
     */
    public CompletableFuture<String> createSpan(String sessionId, String spanName, Object input, Map<String, Object> metadata) {
        if (!langfuseClient.isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            TraceContext context = TraceContext.get(sessionId);
            if (context == null || context.getTraceId() == null) {
                logger.warn("未找到会话 {} 的追踪上下文", sessionId);
                return CompletableFuture.completedFuture(null);
            }
            
            return langfuseClient.createSpan(context.getTraceId(), spanName, input, metadata)
                    .thenApply(spanId -> {
                        if (spanId != null) {
                            context.setCurrentSpanId(spanId);
                            logger.debug("创建新的 Span: sessionId={}, spanName={}, spanId={}", sessionId, spanName, spanId);
                        } else {
                            logger.warn("创建 Span 失败: sessionId={}, spanName={}", sessionId, spanName);
                        }
                        return spanId;
                    });
            
        } catch (Exception e) {
            logger.error("创建 Span 失败: spanName={}, error={}", spanName, e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * 结束一个 Span
     * 
     * @param sessionId 会话ID
     * @param spanId Span ID
     * @param output 输出数据
     * @param metadata 元数据
     */
    public CompletableFuture<Void> endSpan(String sessionId, String spanId, Object output, Map<String, Object> metadata) {
        if (!langfuseClient.isAvailable() || spanId == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            return langfuseClient.endSpan(spanId, output, metadata)
                    .thenRun(() -> {
                        logger.debug("结束 Span: sessionId={}, spanId={}", sessionId, spanId);
                    });
            
        } catch (Exception e) {
            logger.error("结束 Span 失败: spanId={}, error={}", spanId, e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * 记录 LLM 生成过程
     * 
     * @param sessionId 会话ID
     * @param model 模型名称
     * @param input 输入消息列表
     * @param output 输出内容
     * @param usage Token使用统计
     * @param metadata 元数据
     */
    public CompletableFuture<Void> recordGeneration(String sessionId, String model, List<Map<String, Object>> input, 
                                String output, Map<String, Object> usage, Map<String, Object> metadata) {
        if (!langfuseClient.isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            TraceContext context = TraceContext.get(sessionId);
            if (context == null || context.getTraceId() == null) {
                logger.warn("未找到会话 {} 的追踪上下文", sessionId);
                return CompletableFuture.completedFuture(null);
            }
            
            return langfuseClient.createGeneration(context.getTraceId(), model, input, output, usage, metadata)
                    .thenRun(() -> {
                        logger.debug("记录 LLM 生成: sessionId={}, model={}", sessionId, model);
                    });
            
        } catch (Exception e) {
            logger.error("记录 LLM 生成失败: model={}, error={}", model, e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * 结束对话追踪
     * 
     * @param sessionId 会话ID
     * @param output 最终输出
     * @param metadata 元数据
     */
    public void endTrace(String sessionId, Object output, Map<String, Object> metadata) {
        if (!langfuseClient.isAvailable()) {
            return;
        }
        
        try {
            TraceContext context = TraceContext.get(sessionId);
            if (context == null || context.getTraceId() == null) {
                logger.warn("未找到会话 {} 的追踪上下文", sessionId);
                return;
            }
            
            // 注意：在HTTP API版本中，我们不需要显式结束Trace
            // Trace的结束时间和输出可以在最后一个Span或Generation中设置
            
            logger.debug("结束对话追踪: sessionId={}, traceId={}, duration={}ms", 
                    sessionId, context.getTraceId(), context.getDuration());
            
            // 清理上下文
            TraceContext.cleanup(sessionId);
            
        } catch (Exception e) {
            logger.error("结束对话追踪失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }
    }
    
    /**
     * 异步刷新数据
     */
    public CompletableFuture<Void> flushAsync() {
        return CompletableFuture.runAsync(() -> {
            if (langfuseClient.isAvailable()) {
                langfuseClient.flush();
            }
        });
    }
    
    /**
     * 清理会话资源
     * 
     * @param sessionId 会话ID
     */
    public void cleanupSession(String sessionId) {
        try {
            TraceContext.cleanup(sessionId);
            logger.debug("清理会话追踪资源: sessionId={}", sessionId);
        } catch (Exception e) {
            logger.error("清理会话追踪资源失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }
}
