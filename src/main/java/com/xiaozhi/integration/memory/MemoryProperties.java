package com.xiaozhi.integration.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {
    private boolean enabled = true;

    private boolean memosEnabled = false;
    private String memosUrl;
    private int memosTopK = 5;
    private int memosTimeoutMs = 1000;

    private boolean mem0Enabled = false;
    private String mem0Url;
    private int mem0TimeoutMs = 1500;


    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isMemosEnabled() { return memosEnabled; }
    public void setMemosEnabled(boolean memosEnabled) { this.memosEnabled = memosEnabled; }
    public String getMemosUrl() { return memosUrl; }
    public void setMemosUrl(String memosUrl) { this.memosUrl = memosUrl; }
    public int getMemosTopK() { return memosTopK; }
    public void setMemosTopK(int memosTopK) { this.memosTopK = memosTopK; }
    public int getMemosTimeoutMs() { return memosTimeoutMs; }
    public void setMemosTimeoutMs(int memosTimeoutMs) { this.memosTimeoutMs = memosTimeoutMs; }

    public boolean isMem0Enabled() { return mem0Enabled; }
    public void setMem0Enabled(boolean mem0Enabled) { this.mem0Enabled = mem0Enabled; }
    public String getMem0Url() { return mem0Url; }
    public void setMem0Url(String mem0Url) { this.mem0Url = mem0Url; }
    public int getMem0TimeoutMs() { return mem0TimeoutMs; }
    public void setMem0TimeoutMs(int mem0TimeoutMs) { this.mem0TimeoutMs = mem0TimeoutMs; }

}

