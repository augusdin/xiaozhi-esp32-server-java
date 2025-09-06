## Langfuse LLM 追踪 — 当前状态（清晰版）

### 目标
- 只上报 LLM 调用（含 prompt 与 completion），不上报 HTTP 健康检查等噪音。

### 当前现象
- 应用日志已明确存在 LLM 调用（ChatModel.stream + OpenAI 200 OK）。
- Langfuse 面板暂无 LLM 数据（之前出现过 HTTP 健康检查，现已关闭）。
- 日志中未出现 OpenTelemetry/OTLP 初始化相关输出，怀疑导出链路未成功启动。

### 已完成的关键改动（按时间）
1) 移除手工 HTTP 集成，切换到 OpenTelemetry + Spring AI；修复编译/告警，应用可启动。
2) 统一依赖为：
   - opentelemetry-spring-boot-starter（初始化 OTel）
   - micrometer-tracing-bridge-otel（Micrometer → OTel）
   - opentelemetry-exporter-otlp（导出到 Langfuse）
3) 仅导出 traces，关闭 logs/metrics，修复早期 404（logs 导出）问题。
4) 精确关闭 HTTP/JDBC 自动探针，保留 Spring AI 观测；关闭管理端 HTTP 观测（健康检查不再进入链路）。
5) 在 docker-compose 注入 OTEL_*（endpoint、headers、service.name、禁用 HTTP/JDBC），容器重建成功。
6) 为防导出链路被误关，新增 Actuator 级 OTLP 导出（management.otlp.tracing.*）作为兜底通道。

### 现在的配置要点
- traces-only，logs/metrics 关闭；`service.name=xiaozhi-llm`。
- Spring AI ObservationFilter 写入 `gen_ai.prompt` 与 `gen_ai.completion`。
- 关闭所有通用 HTTP/JDBC 自动探针与管理端 HTTP 观测。
- 导出“双通道”并行：
  - OTel SDK → OTLP（Langfuse）
  - Micrometer（Actuator）→ OTLP（Langfuse）

### 下一步需要确认（部署完成后）
1) 启动日志中是否出现 OTel/Micrometer OTLP 初始化信息（任一通道出现即可）。
2) 触发一次 LLM 对话，Langfuse 中应看到 LLM 观测，且 `input/output` 字段由 `gen_ai.*` 填充。


