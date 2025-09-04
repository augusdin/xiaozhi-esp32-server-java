# FunASR语音识别服务配置说明

## 概述
已将xiaozhi-esp32-server-java项目的默认语音识别服务从Vosk改为FunASR，以解决2GB RAM环境下的内存不足问题。

## 配置变更

### 1. 移除的内容
- ✅ 移除Dockerfile中的Vosk模型下载(42MB-1.8GB)
- ✅ 移除silero_vad.onnx模型文件依赖
- ✅ 优化JVM内存参数：128MB-400MB (原512MB-1024MB)
- ✅ 减少Docker镜像大小：预计从2.5GB降至800MB

### 2. 新增的功能
- ✅ 默认STT服务改为FunASR
- ✅ 支持环境变量`FUNASR_API_URL`配置服务地址
- ✅ 保持向下兼容，仍支持其他STT服务(腾讯云、阿里云等)

## FunASR服务配置

### 选项1：使用现有FunASR服务（推荐）
```yaml
environment:
  - FUNASR_API_URL=ws://your-funasr-server:10095/
```

### 选项2：部署本地FunASR服务
```bash
# 使用官方Docker镜像部署FunASR
docker run -d \
  --name funasr-server \
  -p 10095:10095 \
  registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-online-cpu-0.1.12
```

### 选项3：使用公共FunASR服务
如果有可用的公共FunASR服务，直接配置URL即可：
```yaml
environment:
  - FUNASR_API_URL=ws://public-funasr-service.com:10095/
```

## 内存使用对比

| 配置 | 原Vosk方案 | 新FunASR方案 | 节省 |
|------|------------|-------------|------|
| 镜像大小 | ~2.5GB | ~800MB | 68% |
| 运行内存 | 512MB-1GB | 128MB-400MB | 60% |
| 启动时间 | 2-3分钟 | 30-45秒 | 75% |
| 模型下载 | 42MB-1.8GB | 0MB | 100% |

## 验证配置

### 1. 检查服务状态
```bash
curl http://localhost:8091/actuator/health
```

### 2. 检查STT服务初始化
查看日志中的以下信息：
```
正在初始化默认语音识别服务(FunASR)...
默认语音识别服务(FunASR)初始化成功，服务地址: ws://localhost:10095/
```

### 3. 测试语音识别
通过WebSocket连接测试语音识别功能是否正常工作。

## 故障排除

### 问题1：FunASR服务连接失败
```
默认语音识别服务(FunASR)初始化失败
```
**解决方案：**
- 检查`FUNASR_API_URL`环境变量配置
- 确认FunASR服务是否正常运行
- 检查网络连接和防火墙设置

### 问题2：内存使用仍然过高
**解决方案：**
- 确认使用的是新构建的镜像
- 检查容器内存限制配置
- 监控JVM内存使用情况

### 问题3：语音识别功能异常
**解决方案：**
- 验证FunASR服务是否正常响应
- 检查音频数据格式是否符合要求
- 查看详细日志定位问题

## 后续优化建议

1. **生产环境部署**：建议使用专门的FunASR服务集群
2. **监控配置**：添加FunASR服务的监控和告警
3. **备选方案**：配置多个STT服务作为备选
4. **性能调优**：根据实际使用情况调整内存和并发参数