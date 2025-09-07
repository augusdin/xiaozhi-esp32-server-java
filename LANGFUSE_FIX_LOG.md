# Langfuse 集成修复记录（详细版）

本文档记录本次修复 Langfuse 在 `xiaozhi-esp32-server-java` 项目中的问题排查过程、根因分析、具体改动和验证方法，便于团队成员复盘与参考。

## 一、问题背景与目标
- 目标：仅上报 LLM 调用（包含 prompt 与 completion），不关心 HTTP 健康检查等噪音。
- 现象：
  - 应用日志确认存在 LLM 调用（`ChatModel.stream`、OpenAI 200 等），
  - Langfuse 面板无 LLM 数据（之前出现过 HTTP 健康检查，现已关闭）。
  - 日志未见 OTel/OTLP 初始化明显输出，怀疑导出链路未生效或端点错误。

## 二、排查与根因定位
1) CI 编译失败导致无法部署新改动
   - 通过 `gh run view` 查看构建日志发现编译错误：
     - `ChatModelObservationContext` 不存在 `getProvider()` 方法。
   - 结论：代码与 Spring AI 1.0.0 API 不匹配，需修正。

2) LLM 观察数据未进入 Langfuse
   - 端点配置错误：使用了 `/api/public/otel`，而 Langfuse OTel HTTP 端点为 `/api/public/otel/v1/traces`。
   - 协议错误/不明确：未显式设置 `http/protobuf`，可能与 Langfuse 期望不匹配。
   - 流式调用未回填响应：`ChatModel.stream()` 没有将最终聚合响应写回 `ObservationContext`，导致 `gen_ai.prompt`/`gen_ai.completion` 标签无法正确生成。

3) 观测过滤器未能提供可诊断信号
   - 过滤器日志过少，不易判断是否被触发以及输入输出是否被采集。

## 三、修复内容（按模块）

### 1. 修复编译错误与增强观察（ChatService）
- 文件：`src/main/java/com/xiaozhi/dialogue/llm/ChatService.java`
- 变更点：
  - 对流式调用增加 Spring AI 观察封装：
    - 使用 `ChatModelObservationDocumentation.CHAT_MODEL_OPERATION` 启动 Observation。
    - 使用 `MessageAggregator` 聚合流式响应，并通过 `observationContext.setResponse(...)` 将最终响应写回观察上下文。
    - 在 `doOnError`/`doOnTerminate` 中标注错误与结束，确保 Span 正常闭合。
  - 对同步调用（`chatModel.call`）也增加 Observation 包裹，并在成功后 `setResponse`。
  - 增加 `resolveProviderName` 助手用于提供商名称判定（非强依赖，仅用于元信息标注）。
- 目的：
  - 保障 Spring AI 生成的 Observation 覆盖流式与同步路径。
  - 确保 `gen_ai.prompt`/`gen_ai.completion` 能从聚合后的响应中被过滤器读取并作为高基数标签输出。

### 2. 修复过滤器编译与增强日志（ChatModelObservationFilter）
- 文件：`src/main/java/com/xiaozhi/integration/langfuse/ChatModelObservationFilter.java`
- 变更点：
  - 移除对不存在的 `getProvider()` 调用，兼容 Spring AI 1.0.0。
  - 增加统计日志：打印被观察到的 prompt/response 的字符总数，方便判断过滤器是否被触发以及是否采集到内容。
- 目的：
  - 解决编译失败问题。
  - 提供运行时诊断信号，便于在无法直观查看 Langfuse 面板的情况下，通过日志判断链路是否工作。

### 3. 更正 OTLP 端点与协议（application.properties）
- 文件：`src/main/resources/application.properties`
- 变更点：
  - `otel.exporter.otlp.protocol=http/protobuf`
  - `otel.exporter.otlp.endpoint=http://198.12.104.212:3000/api/public/otel/v1/traces`
  - `management.otlp.tracing.endpoint=http://198.12.104.212:3000/api/public/otel/v1/traces`
- 目的：
  - 对齐 Langfuse 的 OTel HTTP 接口（路径与协议）。
  - 确保 SDK（OTel）与 Micrometer（Actuator）的双通道导出均指向正确的端点。

### 4. 增强 CI 可观察性（Dockerfile）
- 文件：`Dockerfile-server`
- 变更点：
  - 构建阶段移除 Maven `-q` 静默参数，改用 `-e` 输出错误堆栈。
- 目的：
  - CI 失败时能清晰看到具体编译错误，缩短定位时间。

## 四、CI/部署操作与监控
- 分支策略：
  - 创建备份分支：`backup/main-<timestamp>`。
  - 开发分支：`feature/langfuse-otel-fix`，完成后合并至 `main` 触发自动部署。
- CI 监控策略（每 3 分钟检查一次，最长 15 分钟）：
  - `gh run list --limit 1`
  - `gh run view <run-id> --json status,conclusion`
  - 若 15 分钟仍未完成或失败，分析日志修复或中断重新触发。
- 本次部署结果：
  - 首次失败（编译错误：`getProvider()` 不存在），修复后两次构建成功并部署。

## 五、运行时验证方法
1) 验证 Langfuse OTLP 端点连通性（从 186 主机）：
   - GET `http://198.12.104.212:3000/api/public/otel/v1/traces` → 405（预期，需 POST）。
   - POST（含权限头与 Content-Type）空 JSON → 200：
     ```bash
     curl -s -o /dev/null -w "%{http_code}\n" \
       -X POST \
       -H "Authorization: Basic <base64(pk:sk)>" \
       -H "Content-Type: application/json" \
       --data "{}" \
       http://198.12.104.212:3000/api/public/otel/v1/traces
     ```

2) 验证应用日志（186 主机）：
   - 触发一次 LLM 请求（正常业务调用或 `/vl/chat` 携带有效 session）。
   - 观察日志：
     - `[LangfuseFilter] observed promptChars=..., completionChars=...`
     - `=== Calling ChatModel.stream() ===` 与提供商 HTTP 请求/响应日志（如使用 OpenAI 协议）。
   - 查询日志命令：
     ```bash
     ssh -i ~/.ssh/xiaozhi_deploy root@107.173.38.186 'docker logs -f xiaozhi-esp32-server-java-server-1'
     ```

## 六、过往问题与本次修复对照
- 过往问题：
  - CI 编译失败导致无法上线（过滤器 API 不兼容）。
  - Langfuse 无 LLM 数据（OTLP 端点/协议错误；流式未回填响应）。
  - 难以通过日志判断观察链路是否有效。
- 本次修复：
  - 修正过滤器 API 调用，恢复编译与部署。
  - 明确配置 Langfuse OTel HTTP 端点与协议，保证导出链路正确。
  - 对 `call` 与 `stream` 双路径补齐 Observation 封装与响应回填，确保 `gen_ai.*` 标签生成。
  - 增加过滤器统计日志，便于离线验证链路。

## 七、潜在改进（可选）
- 临时添加 `/debug/llm` 端点，仅用于触发一次最小 LLM 调用以验证链路，验证后删除。
- 启动时打印 OTLP 关键配置（如端点、协议）以便审计。
- 若后续升级 Spring AI 版本，可考虑使用更新的 Observation API 能力，但需对照 API 变更。

---
如需我继续添加调试端点或扩大日志，请告知具体需求与保留时长。

