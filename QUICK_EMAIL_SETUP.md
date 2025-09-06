# 📧 163邮箱一键配置使用指南

## 🎯 **关于IMAP/SMTP服务**

**✅ 完全可用**：163邮箱的 IMAP/SMTP 服务完全满足需求！
- **IMAP**: 收件协议 (我们不需要)
- **SMTP**: 发件协议 (✅ 这是我们需要的)

POP3/SMTP服务被关闭不影响，IMAP/SMTP服务已经包含了SMTP功能。

---

## 🚀 **立即开始 (2分钟搞定)**

### **步骤 1: 运行配置脚本**
```bash
./setup-email.sh
```

### **步骤 2: 输入你的163邮箱信息**
脚本会提示你输入：
- 📧 **163邮箱地址**: `yourname@163.com`
- 🔑 **IMAP/SMTP授权码**: `你在163邮箱设置中获取的授权码`

### **步骤 3: 自动完成配置**
脚本会自动：
- 连接远程服务器
- 更新邮箱配置
- 重启服务
- 验证结果

---

## 📋 **如果你还没有163邮箱授权码**

### **获取步骤**:
1. **登录163邮箱**: https://mail.163.com
2. **进入设置**: 点击右上角"设置" → "POP3/SMTP/IMAP"
3. **开启服务**: 勾选"IMAP/SMTP服务"
4. **设置授权码**: 点击"客户端授权密码"，设置一个密码
5. **保存授权码**: 这个密码就是你要输入的授权码

---

## 🔄 **完整流程演示**

```bash
$ ./setup-email.sh

🚀 小智邮箱服务自动配置脚本
================================

📧 163邮箱地址 (如: yourname@163.com): your_email@163.com
🔑 IMAP/SMTP授权码 (在163邮箱设置中获取): ********

📋 配置信息确认:
邮箱地址: your_email@163.com
授权码: abc***xyz

是否确认配置? (y/N): y

🚀 开始自动配置...

📡 连接到远程服务器...
🔍 检查服务状态...
✅ 原配置已备份
🔧 更新邮箱配置...
✅ Docker配置已更新
🔄 重启服务...
⏳ 等待服务启动...
✅ 服务重启成功

🎉 配置完成！

📝 接下来需要修改代码支持163邮箱:
是否自动修改代码? (Y/n): y

🔧 修改代码以支持163邮箱...
✅ 代码已修改为使用163邮箱SMTP

📤 请提交代码更改并等待自动部署:
git add .
git commit -m "feat: configure 163 email SMTP service"
git push

📧 现在可以测试邮箱验证码发送功能了！
```

---

## 🧪 **测试验证**

### **配置完成后**:

1. **提交代码** (如果脚本建议):
   ```bash
   git add .
   git commit -m "feat: configure 163 email SMTP service"
   git push
   ```

2. **等待部署完成** (2-3分钟)

3. **测试邮箱功能**:
   - 访问: http://107.173.38.186:8084
   - 尝试注册新用户
   - 检查163邮箱是否收到验证码

---

## 🔍 **故障排除**

### **如果邮件发送失败**:

1. **查看服务日志**:
   ```bash
   ssh -i ~/.ssh/xiaozhi_deploy root@107.173.38.186 \
   'docker logs xiaozhi-esp32-server-java-server-1 --tail 50'
   ```

2. **检查配置是否正确**:
   ```bash
   ssh -i ~/.ssh/xiaozhi_deploy root@107.173.38.186 \
   'docker exec xiaozhi-esp32-server-java-server-1 env | grep EMAIL'
   ```

3. **重新运行配置脚本**:
   ```bash
   ./setup-email.sh
   ```

### **常见问题**:

❓ **"Authentication failed"**
- 检查授权码是否正确
- 确认163邮箱IMAP/SMTP服务已开启

❓ **"Connection timeout"**
- 检查网络连接
- 重新运行脚本

❓ **"邮箱格式不正确"**
- 确保输入的是 `@163.com` 结尾的邮箱

---

## 💡 **高级用法**

### **手动配置** (如果脚本失败):

```bash
# 1. 连接服务器
ssh -i ~/.ssh/xiaozhi_deploy root@107.173.38.186

# 2. 编辑配置
cd /opt/xiaozhi-deployment/xiaozhi-esp32-server-java
nano docker-compose.yml

# 3. 修改邮箱配置
EMAIL_SMTP_USERNAME=your_email@163.com
EMAIL_SMTP_PASSWORD=your_authorization_code

# 4. 重启服务
docker-compose restart server
```

---

## 🎉 **预期结果**

配置成功后：
- ✅ 用户注册时能收到验证码邮件
- ✅ 邮件发送速度快 (163国内服务器)
- ✅ 无发送数量限制
- ✅ 服务稳定可靠

---

## 📞 **需要帮助？**

如果遇到问题：
1. 查看脚本输出的错误信息
2. 检查网络连接和SSH配置
3. 确认163邮箱授权码正确
4. 重新运行 `./setup-email.sh`

---

**一键配置，立即可用！** 🚀

*脚本文件: `setup-email.sh`*
*配置指南: 本文档*
