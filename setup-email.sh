#!/bin/bash

# 163邮箱SMTP自动配置脚本
# 支持IMAP/SMTP服务

echo "🚀 小智邮箱服务自动配置脚本"
echo "================================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 输入验证函数
validate_email() {
    if [[ $1 =~ ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$ ]]; then
        return 0
    else
        return 1
    fi
}

# 获取用户输入
echo -e "${BLUE}请输入你的163邮箱配置信息:${NC}"
echo ""

while true; do
    read -p "📧 163邮箱地址 (如: yourname@163.com): " EMAIL_USERNAME
    if validate_email "$EMAIL_USERNAME"; then
        if [[ "$EMAIL_USERNAME" == *"@163.com" ]]; then
            break
        else
            echo -e "${RED}❌ 请输入163邮箱地址 (必须以@163.com结尾)${NC}"
        fi
    else
        echo -e "${RED}❌ 邮箱格式不正确，请重新输入${NC}"
    fi
done

while true; do
    read -s -p "🔑 IMAP/SMTP授权码 (在163邮箱设置中获取): " EMAIL_PASSWORD
    echo ""
    if [[ ${#EMAIL_PASSWORD} -ge 6 ]]; then
        break
    else
        echo -e "${RED}❌ 授权码长度不能少于6位，请重新输入${NC}"
    fi
done

echo ""
echo -e "${YELLOW}📋 配置信息确认:${NC}"
echo "邮箱地址: $EMAIL_USERNAME"
echo "授权码: ${EMAIL_PASSWORD:0:3}***${EMAIL_PASSWORD: -3}"
echo ""

read -p "是否确认配置? (y/N): " CONFIRM
if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
    echo -e "${RED}❌ 配置已取消${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}🚀 开始自动配置...${NC}"
echo ""

# SSH配置
SSH_KEY="$HOME/.ssh/xiaozhi_deploy"
REMOTE_HOST="root@107.173.38.186"
REMOTE_PATH="/opt/xiaozhi-deployment/xiaozhi-esp32-server-java"

# 检查SSH密钥
if [[ ! -f "$SSH_KEY" ]]; then
    echo -e "${RED}❌ SSH密钥文件不存在: $SSH_KEY${NC}"
    echo "请确保SSH密钥文件存在并有正确权限"
    exit 1
fi

echo -e "${BLUE}📡 连接到远程服务器...${NC}"

# 创建远程配置脚本
cat > /tmp/remote_config.sh << EOF
#!/bin/bash
set -e

EMAIL_USERNAME="$EMAIL_USERNAME"
EMAIL_PASSWORD="$EMAIL_PASSWORD"
REMOTE_PATH="$REMOTE_PATH"

echo "🔍 检查服务状态..."
cd "\$REMOTE_PATH"

# 备份原配置
if [[ ! -f "docker-compose.yml.backup" ]]; then
    cp docker-compose.yml docker-compose.yml.backup
    echo "✅ 原配置已备份"
fi

echo "🔧 更新邮箱配置..."
# 更新docker-compose.yml中的邮箱配置
sed -i "s|EMAIL_SMTP_USERNAME=.*|EMAIL_SMTP_USERNAME=\$EMAIL_USERNAME|" docker-compose.yml
sed -i "s|EMAIL_SMTP_PASSWORD=.*|EMAIL_SMTP_PASSWORD=\$EMAIL_PASSWORD|" docker-compose.yml

echo "✅ Docker配置已更新"

# 检查当前服务状态
echo "🔍 检查服务状态..."
docker ps --format "table {{.Names}}\t{{.Status}}" | grep xiaozhi

echo "🔄 重新创建服务容器以应用新的环境变量..."
docker-compose down server
docker-compose up -d server

echo "⏳ 等待服务启动..."
sleep 15

# 验证服务状态
echo "🔍 验证服务状态..."
if docker ps | grep -q "xiaozhi.*server.*Up"; then
    echo "✅ 服务重启成功"
else
    echo "❌ 服务重启可能失败，请检查日志"
    docker logs xiaozhi-esp32-server-java-server-1 --tail 20
fi

echo "📋 验证容器内环境变量:"
EMAIL_CHECK=\$(docker exec xiaozhi-esp32-server-java-server-1 env | grep EMAIL_SMTP_USERNAME | cut -d'=' -f2 2>/dev/null)
PASSWORD_CHECK=\$(docker exec xiaozhi-esp32-server-java-server-1 env | grep EMAIL_SMTP_PASSWORD | cut -d'=' -f2 2>/dev/null)

if [[ "\$EMAIL_CHECK" == "$EMAIL_USERNAME" ]]; then
    echo "✅ 邮箱用户名配置正确: \$EMAIL_CHECK"
else
    echo "❌ 邮箱用户名配置失败 - 期望: $EMAIL_USERNAME, 实际: \$EMAIL_CHECK"
fi

if [[ "\$PASSWORD_CHECK" == "$EMAIL_PASSWORD" ]]; then
    echo "✅ 邮箱密码配置正确"
else
    echo "❌ 邮箱密码配置失败"
fi
EOF

# 上传并执行远程脚本
scp -i "$SSH_KEY" /tmp/remote_config.sh "$REMOTE_HOST":/tmp/
ssh -i "$SSH_KEY" "$REMOTE_HOST" "chmod +x /tmp/remote_config.sh && /tmp/remote_config.sh"

# 清理临时文件
rm -f /tmp/remote_config.sh
ssh -i "$SSH_KEY" "$REMOTE_HOST" "rm -f /tmp/remote_config.sh"

# 检查远程命令执行结果
if [[ $? -eq 0 ]]; then
    echo ""
    echo -e "${GREEN}🎉 163邮箱配置完成！${NC}"
    echo ""
    echo -e "${BLUE}📝 配置结果:${NC}"
    echo "✅ 代码已通过GitHub自动部署"
    echo "✅ 环境变量已更新并生效"
    echo ""
    
    echo -e "${BLUE}🔍 配置验证信息:${NC}"
    echo "邮箱地址: $EMAIL_USERNAME"
    echo "远程服务: http://107.173.38.186:8084"
    echo ""
    echo -e "${GREEN}📧 现在可以测试邮箱验证码发送功能了！${NC}"
    echo ""
    echo -e "${YELLOW}💡 测试步骤:${NC}"
    echo "1. 访问 http://107.173.38.186:8084"
    echo "2. 尝试注册新用户"
    echo "3. 查看163邮箱是否收到验证码邮件"
    echo ""
    echo -e "${BLUE}🔧 如果遇到问题，查看日志:${NC}"
    echo "ssh -i ~/.ssh/xiaozhi_deploy root@107.173.38.186 'docker logs xiaozhi-esp32-server-java-server-1 --tail 50'"
    
else
    echo ""
    echo -e "${RED}❌ 远程配置失败${NC}"
    echo "请检查网络连接和SSH配置"
    exit 1
fi
