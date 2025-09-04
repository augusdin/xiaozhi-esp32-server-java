#!/bin/bash

# 本地一键部署脚本
# 在xiaozhi-esp32-server-java项目目录下运行此脚本即可自动部署到远程服务器

set -e

echo "🚀 xiaozhi-esp32-server-java 一键部署脚本"
echo "===================================="

# 检查是否在正确的目录
if [ ! -f "docker-compose.yml" ]; then
    echo "❌ 错误: 请在xiaozhi-esp32-server-java项目根目录下运行此脚本"
    echo "   当前目录应该包含docker-compose.yml文件"
    exit 1
fi

# 检查git状态
if [ -d ".git" ]; then
    echo "📝 检查Git状态..."
    
    # 检查是否有未提交的更改
    if ! git diff-index --quiet HEAD --; then
        echo "⚠️  检测到未提交的更改，正在提交..."
        git add .
        git commit -m "Auto commit before deployment - $(date '+%Y-%m-%d %H:%M:%S')"
    fi
    
    echo "📤 推送代码到GitHub..."
    git push origin main 2>/dev/null || git push origin master 2>/dev/null || {
        echo "⚠️  推送失败，可能需要先创建远程仓库或检查权限"
        echo "   请手动创建GitHub仓库: https://github.com/new"
        echo "   仓库名: xiaozhi-esp32-server-java"
        read -p "   创建完成后按回车继续..."
    }
else
    echo "⚠️  当前目录不是Git仓库，跳过Git操作"
fi

echo "🌐 开始远程部署..."

# 远程部署
ssh -o StrictHostKeyChecking=no root@107.173.38.186 << 'EOF'
set -e

echo "🔧 准备部署环境..."

# 安装Docker（如果未安装）
if ! command -v docker &> /dev/null; then
    echo "📦 安装Docker..."
    apt update
    apt install -y apt-transport-https ca-certificates curl software-properties-common
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -
    add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
    apt update && apt install -y docker-ce docker-ce-cli containerd.io
    systemctl start docker && systemctl enable docker
fi

# 安装Docker Compose（如果未安装）
if ! command -v docker-compose &> /dev/null; then
    echo "📦 安装Docker Compose..."
    curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
fi

# 创建部署目录
mkdir -p /opt/xiaozhi-deployment
cd /opt/xiaozhi-deployment

# 克隆或更新代码
if [ -d "xiaozhi-esp32-server-java" ]; then
    echo "🔄 更新项目代码..."
    cd xiaozhi-esp32-server-java
    git pull origin main || git pull origin master
else
    echo "📥 克隆项目代码..."
    git clone https://github.com/augusdin/xiaozhi-esp32-server-java.git
    cd xiaozhi-esp32-server-java
fi

# 停止现有服务
echo "🛑 停止现有服务..."
docker-compose down 2>/dev/null || true

# 启动服务
echo "🚀 启动服务..."
docker-compose up -d --build

# 等待服务启动
echo "⏳ 等待服务启动..."
sleep 30

# 检查状态
echo "📊 服务状态:"
docker-compose ps

echo ""
echo "🎉 部署完成！"
echo "🌐 访问地址:"
echo "   前端: http://107.173.38.186:8084"
echo "   后端: http://107.173.38.186:8091"

EOF

echo ""
echo "✅ 部署完成！服务正在远程服务器上运行"
echo "🔍 查看服务状态: ssh root@107.173.38.186 'cd /opt/xiaozhi-deployment/xiaozhi-esp32-server-java && docker-compose ps'"
echo "📋 查看日志: ssh root@107.173.38.186 'cd /opt/xiaozhi-deployment/xiaozhi-esp32-server-java && docker-compose logs -f'"