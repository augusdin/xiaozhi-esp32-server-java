# GitHub Secrets 配置说明

为了使CI/CD流程正常工作，需要在GitHub仓库中配置以下Secrets：

## 必需的Secrets

1. **HOST_IP** - 远程服务器IP地址
   - 值: `107.173.38.186`

2. **HOST_USER** - 远程服务器用户名  
   - 值: `root`

3. **HOST_PASSWORD** - 远程服务器密码
   - 值: `43XBj2JHRqlfHd35f4`

## 配置步骤

1. 进入GitHub仓库: https://github.com/augusdin/xiaozhi-esp32-server-java
2. 点击 **Settings** 选项卡
3. 在左侧菜单中选择 **Secrets and variables** → **Actions**
4. 点击 **New repository secret** 按钮
5. 逐个添加上述三个secrets

## CI/CD 工作流程

1. **触发条件**: 推送代码到main分支时自动执行
2. **构建步骤**: 使用Dockerfile-server构建Docker镜像
3. **推送镜像**: 推送到GitHub Container Registry (ghcr.io)
4. **部署步骤**: SSH登录远程服务器，拉取最新镜像并启动容器

## 手动触发

也可以在GitHub Actions页面手动触发部署：
1. 进入 **Actions** 选项卡
2. 选择 **Deploy to Remote Server** workflow
3. 点击 **Run workflow** 按钮

## 验证部署

部署完成后，可以通过以下方式验证：
- 访问: http://107.173.38.186:8080 (主服务)
- 访问: http://107.173.38.186:8181 (WebSocket服务)

## 注意事项

- GitHub Container Registry使用GitHub Token自动认证，无需额外配置
- 镜像会自动推送到: ghcr.io/augusdin/xiaozhi-esp32-server-java:main
- 容器名称: xiaozhi-server
- 自动重启策略: unless-stopped