package com.xiaozhi.integration.langfuse;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Langfuse 配置属性
 * 对应 application.properties 中的 langfuse.* 配置项
 */
@ConfigurationProperties(prefix = "langfuse")
public class LangfuseProperties {
    
    /**
     * 是否启用 Langfuse
     */
    private boolean enabled = true;
    
    /**
     * Langfuse 服务地址
     */
    private String host = "http://198.12.104.212:3000";
    
    /**
     * 私钥
     */
    private String secretKey = "sk-lf-5a89772f-10f4-4173-956d-1378d638eaf9";
    
    /**
     * 公钥
     */
    private String publicKey = "pk-lf-e4194da0-96ee-4bf5-a556-fc3ca29d6ec4";
    
    /**
     * 请求超时时间（毫秒）
     */
    private long timeoutMs = 5000;
    
    /**
     * 批量大小
     */
    private int batchSize = 10;
    
    /**
     * 刷新间隔（毫秒）
     */
    private long flushIntervalMs = 1000;

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public void setFlushIntervalMs(long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }

    @Override
    public String toString() {
        return "LangfuseProperties{" +
                "enabled=" + enabled +
                ", host='" + host + '\'' +
                ", secretKey='***'" +
                ", publicKey='***'" +
                ", timeoutMs=" + timeoutMs +
                ", batchSize=" + batchSize +
                ", flushIntervalMs=" + flushIntervalMs +
                '}';
    }
}
