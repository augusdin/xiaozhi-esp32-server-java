# xiaozhi-esp32-server-java 项目分析

## 项目结构分析

### 核心架构
- **基础框架**: Spring Boot 3.3.0 + Java 21
- **AI框架**: Spring AI 1.0.0 (支持 Ollama, OpenAI, ZhipuAI)
- **数据库**: MySQL + MyBatis
- **WebSocket**: 实时通信支持
- **语音处理**: 集成多个TTS/STT服务商(阿里云、腾讯云、讯飞等)

### 核心组件

#### 1. 实体层 (Entity)
- `SysDevice`: 设备管理
- `SysRole`: 角色配置 (包含roleDesc作为系统提示词)
- `SysTemplate`: 提示词模板管理
- `SysMessage`: 消息存储
- `SysConfig`: 系统配置

#### 2. 对话管理层
- `ChatService`: 核心聊天服务，管理LLM交互
- `ChatMemory`: 消息历史管理
- `Conversation`: 对话抽象，代表device+role+session的会话
- `MessageWindowConversation`: 实现消息窗口限制的对话管理

#### 3. LLM集成层
- `ChatModelFactory`: 聊天模型工厂
- 支持多个模型提供商的集成
- MCP (Model Context Protocol) 工具调用支持

## Prompt 拼接链路分析

### 关键流程路径
```
用户输入 → ChatService.chatStream() → MessageWindowConversation.prompt() → LLM模型
```

### 详细拼接流程

#### 1. 入口点 (`ChatService.java:121-137`)
```java
// 获取ChatModel和配置选项
ChatModel chatModel = chatModelFactory.takeChatModel(session);
ChatOptions chatOptions = ToolCallingChatOptions.builder()...

// 创建用户消息
UserMessage userMessage = new UserMessage(message);

// 获取完整的消息列表 (关键步骤)
List<Message> messages = session.getConversation().prompt(userMessage);

// 构建Prompt
Prompt prompt = new Prompt(messages, chatOptions);
```

#### 2. Prompt构建核心 (`MessageWindowConversation.java:125-140`)
```java
public List<Message> prompt(UserMessage userMessage) {
    // 1. 从Role获取系统提示词
    String roleDesc = role().getRoleDesc();
    SystemMessage systemMessage = new SystemMessage(StringUtils.hasText(roleDesc)?roleDesc:"");
    
    // 2. 获取历史消息并限制数量
    final var historyMessages = messages();
    while (historyMessages.size() > maxMessages) {
        historyMessages.remove(0);  // 删除最早的消息
    }
    
    // 3. 按顺序构建最终消息列表
    List<Message> messages = new ArrayList<>();
    messages.add(systemMessage);        // 系统消息(角色描述)
    messages.addAll(historyMessages);   // 历史对话
    messages.add(userMessage);          // 当前用户输入
    
    return messages;
}
```

#### 3. 消息持久化和管理
- 用户消息和助手回复会异步持久化到数据库
- 工具调用(function calls)的消息不会加入对话历史记忆
- 支持不同消息类型：普通消息、函数调用消息

### Prompt结构总结
最终发送给LLM的消息顺序：
1. **SystemMessage**: 来自 `SysRole.roleDesc` 字段
2. **历史对话**: 从数据库加载的历史消息（用户+助手消息对）
3. **UserMessage**: 当前用户输入

### 集成点分析
- **角色系统**: 通过 `SysRole.roleDesc` 设置系统提示词
- **模板系统**: `SysTemplate` 提供提示词模板管理
- **配置系统**: `SysConfig` 管理各种系统配置
- **工具调用**: 支持MCP协议的工具调用，但工具调用消息不进入对话历史

## 集成建议

### MemOS 集成策略
1. **系统提示词增强**: 在 `MessageWindowConversation.prompt()` 中集成MemOS的上下文
2. **配置管理**: 通过 `SysConfig` 管理MemOS API配置
3. **异步调用**: 利用现有的异步框架调用MemOS服务

### mem0 集成策略  
1. **长期记忆**: 在消息持久化时同步更新mem0
2. **记忆检索**: 在prompt构建时从mem0检索相关记忆
3. **配置统一**: 与MemOS使用相同的配置管理模式

### 技术兼容性
- 项目已集成HTTP客户端 (OkHttp 5.0)
- 支持异步处理和流式响应
- 具备良好的配置管理和服务抽象
- 可以通过现有的工具调用机制集成外部Python服务