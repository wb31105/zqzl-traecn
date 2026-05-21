# ZQZL 微服务群 - 部署架构说明

## 一、架构概述

### 1.1 当前架构（v4.0）

本次架构重构实现了**轻量化微服务架构**，移除了冗余的注册中心和Spring Gateway，采用更简洁高效的方案：

✅ **核心变更**：
- **移除 Eureka 注册中心**：不再需要服务注册发现
- **移除 Spring Gateway**：使用 APISIX 作为业务网关
- **前端调用使用 HTTP 代理**：前端通过相对路径调用后端接口
- **服务间调用使用 gRPC**：后端服务间通过 gRPC 高性能通信
- **端口统一**：所有后端服务 HTTP 端口统一为 8080（容器环境）
- **配置简化**：只保留 application.yml 和 application-dev.yml

### 1.2 架构图

```
┌─────────────────────────────────────────────────────────┐
│                        客户端浏览器                        │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│                    APISIX 网关 (80/443)                   │
│  ┌─────────────────────────────────────────────────┐    │
│  │  路由规则:                                        │    │
│  │  /          → sso-web:80 (登录门户)              │    │
│  │  /user/*    → user-web:80 (管理平台)             │    │
│  │  /v1/auth/* → sso-server:8080 (认证服务)         │    │
│  │  /v1/users/* → user-server:8080 (用户服务)       │    │
│  └─────────────────────────────────────────────────┘    │
└──────────┬───────────────────────────┬──────────────────┘
           │                           │
           ▼                           ▼
┌─────────────────────┐    ┌─────────────────────────────┐
│     sso-web (80)     │    │      user-web (80)          │
│   (React SPA)        │    │     (React SPA)             │
└─────────────────────┘    └─────────────────────────────┘
           │                           │
           │ HTTP (相对路径)            │ HTTP (相对路径)
           ▼                           ▼
┌─────────────────────────────────────────────────────────┐
│              后端服务 (gRPC 通信)                         │
│                                                          │
│  ┌──────────────────┐     gRPC      ┌─────────────────┐ │
│  │  sso-server       │◄────────────►│  user-server    │ │
│  │  HTTP:8080        │              │  HTTP:8080      │ │
│  │  gRPC:9090        │              │  gRPC:9091      │ │
│  └──────────────────┘              └─────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

### 1.3 技术栈

| 层级 | 技术选型 | 说明 |
|------|---------|------|
| **网关** | APISIX 2.15 | 高性能 API 网关，支持路由、限流、鉴权 |
| **服务注册** | 无 | 通过 Docker 网络 + 静态地址通信 |
| **前端框架** | React 18 + React Router | SPA 单页应用 |
| **后端框架** | Spring Boot 2.7 | Java 微服务框架 |
| **服务间通信** | gRPC 1.59 | 高性能 RPC 框架 |
| **前端调用** | HTTP + Proxy | 通过相对路径 + 代理转发 |
| **数据库** | H2 (内存) | 轻量级数据库，便于开发 |

---

## 二、项目目录结构

```
zqzl-traecn/
├── ops/                              # 通用配置和脚本目录
│   ├── docker/
│   │   └── apisix/                  # APISIX 网关配置
│   │       ├── Dockerfile
│   │       ├── config.yaml          # APISIX 主配置
│   │       └── apisix.yaml          # 路由规则配置
│   └── scripts/
│       ├── build-all.sh             # 构建所有服务镜像
│       └── build-apisix.sh          # 构建 APISIX 网关镜像
│
├── backend/
│   ├── frameworks/
│   │   └── zqzl-framework/          # 统一依赖管理
│   │       └── pom.xml
│   │
│   └── services/
│       ├── sso-server/              # 单点登录服务
│       │   ├── deploy/
│       │   │   ├── Dockerfile
│       │   │   └── build.sh
│       │   ├── src/main/
│       │   │   ├── java/com/sso/
│       │   │   │   ├── grpc/        # gRPC 服务实现
│       │   │   │   │   └── SsoGrpcServiceImpl.java
│       │   │   │   ├── client/      # gRPC 客户端
│       │   │   │   │   └── UserServiceClient.java
│       │   │   │   └── ...
│       │   │   ├── proto/           # gRPC Proto 定义
│       │   │   │   └── sso.proto
│       │   │   └── resources/
│       │   │       ├── application.yml
│       │   │       └── application-dev.yml
│       │   └── pom.xml
│       │
│       └── user-server/             # 用户中心服务
│           ├── deploy/
│           │   ├── Dockerfile
│           │   └── build.sh
│           ├── src/main/
│           │   ├── java/com/user/
│           │   │   ├── grpc/        # gRPC 服务实现
│           │   │   │   └── UserGrpcServiceImpl.java
│           │   │   ├── client/      # gRPC 客户端
│           │   │   │   └── SsoClient.java
│           │   │   └── ...
│           │   ├── proto/           # gRPC Proto 定义
│           │   │   └── user.proto
│           │   └── resources/
│           │       ├── application.yml
│           │       └── application-dev.yml
│           └── pom.xml
│
├── frontend/
│   └── apps/
│       ├── sso-web/                 # 登录门户前端
│       │   ├── deploy/
│       │   │   ├── Dockerfile
│       │   │   ├── nginx.conf
│       │   │   └── build.sh
│       │   ├── src/
│       │   │   └── setupProxy.js    # 开发环境代理配置
│       │   ├── .env                 # 生产环境配置
│       │   ├── .env.development     # 开发环境配置
│       │   └── package.json
│       │
│       └── user-web/                # 管理平台前端
│           ├── deploy/
│           │   ├── Dockerfile
│           │   ├── nginx.conf
│           │   └── build.sh
│           ├── src/
│           │   └── setupProxy.js    # 开发环境代理配置
│           ├── .env                 # 生产环境配置
│           ├── .env.development     # 开发环境配置
│           └── package.json
│
├── docker-compose.yml               # Docker 编排文件
└── README.md
```

---

## 三、服务端口映射

### 3.1 开发环境端口

| 服务 | HTTP 端口 | gRPC 端口 | 说明 |
|------|----------|----------|------|
| **sso-server** | 8080 | 9090 | 单点登录服务 |
| **user-server** | 8081 | 9091 | 用户中心服务 |
| **sso-web** | 3000 | - | 登录门户前端 |
| **user-web** | 3031 | - | 管理平台前端 |

### 3.2 Docker 环境端口

| 服务 | 容器端口 | 映射端口 | 说明 |
|------|----------|----------|------|
| **etcd** | 2379 | 2379 | APISIX 配置存储 |
| **APISIX** | 9080 | 80 | 网关 HTTP |
| **APISIX** | 9443 | 443 | 网关 HTTPS |
| **sso-server** | 8080 | 8080 | 认证服务 |
| **user-server** | 8080 | 8081 | 用户服务 |
| **sso-web** | 80 | 3000 | 登录门户 |
| **user-web** | 80 | 3031 | 管理平台 |

---

## 四、调用链路详解

### 4.1 前端 → 后端 (HTTP)

```
浏览器 (相对路径)
    │
    ▼
APISIX 网关 (路由转发)
    │
    ├── /v1/auth/* → sso-server:8080/sso/v1/auth/*
    │
    └── /v1/users/* → user-server:8080/v1/users/*
```

**前端代码示例：**
```javascript
// 使用相对路径，由网关/代理转发
const response = await axios.post('/v1/auth/login', { username, password });
```

### 4.2 服务间调用 (gRPC)

```
sso-server (9090) ◄────gRPC────► user-server (9091)
```

**Proto 定义：**
- [user.proto](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/user-server/src/main/proto/user.proto) - 用户服务 gRPC 接口
- [sso.proto](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/proto/sso.proto) - SSO 服务 gRPC 接口

### 4.3 APISIX 路由配置

配置文件：[apisix.yaml](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/ops/docker/apisix/apisix.yaml)

```yaml
routes:
  - id: sso-server-route
    uri: /v1/auth/*
    plugins:
      proxy-rewrite:
        regex_uri: ["^/v1/auth/(.*)", "/sso/v1/auth/$1"]
    upstream:
      nodes:
        "sso-server:8080": 1

  - id: user-server-route
    uri: /v1/users/*
    plugins:
      proxy-rewrite:
        regex_uri: ["^/v1/users/(.*)", "/v1/users/$1"]
    upstream:
      nodes:
        "user-server:8080": 1
```

---

## 五、配置文件说明

### 5.1 后端配置

每个服务只保留两个配置文件：

| 文件 | 用途 | 激活方式 |
|------|------|----------|
| `application.yml` | 默认配置（生产环境） | 默认加载 |
| `application-dev.yml` | 开发环境覆盖 | `SPRING_PROFILES_ACTIVE=dev` |

**配置示例：**

```yaml
# application.yml - 生产默认配置
server:
  port: ${SERVER_PORT:8080}

grpc:
  server:
    port: ${GRPC_SERVER_PORT:9090}
  client:
    user-server:
      address: static://${USER_GRPC_HOST:user-server}:${USER_GRPC_PORT:9091}
```

```yaml
# application-dev.yml - 开发环境覆盖
server:
  port: 8080

grpc:
  client:
    user-server:
      address: static://localhost:9091
```

### 5.2 前端配置

| 文件 | 用途 | 激活方式 |
|------|------|----------|
| `.env` | 默认配置（生产环境） | `dotenv -e .env` |
| `.env.development` | 开发环境覆盖 | `dotenv -e .env.development` |

**开发环境代理配置：**

[sso-web/setupProxy.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/sso-web/src/setupProxy.js)
```javascript
module.exports = function(app) {
  app.use('/v1/auth', createProxyMiddleware({
    target: 'http://localhost:8080',
    changeOrigin: true,
    pathRewrite: { '^/v1/auth': '/sso/v1/auth' },
  }));
};
```

---

## 六、构建与发布

### 6.1 构建所有镜像

```bash
# 方式一：使用通用脚本
cd ops/scripts
./build-all.sh

# 方式二：分别构建
# 构建后端服务
bash backend/services/sso-server/deploy/build.sh
bash backend/services/user-server/deploy/build.sh

# 构建前端应用
bash frontend/apps/sso-web/deploy/build.sh
bash frontend/apps/user-web/deploy/build.sh

# 构建 APISIX 网关
bash ops/scripts/build-apisix.sh
```

### 6.2 单个服务构建

```bash
# 构建 sso-server
cd backend/services/sso-server
bash deploy/build.sh

# 构建 user-server
cd backend/services/user-server
bash deploy/build.sh

# 构建 sso-web
cd frontend/apps/sso-web
bash deploy/build.sh

# 构建 user-web
cd frontend/apps/user-web
bash deploy/build.sh

# 构建 APISIX
cd ops/docker/apisix
docker build -t zqzl/apisix-gateway:latest .
```

### 6.3 Docker Compose 部署

```bash
# 1. 构建所有镜像
bash ops/scripts/build-all.sh

# 2. 启动所有服务
docker compose up -d

# 3. 查看服务状态
docker compose ps

# 4. 查看日志
docker compose logs -f apisix
docker compose logs -f sso-server
docker compose logs -f user-server

# 5. 停止所有服务
docker compose down
```

### 6.4 访问地址

| 服务 | 开发环境地址 | Docker 环境地址 |
|------|-------------|----------------|
| 登录门户 | http://localhost:3000 | http://localhost |
| 管理平台 | http://localhost:3031 | http://localhost/user |
| SSO API | http://localhost:8080/sso/v1/auth | http://localhost/v1/auth |
| User API | http://localhost:8081/v1/users | http://localhost/v1/users |

---

## 七、本地开发指南

### 7.1 环境要求

- JDK 11+
- Maven 3.8+
- Node.js 16+
- npm 或 yarn

### 7.2 启动后端服务

```bash
# 启动 user-server (端口 8081)
cd backend/services/user-server
mvn spring-boot:run

# 启动 sso-server (端口 8080)
cd backend/services/sso-server
mvn spring-boot:run
```

### 7.3 启动前端应用

```bash
# 启动 sso-web (端口 3000)
cd frontend/apps/sso-web
npm install
npm start

# 启动 user-web (端口 3031)
cd frontend/apps/user-web
npm install
npm start
```

### 7.4 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ADMIN |

---

## 八、常见问题排查

### 8.1 脚本执行权限问题

**问题：** `Permission denied` 或 `sh: permission denied`

**解决方案：**
```bash
# 给所有脚本添加执行权限
find . -name "*.sh" -type f -exec chmod +x {} \;

# 或单独给某个脚本
chmod +x ops/scripts/build-apisix.sh
```

### 8.2 gRPC 连接失败

**问题：** `UNAVAILABLE: Connection refused`

**排查步骤：**
1. 确认目标服务是否启动
2. 检查 gRPC 端口是否正确
3. 查看配置文件中 gRPC 地址是否正确

### 8.3 前端代理不生效

**问题：** 开发环境接口调用失败

**排查步骤：**
1. 确认 `setupProxy.js` 配置正确
2. 确认后端服务已启动
3. 检查端口是否被占用

### 8.4 Docker 构建失败

**问题：** `COPY failed: file not found`

**排查步骤：**
1. 确认在正确目录执行构建
2. 检查 Dockerfile 中的 COPY 路径
3. 确认源码文件存在

---

## 九、文档更新记录

| 版本 | 日期 | 更新内容 | 更新人 |
|------|------|----------|--------|
| v4.0 | 2026-05-21 | 架构重构：APISIX 网关 + gRPC 服务间调用 | 系统 |
| v3.0 | 2026-05-19 | 域名架构升级，三域名分离 | 系统 |
| v2.0 | 2026-05-19 | Docker 容器化部署架构设计 | 系统 |
| v1.0 | 2026-05-19 | 初始版本 | 系统 |
