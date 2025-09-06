# 400错误调试总结 - 模型名称大小写问题

## 问题现象
- 聊天功能返回 "抱歉，我在处理您的请求时遇到了问题"
- 后端日志显示: `400 Bad Request from POST https://aihubmix.com/v1/chat/completions`
- 错误信息: `"Incorrect model ID...GPT-4o"`

## 根本原因
**数据流向理解错误** - 系统不使用"默认配置"，而是通过以下链路获取模型配置：
```
Device → Role → Model Config
test-device → roleId=1 → modelId=4 → configName="GPT-4o" (大写)
```

而 `aihubmix.com` API 要求小写模型名 `gpt-4o`，不接受大写 `GPT-4o`。

## 关键发现
1. **配置获取代码**：
   ```java
   // ChatModelFactory.java:69
   SysRole role = roleService.selectRoleById(device.getRoleId());
   Integer modelId = role.getModelId();  // 关键：使用角色中的modelId
   SysConfig config = configService.selectConfigById(modelId);
   String model = config.getConfigName(); // 使用configName作为模型名
   ```

2. **缓存机制**：Spring Boot 的 `@Cacheable` 导致数据库修改不立即生效

## 修复步骤
1. **确认问题配置**：
   ```sql
   SELECT roleId, modelId FROM sys_role WHERE roleId=1;
   -- 结果：roleId=1, modelId=4 (指向错误的大写配置)
   ```

2. **更新角色配置**：
   ```sql
   UPDATE sys_role SET modelId = 8 WHERE roleId = 1;
   -- 将角色指向正确的小写配置 (configId=8: gpt-4o)
   ```

3. **清除缓存**：
   ```bash
   docker restart xiaozhi-esp32-server-java-server-1
   ```

## 正确的配置链
- **设备**: `test-device`
- **角色**: `roleId=1` → `modelId=8`
- **模型配置**: `configId=8` → `configName="gpt-4o"` (小写)
- **API**: `https://aihubmix.com/v1/chat/completions`

## 教训
1. **追踪完整数据流**：不要假设使用"默认配置"
2. **检查所有配置层级**：Device → Role → Model Config
3. **注意缓存影响**：修改数据库后需要清除Spring缓存
4. **模型名称大小写敏感**：不同API提供商要求可能不同

## 相关文件
- 配置获取：`src/main/java/com/xiaozhi/dialogue/llm/factory/ChatModelFactory.java:69`
- 数据库表：`sys_device`, `sys_role`, `sys_config`
- 缓存注解：`@Cacheable` in `SysConfigServiceImpl.java:130`
