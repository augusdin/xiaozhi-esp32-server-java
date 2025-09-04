#!/bin/bash

# 测试远程连接并执行初始部署的脚本
echo "🔧 正在测试远程连接并执行初始部署..."

# 测试SSH连接
echo "📡 测试SSH连接..."
if ssh -o ConnectTimeout=10 -o BatchMode=yes root@107.173.38.186 exit; then
    echo "✅ SSH连接成功"
else
    echo "❌ SSH连接失败，请检查："
    echo "   1. 服务器IP是否正确: 107.173.38.186"
    echo "   2. SSH密钥是否已配置"
    echo "   3. 服务器是否在线"
    echo ""
    echo "💡 如需使用密码登录，请运行:"
    echo "   ssh root@107.173.38.186"
    echo "   密码: 43XBj2JHRqlfHd35f4"
    exit 1
fi

echo "🚀 开始远程部署流程..."

ssh root@107.173.38.186 << 'DEPLOY_SCRIPT'
set -e

echo "🔍 系统信息:"
uname -a
echo ""

echo "📦 检查并安装Docker..."
if ! command -v docker &> /dev/null; then
    echo "🔧 安装Docker..."
    apt update
    apt install -y curl
    curl -fsSL https://get.docker.com -o get-docker.sh
    sh get-docker.sh
    systemctl enable docker
    systemctl start docker
    echo "✅ Docker安装完成"
else
    echo "✅ Docker已安装"
fi

echo "🔧 检查并安装Docker Compose..."
if ! command -v docker-compose &> /dev/null; then
    echo "📦 安装Docker Compose..."
    curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
    ln -sf /usr/local/bin/docker-compose /usr/bin/docker-compose
    echo "✅ Docker Compose安装完成"
else
    echo "✅ Docker Compose已安装"
fi

echo "📊 安装Portainer..."
if ! docker ps -a | grep -q portainer; then
    docker volume create portainer_data 2>/dev/null || true
    docker run -d -p 9000:9000 --name portainer --restart=always \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -v portainer_data:/data \
        portainer/portainer-ce:latest
    echo "✅ Portainer已启动，访问: http://107.173.38.186:9000"
else
    echo "✅ Portainer已运行"
fi

echo "📁 创建部署目录..."
mkdir -p /opt/xiaozhi-deployment
cd /opt/xiaozhi-deployment

echo "📥 准备项目部署..."
echo "   - 部署目录: /opt/xiaozhi-deployment"
echo "   - Docker版本: $(docker --version)"
echo "   - Docker Compose版本: $(docker-compose --version)"

echo "🎉 服务器环境准备完成！"
echo ""
echo "🌐 接下来的步骤："
echo "   1. 在GitHub上创建仓库: https://github.com/new"
echo "      仓库名: xiaozhi-esp32-server-java"
echo "   2. 推送代码到GitHub"
echo "   3. 运行 ./deploy.sh 进行部署"

DEPLOY_SCRIPT

echo ""
echo "✅ 远程服务器环境准备完成！"
echo ""
echo "📝 下一步操作："
echo "   1. 在GitHub创建新仓库: https://github.com/new"
echo "      - 仓库名: xiaozhi-esp32-server-java"  
echo "      - 设为公开仓库"
echo "   2. 运行以下命令推送代码:"
echo "      git push -u origin main"
echo "   3. 运行部署脚本:"
echo "      ./deploy.sh"