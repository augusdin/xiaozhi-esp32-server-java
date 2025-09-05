package com.xiaozhi.integration.memory;

import com.xiaozhi.communication.common.ChatSession;
import org.springframework.stereotype.Service;

@Service
public class MemoryOrchestrator {
    private final MemoryProperties properties;
    private final MemOSClient memOSClient;
    private final Mem0Client mem0Client;
    private final LangfuseClient langfuseClient;

    public MemoryOrchestrator(MemoryProperties properties, MemOSClient memOSClient, Mem0Client mem0Client, LangfuseClient langfuseClient) {
        this.properties = properties;
        this.memOSClient = memOSClient;
        this.mem0Client = mem0Client;
        this.langfuseClient = langfuseClient;
    }

    public boolean isMemosEnabled() {
        return properties.isEnabled() && properties.isMemosEnabled();
    }

    public String buildMemorySystemPrompt(ChatSession session, String userMessage) {
        if (!isMemosEnabled() || session == null || session.getSysDevice() == null) return null;
        String userId = session.getSysDevice().getDeviceId();
        return memOSClient.buildMemoryContext(userId, userMessage);
    }

    public void persistAsync(ChatSession session, String userText, String assistantText) {
        if (!properties.isEnabled() || session == null || session.getSysDevice() == null) return;
        String userId = session.getSysDevice().getDeviceId();
        if (properties.isMem0Enabled() && assistantText != null && !assistantText.isBlank()) {
            String toStore = buildDialogueText(userText, assistantText);
            mem0Client.addMemoryAsync(userId, toStore);
        }
        // Langfuse events可以在后续扩展，这里预留入口
    }

    private String buildDialogueText(String userText, String assistantText) {
        StringBuilder sb = new StringBuilder();
        if (userText != null && !userText.isBlank()) {
            sb.append("User: ").append(userText).append("\n");
        }
        if (assistantText != null && !assistantText.isBlank()) {
            sb.append("Assistant: ").append(assistantText);
        }
        return sb.toString();
    }
}

