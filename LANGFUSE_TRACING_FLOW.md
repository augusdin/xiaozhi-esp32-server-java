# Langfuse 完整对话链路追踪图

## 总体架构流程

```mermaid
graph TB
    %% 用户输入层
    A[用户语音输入] --> B[VAD 语音活动检测]
    A1[用户文本输入] --> B1[文本输入处理]
    
    %% 语音处理层
    B --> C[STT 语音转文本]
    B1 --> D[消息预处理]
    C --> D
    
    %% 核心处理层
    D --> E[ChatService 处理]
    E --> F[记忆系统检索]
    F --> G[LLM 模型调用]
    G --> H[工具函数调用]
    H --> I[响应生成]
    
    %% 输出处理层
    I --> J[句子分割处理]
    J --> K[TTS 文本转语音]
    K --> L[音频合成]
    L --> M[最终音频输出]
    
    %% Langfuse 追踪层
    subgraph "Langfuse 追踪点"
        T1[Trace: 对话会话]
        S1[Span: STT处理]
        S2[Span: Chat处理]
        G1[Generation: LLM生成]
        S3[Span: TTS处理]
        S4[Span: 工具调用]
    end
    
    %% 追踪连接
    C -.-> S1
    E -.-> S2
    G -.-> G1
    H -.-> S4
    K -.-> S3
    
    S1 --> T1
    S2 --> T1
    G1 --> T1
    S3 --> T1
    S4 --> T1
    
    style T1 fill:#e1f5fe
    style S1 fill:#f3e5f5
    style S2 fill:#f3e5f5
    style G1 fill:#e8f5e8
    style S3 fill:#f3e5f5
    style S4 fill:#f3e5f5
```

## 详细技术流程图

```mermaid
graph LR
    %% 输入阶段
    subgraph "输入处理"
        A1[音频输入] --> A2[VAD检测]
        A3[文本输入] --> A4[直接处理]
        A2 --> A5[STT处理]
    end
    
    %% LLM处理阶段
    subgraph "LLM处理"
        B1[ChatService.chat] --> B2[消息构建]
        B11[ChatService.chatStreamBySentence] --> B2
        B2 --> B3[记忆注入]
        B3 --> B4[模型调用]
        B4 --> B5[响应处理]
    end
    
    %% 输出阶段
    subgraph "输出处理"
        C1[句子分割] --> C2[TTS任务]
        C2 --> C3[音频生成]
        C3 --> C4[音频输出]
    end
    
    %% Langfuse追踪
    subgraph "Langfuse追踪"
        direction TB
        D1[🔍 Trace创建<br/>会话级追踪]
        D2[📊 STT Span<br/>语音识别追踪]
        D3[💬 Chat Span<br/>聊天处理追踪]
        D4[🤖 LLM Generation<br/>模型生成追踪]
        D5[🔧 Tool Span<br/>工具调用追踪]
        D6[🔊 TTS Span<br/>语音合成追踪]
        D7[✅ Trace结束<br/>会话完成]
        
        D1 --> D2
        D1 --> D3
        D1 --> D4
        D1 --> D5
        D1 --> D6
        D1 --> D7
    end
    
    %% 连接关系
    A5 --> B1
    A5 --> B11
    A4 --> B1
    A4 --> B11
    B5 --> C1
    
    %% 追踪连接
    A2 -.-> D2
    B1 -.-> D3
    B11 -.-> D3
    B4 -.-> D4
    B4 -.-> D5
    C2 -.-> D6
    C4 -.-> D7
    
    style D1 fill:#e3f2fd
    style D2 fill:#fff3e0
    style D3 fill:#f1f8e9
    style D4 fill:#fce4ec
    style D5 fill:#e8eaf6
    style D6 fill:#e0f2f1
    style D7 fill:#fff8e1
```

## 追踪数据结构

```mermaid
graph TD
    %% Trace结构
    subgraph "Trace (会话级)"
        T[Trace ID]
        T --> TM[Metadata<br/>• sessionId<br/>• deviceId<br/>• roleId<br/>• startTime]
    end
    
    %% STT Span
    subgraph "STT Span"
        S1[STT Span ID]
        S1 --> S1M[Metadata<br/>• provider<br/>• audioSize<br/>• duration<br/>• textLength]
        S1 --> S1I[Input: 音频数据]
        S1 --> S1O[Output: 识别文本]
    end
    
    %% Chat Span
    subgraph "Chat Span"
        S2[Chat Span ID]
        S2 --> S2M[Metadata<br/>• method<br/>• useFunctionCall<br/>• messagesCount]
        S2 --> S2I[Input: 用户消息]
        S2 --> S2O[Output: 处理状态]
    end
    
    %% LLM Generation
    subgraph "LLM Generation"
        G[Generation ID]
        G --> GM[Metadata<br/>• model<br/>• duration<br/>• temperature<br/>• topP]
        G --> GI[Input: 消息列表]
        G --> GO[Output: 生成内容]
        G --> GU[Usage<br/>• promptTokens<br/>• completionTokens<br/>• totalTokens]
    end
    
    %% TTS Span
    subgraph "TTS Span"
        S3[TTS Span ID]
        S3 --> S3M[Metadata<br/>• provider<br/>• voiceName<br/>• sequence<br/>• isFirst/isLast]
        S3 --> S3I[Input: 文本内容]
        S3 --> S3O[Output: 音频路径]
    end
    
    %% 关系连接
    T -.-> S1
    T -.-> S2
    T -.-> G
    T -.-> S3
    
    style T fill:#e1f5fe
    style S1 fill:#fff3e0
    style S2 fill:#f1f8e9
    style G fill:#fce4ec
    style S3 fill:#e0f2f1
```

## 性能监控指标

```mermaid
graph LR
    subgraph "性能指标监控"
        A[响应时间指标]
        B[资源使用指标]
        C[质量指标]
        D[业务指标]
        
        A --> A1[STT 处理时间]
        A --> A2[LLM 响应时间]
        A --> A3[TTS 生成时间]
        A --> A4[端到端延迟]
        
        B --> B1[Token 使用量]
        B --> B2[音频文件大小]
        B --> B3[内存使用]
        B --> B4[并发处理数]
        
        C --> C1[识别准确率]
        C --> C2[生成质量]
        C --> C3[错误率]
        C --> C4[重试次数]
        
        D --> D1[会话时长]
        D --> D2[功能使用率]
        D --> D3[用户满意度]
        D --> D4[设备分布]
    end
    
    style A fill:#e3f2fd
    style B fill:#e8f5e8
    style C fill:#fff3e0
    style D fill:#fce4ec
```

## 追踪配置说明

### 基础配置
```properties
# Langfuse 主配置
langfuse.enabled=true
langfuse.host=http://198.12.104.212:3000
langfuse.secret-key=sk-lf-5a89772f-10f4-4173-956d-1378d638eaf9
langfuse.public-key=pk-lf-e4194da0-96ee-4bf5-a556-fc3ca29d6ec4

# 性能配置
langfuse.timeout-ms=5000
langfuse.batch-size=10
langfuse.flush-interval-ms=1000
```

### 环境变量支持
- `LANGFUSE_ENABLED` - 启用/禁用追踪
- `LANGFUSE_HOST` - Langfuse 服务地址  
- `LANGFUSE_SECRET_KEY` - 私钥
- `LANGFUSE_PUBLIC_KEY` - 公钥

## 集成组件说明

### 核心组件
1. **LangfuseClient** - HTTP API 客户端，负责与 Langfuse 服务通信
2. **LangfuseService** - 高级服务接口，提供追踪功能
3. **TraceContext** - 追踪上下文管理，维护会话状态
4. **LangfuseProperties** - 配置属性管理

### 集成点
1. **ChatService** - LLM 调用和响应追踪
2. **DialogueService** - STT、TTS 和完整对话流程追踪
3. **工具调用** - Function calling 的追踪和监控

### 数据流向
```
用户输入 → STT追踪 → Chat追踪 → LLM生成追踪 → TTS追踪 → 输出完成
    ↓         ↓         ↓           ↓            ↓         ↓
  Langfuse Trace ← Span ← Span ← Generation ← Span ← 结束追踪
```

## 使用示例

### 查看追踪数据
1. 访问 Langfuse Web 界面：http://198.12.104.212:3000
2. 使用提供的公钥和私钥登录
3. 在 Traces 页面查看完整的对话链路
4. 在 Generations 页面分析 LLM 性能
5. 在 Dashboard 页面查看总体指标

### 故障排查
- 检查日志中的 Langfuse 相关信息
- 验证网络连接到 Langfuse 服务
- 确认配置文件中的密钥正确性
- 查看 Langfuse 服务状态

---

**🎯 此图表展示了完整的 xiaozhi-esp32-server-java 项目中 Langfuse 追踪集成的全貌，包含所有关键追踪点和数据流向。**
