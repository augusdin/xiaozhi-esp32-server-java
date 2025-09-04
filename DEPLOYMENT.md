# xiaozhi-esp32-server-java 自动部署指南

## 🚀 一键部署

在项目根目录下运行以下命令即可自动部署到远程服务器：

```bash
./deploy.sh
```

## 📋 部署流程

1. **检查Git状态** - 自动提交未保存的更改
2. **推送代码** - 将代码推送到GitHub仓库
3. **远程安装** - 自动安装Docker和Docker Compose（如果未安装）
4. **部署服务** - 拉取代码并启动容器
5. **验证部署** - 检查服务状态

## 🌐 服务访问地址

部署成功后，可以通过以下地址访问服务：

- **前端界面**: http://107.173.38.186:8084
- **后端API**: http://107.173.38.186:8091
- **Portainer管理**: http://107.173.38.186:9000

## 🔧 手动管理命令

### 连接到服务器
```bash
ssh root@107.173.38.186
```

### 查看服务状态
```bash
cd /opt/xiaozhi-deployment/xiaozhi-esp32-server-java
docker-compose ps
```

### 查看日志
```bash
# 实时查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f server
docker-compose logs -f node
docker-compose logs -f mysql
```

### 重启服务
```bash
docker-compose restart
```

### 停止服务
```bash
docker-compose down
```

### 重新部署
```bash
git pull origin main
docker-compose down
docker-compose up -d --build
```

## 🏗️ GitHub Actions自动部署

项目已配置GitHub Actions，每次推送到main分支时会自动触发部署。

### 设置SSH密钥

1. 在GitHub仓库中添加以下Secrets：
   - `SSH_PRIVATE_KEY`: SSH私钥内容

2. 生成SSH密钥对（如果没有）：
   ```bash
   ssh-keygen -t rsa -b 4096 -C "your_email@example.com"
   ```

3. 将公钥添加到服务器：
   ```bash
   ssh-copy-id root@107.173.38.186
   ```

## 🛠️ 系统要求

### 本地环境
- Git
- SSH客户端
- 网络连接到目标服务器

### 远程服务器（会自动安装）
- Ubuntu 18.04+
- Docker
- Docker Compose
- 开放端口：8084, 8091, 9000, 13306

## 📊 端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端Node服务 | 8084 | Web界面访问端口 |
| 后端Java服务 | 8091 | API接口和WebSocket端口 |
| MySQL数据库 | 13306 | 数据库访问端口 |
| Portainer | 9000 | Docker容器管理界面 |

## 🔍 故障排除

### 1. 部署失败
- 检查网络连接
- 确认服务器SSH访问权限
- 查看详细错误日志

### 2. 服务无法访问
- 检查防火墙设置
- 确认端口是否开放
- 查看容器运行状态

### 3. 数据库连接问题
- 等待MySQL容器完全启动（大约1-2分钟）
- 检查MySQL健康状态
- 查看数据库日志

## 📝 开发注意事项

- 每次修改代码后运行 `./deploy.sh` 即可自动部署
- 支持热重载，无需手动重启服务
- 数据库数据持久化，容器重启不会丢失数据
- 日志会自动轮转，避免磁盘空间不足

## 🤝 支持

如有问题，请查看：
1. 服务器日志：`docker-compose logs -f`
2. 系统资源：`htop` 或 `docker stats`
3. 网络连接：`netstat -tlnp`