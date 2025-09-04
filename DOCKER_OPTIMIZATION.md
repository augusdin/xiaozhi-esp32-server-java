# Docker 多阶段构建优化说明

## 优化概述

通过重构Dockerfile-server，实现了4阶段多阶段构建，显著减少了最终镜像大小并提高了构建效率。

## 优化内容

### 1. 四阶段构建架构

**Stage 1: Maven Dependencies Cache (deps-builder)**
- 单独处理Maven依赖下载
- 利用Docker层缓存，只有pom.xml变化时才重新下载依赖
- 优化构建速度

**Stage 2: Model Downloader (model-downloader)**  
- 使用轻量级Alpine镜像下载Vosk模型
- 默认使用small模型(42MB)替代standard模型(1.8GB)
- 与应用构建并行进行

**Stage 3: Application Builder (app-builder)**
- 复用Stage 1的Maven依赖缓存
- 专注于应用编译，跳过测试加速构建
- 使用静默模式减少构建日志

**Stage 4: Runtime Environment (runtime)**
- 使用Alpine JRE镜像，大幅减少基础镜像大小
- 只复制运行时必需的文件
- 添加健康检查和非root用户运行

### 2. 性能优化

**JVM参数优化:**
- 内存使用：256MB-768MB (原512MB-1024MB)
- 使用G1GC垃圾收集器
- 启用JVMCI编译器提升性能

**安全增强:**
- 创建专用xiaozhi用户运行应用
- 非root权限运行
- 添加健康检查机制

### 3. 预期效果

| 指标 | 优化前 | 优化后 | 改进 |
|------|--------|--------|------|
| 镜像大小 | ~2.5GB | ~800MB | ↓70% |
| 构建时间 | ~15分钟 | ~5分钟 | ↓67% |
| 内存占用 | 512MB-1GB | 256MB-768MB | ↓25% |
| 模型大小 | 1.8GB | 42MB | ↓97% |

### 4. 配置变更

**docker-compose.yml优化:**
- 移除不必要的volume挂载
- 优化缓存策略
- 添加健康检查
- 改进重启策略

**默认配置:**
- 默认使用small Vosk模型
- 可通过环境变量VOSK_MODEL_SIZE切换

## 使用方法

**构建镜像:**
```bash
docker-compose build server
```

**使用标准模型:**
```bash
VOSK_MODEL_SIZE=standard docker-compose build server
```

**运行服务:**
```bash
docker-compose up -d
```

## 回滚方法

如需回滚到原始版本：
```bash
cp backup/docker-original/* .
```