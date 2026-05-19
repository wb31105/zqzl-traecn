# ZQZL 微服务群 - 部署架构调整说明

## 一、架构调整概述

本次工程化优化对项目部署架构进行了全面升级，实现了**双轨制部署模式**：
1. **本地开发模式** - 保留原有 `start-all.sh` 脚本体系
2. **Docker 容器化部署模式** - 新增 Docker 一键部署体系

两种模式完全独立，配置互不影响，可根据场景灵活选择。

---

## 二、目录结构调整

### 新增部署目录结构

```
zqzl-traecn/
├── .deploy/                          # 部署配置目录（新增）
│   ├── docker/                       # Docker 相关配置
│   │   ├── backend/                  # 后端服务 Dockerfile
│   │   │   ├── eureka-server.Dockerfile
│   │   │   ├── gateway-server.Dockerfile
│   │   │   ├── sso-server.Dockerfile
│   │   │   └── user-server.Dockerfile
│   │   ├── frontend/                 # 前端应用 Dockerfile
│   │   │   ├── sso-web.Dockerfile
│   │   │   └── user-web.Dockerfile
│   │   └── nginx/                    # Nginx 网关配置
│   │       ├── nginx.Dockerfile
│   │       └── nginx-docker.conf
│   └── scripts/                      # 单个服务打包脚本
│       ├── build-eureka-server.sh
│       ├── build-gateway-server.sh
│       ├── build-sso-server.sh
│       ├── build-user-server.sh
│       ├── build-sso-web.sh
│       ├── build-user-web.sh
│       └── build-nginx-gateway.sh
├── docker-compose.yml                 # Docker Compose 编排文件（新增）
├── docker-build-all.sh                # Docker 一键构建脚本（新增）
├── docker-start-all.sh                # Docker 一键启动脚本（新增）
├── docker-stop-all.sh                 # Docker 一键停止脚本（新增）
├── docker-logs.sh                     # Docker 日志查看脚本（新增）
├── start-all.sh                       # 原本地启动脚本（保留）
├── stop-all.sh                        # 原本地停止脚本（保留）
└── build-all.sh                       # 原本地构建脚本（保留）
```

---

## 三、配置文件调整说明

### 3.1 后端服务配置优化

**调整文件：**
- [gateway-server/src/main/resources/application.yml](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/gateway-server/src/main/resources/application.yml)
- [sso-server/src/main/resources/application.yml](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/resources/application.yml)
- [user-server/src/main/resources/application.yml](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/user-server/src/main/resources/application.yml)

**调整内容：**

将硬编码的配置改为支持环境变量注入：

```yaml
# 调整前（固定值）
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

# 调整后（支持环境变量）
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE:http://localhost:8761/eureka/}
```

**新增环境变量：**

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | Eureka 注册中心地址 | `http://localhost:8761/eureka/` |
| `USER_SERVICE_URL` | 用户服务调用地址 | `http://user-server/user` |

**设计原则：**
- 本地开发时使用默认值，无需额外配置
- Docker 部署时通过环境变量覆盖，实现容器间通信

### 3.2 Nginx 配置调整

**新增 Docker 专用配置：** [nginx-docker.conf](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/.deploy/docker/nginx/nginx-docker.conf)

**关键调整：**

```nginx
# Docker 环境中使用服务名代替 localhost
upstream sso_web {
    server sso-web:80;          # Docker 服务名 + 容器内部端口
}

upstream user_web {
    server user-web:80;
}

upstream backend_gateway {
    server gateway-server:9000;
}
```

---

## 四、Docker 镜像设计

### 4.1 后端服务镜像

**采用多阶段构建（Multi-stage Build）：**

- **构建阶段**：`maven:3.8.6-eclipse-temurin-11`
  - 完整的 Maven + JDK 11 环境
  - 只下载依赖，提高构建速度
  - 编译打包应用
  
- **运行阶段**：`eclipse-temurin:11-jre`
  - 仅包含 JRE 11 运行时
  - 镜像体积小，启动快
  - 安全性更高

**镜像大小对比：**
| 镜像 | 大小 | 说明 |
|------|------|------|
| maven:3.8.6-eclipse-temurin-11 | ~700MB | 构建阶段使用 |
| eclipse-temurin:11-jre | ~250MB | 运行阶段使用 |

### 4.2 前端应用镜像

**同样采用多阶段构建：**

- **构建阶段**：`node:18-alpine`
  - Node.js 环境，执行 npm build
  - 生成静态资源
  
- **运行阶段**：`nginx:alpine`
  - 轻量级 Nginx 服务器
  - 仅托管静态资源
  - 镜像 ~50MB

---

## 五、Docker Compose 编排设计

### 5.1 服务依赖关系

```
nginx-gateway:80
    ├── sso-web:80
    ├── user-web:80
    └── gateway-server:9000
              ├── eureka-server:8761
              ├── sso-server:8080
              └── user-server:8081
```

### 5.2 健康检查与启动顺序

1. **eureka-server** - 优先启动，健康检查通过后启动后续服务
2. **gateway-server / user-server** - 依赖 Eureka 启动
3. **sso-server** - 依赖 Eureka 和 user-server
4. **sso-web / user-web** - 前端应用，依赖后端服务
5. **nginx-gateway** - 最后启动，统一入口

### 5.3 网络设计

- 所有服务加入 `zqzl-network` 自定义网络
- 服务间通过服务名（如 `eureka-server`）通信
- 无需暴露内部端口，仅暴露必要的对外端口

---

## 六、部署模式对比

### 6.1 本地开发模式

**适用场景：** 开发调试、本地测试

**使用脚本：**
```bash
# 一键启动（原有方式）
./start-all.sh

# 一键停止
./stop-all.sh
```

**特点：**
- ✅ 代码热更新，修改立即生效
- ✅ 调试方便，IDEA 可直接附加
- ✅ 启动速度快
- ❌ 环境依赖（Java、Node、Maven）
- ❌ 端口占用问题

### 6.2 Docker 容器化部署模式

**适用场景：** 生产部署、演示环境、CI/CD

**使用脚本：**
```bash
# 1. 构建所有镜像
./docker-build-all.sh

# 2. 一键启动
./docker-start-all.sh

# 3. 查看日志
./docker-logs.sh

# 4. 停止服务
./docker-stop-all.sh
```

**特点：**
- ✅ 环境一致性，一次构建处处运行
- ✅ 无环境依赖，仅需 Docker
- ✅ 一键部署，自动编排
- ✅ 隔离性好，服务互不影响
- ❌ 代码修改需重新构建镜像

---

## 七、端口映射表

| 服务 | 本地端口 | Docker 容器端口 | 说明 |
|------|----------|----------------|------|
| Eureka 注册中心 | 8761 | 8761 | 服务发现 |
| Gateway API 网关 | 9000 | 9000 | 后端统一入口 |
| User 后端服务 | 8081 | 8081 | 用户服务 |
| SSO 后端服务 | 8080 | 8080 | 单点登录服务 |
| SSO 前端 | 3000 | 80 | 登录页面 |
| User 前端 | 3031 | 80 | 用户管理页面 |
| Nginx 统一入口 | 80 | 80 | 对外访问入口 |

---

## 八、单个服务独立部署

每个服务都支持单独打包和部署，便于微服务独立发布：

```bash
# 构建单个服务镜像
cd .deploy/scripts/
./build-eureka-server.sh    # 构建 Eureka
./build-user-server.sh      # 构建用户服务
./build-sso-web.sh          # 构建 SSO 前端

# 单独启动某个服务（使用 docker compose）
docker compose up -d user-server
```

---

## 九、常见问题排查

### 9.1 Docker 镜像构建失败

**问题：** `failed to resolve source metadata`

**解决方案：**
```bash
# 清理 Docker 缓存
docker system prune -a

# 检查网络连接
docker pull hello-world
```

### 9.2 服务注册失败

**问题：** 后端服务无法注册到 Eureka

**排查步骤：**
1. 检查 Eureka 是否启动完成
2. 查看服务日志：`./docker-logs.sh user-server`
3. 确认环境变量是否正确注入

### 9.3 前端无法访问后端

**问题：** 前端页面请求失败

**排查步骤：**
1. 检查 Nginx 配置是否正确
2. 确认 Gateway 服务是否健康
3. 查看浏览器控制台错误信息

---

## 十、后续优化方向

1. **镜像优化** - 使用 Alpine 基础镜像进一步减小体积
2. **资源限制** - 为容器添加 CPU/内存限制
3. **日志收集** - 集成 ELK 日志收集
4. **监控告警** - 添加 Prometheus + Grafana
5. **灰度发布** - 支持服务滚动更新
6. **配置中心** - 引入 Nacos/Apollo 统一配置管理

---

## 十一、文档更新记录

| 版本 | 日期 | 更新内容 | 更新人 |
|------|------|----------|--------|
| v1.0 | 2026-05-19 | 初始版本，完成 Docker 化架构设计 | 系统 |
