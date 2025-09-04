# Docker 文件备份说明

## 备份时间
2025-09-04

## 备份文件
- `Dockerfile-server` - 原始Java服务端Dockerfile
- `Dockerfile-mysql` - MySQL容器Dockerfile  
- `Dockerfile-node` - Node.js前端Dockerfile
- `docker-compose.yml` - Docker Compose配置文件
- `.dockerignore` - Docker忽略文件配置

## 备份原因
在实施Docker多阶段构建优化之前进行备份，确保可以回滚到原始版本。

## 恢复方法
如需恢复原始文件：
```bash
cp backup/docker-original/* .
```

## 优化目标
- 减少最终镜像大小
- 提高构建效率
- 优化层缓存利用