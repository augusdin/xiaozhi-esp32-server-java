package com.xiaozhi.common.config;

import com.xiaozhi.integration.langfuse.LangfuseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

/**
 * Langfuse 配置类
 * 负责 Langfuse 相关的 Spring 配置
 */
@Configuration
@EnableConfigurationProperties(LangfuseProperties.class)
public class LangfuseConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(LangfuseConfig.class);
    
    /**
     * 应用关闭时的清理操作
     */
    @EventListener
    public void handleContextClosed(ContextClosedEvent event) {
        try {
            logger.info("应用关闭，清理 Langfuse 相关资源...");
            // 清理所有追踪上下文
            com.xiaozhi.integration.langfuse.TraceContext.cleanupAll();
            logger.info("Langfuse 资源清理完成");
        } catch (Exception e) {
            logger.error("清理 Langfuse 资源时发生错误: {}", e.getMessage(), e);
        }
    }
}
