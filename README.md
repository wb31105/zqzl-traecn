# ZQZL 微服务架构平台

基于轻量化微服务架构，采用 APISIX 网关 + gRPC 服务间通信，支持 Docker 容器化部署。

---

## 目录

- [架构概览](#架构概览)
- [服务列表](#服务列表)
- [快速开始](#快速开始)
- [构建与发布](#构建与发布)
- [服务维护](#服务维护)
- [API 接口文档](#api-接口文档)
- [技术栈](#技术栈)

---

## 架构概览

### 整体架构图（APISIX 网关版）

```
┌─────────────────────────────────────────────────────────────────┐
│                        客户端浏览器                                │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    APISIX 网关 (80/443)                          │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  路由规则:                                                  │  │
│  │  /          → sso-web:80 (登录门户)                         │  │
│  │  /user/*    → user-web:80 (管理平台)                        │  │
│  │  /v1/auth/* → sso-server:8080 (认证服务)                    │  │
│  │  /v1/users/* → user-server:8080 (用户服务)                  │  │
│  └───────────────────────────────────────────────────────────┘  │
└──────────┬───────────────────────────┬──────────────────────────┘
           │                           │
           ▼                           ▼
┌─────────────────────┐    ┌─────────────────────────────┐
│     sso-web (80)     │    │      user-web (80)          │
│   (React SPA)        │    │     (React SPA)             │
└─────────────────────┘    └─────────────────────────────┘
           │                           │
           │ HTTP (相对路径)            │ HTTP (相对路径)
           ▼                           ▼
┌─────────────────────────────────────────────────────────────────┐
│              后端服务 (gRPC 通信)                                 │
│                                                                  │
│  ┌──────────────────┐     gRPC      ┌─────────────────┐        │
│  │  sso-server       │◄────────────►│  user-server    │        │
│  │  HTTP:8080        │              │  HTTP:8080      │        │
│  │  gRPC:9090        │              │  gRPC:9091      │        │
│  └──────────────────┘              └─────────────────┘        │
└─────────────────────────────────────────────────────────────────┘
```

### 核心架构特点

| 特性 | 说明 |
|------|------|
| **网关** | APISIX 2.15，高性能 API 网关 |
| **服务注册** | 无注册中心，Docker 网络 + 静态地址 |
| **前端调用** | HTTP + Proxy，相对路径调用 |
| **服务间通信** | gRPC 1.59，高性能 RPC |
| **端口统一** | 容器环境 HTTP:8080，gRPC:9090/9091 |
| **配置简化** | 只保留 application.yml + application-dev.yml |

### 调用流程说明

1. **用户访问**: 浏览器访问 APISIX 网关 (http://localhost)
2. **前端路由**: APISIX 根据路径分发到对应的前端应用
3. **API 路由**: 前端通过相对路径调用后端接口，由 APISIX 转发
4. **服务间调用**: sso-server 和 user-server 通过 gRPC 通信

---

## 服务列表

### 后端服务

| 服务名称 | HTTP 端口 | gRPC 端口 | 职责 |
|---------|----------|----------|------|
| **sso-server** | 8080 | 9090 | 单点登录服务、票据生成/验证 |
| **user-server** | 8080 | 9091 | 用户CRUD、认证、权限管理 |

### 前端服务

| 服务名称 | 容器端口 | 映射端口 | 职责 |
|---------|---------|---------|------|
| **sso-web** | 80 | 3000 | 登录门户、注册/找回密码 |
| **user-web** | 80 | 3031 | 用户管理后台 |

### 基础设施

| 服务名称 | 端口 | 职责 |
|---------|------|------|
| **APISIX** | 80/443 | API 网关、路由转发 |
| **etcd** | 2379 | APISIX 配置存储 |

---

## 快速开始

### 前置条件

- JDK 11+
- Maven 3.8+
- Node.js 16+
- Docker & Docker Compose

### 本地开发启动

#### 1. 构建框架层（首次必须执行）

```bash
cd backend/frameworks/zqzl-framework
mvn clean install
```

#### 2. 启动后端服务

**启动 user-server (端口 8081, gRPC 9091)**:
```bash
cd backend/services/user-server
mvn spring-boot:run
```

**启动 sso-server (端口 8080, gRPC 9090)**:
```bash
cd backend/services/sso-server
mvn spring-boot:run
```

#### 3. 启动前端应用

**启动 sso-web (端口 3000)**:
```bash
cd frontend/apps/sso-web
npm install
npm start
```

**启动 user-web (端口 3031)**:
```bash
cd frontend/apps/user-web
npm install
npm start
```

### Docker Compose 部署

```bash
# 1. 构建所有镜像
bash ops/scripts/build-all.sh

# 2. 启动所有服务
docker compose up -d

# 3. 查看服务状态
docker compose ps

# 4. 停止所有服务
docker compose down
```

### 访问地址

| 服务 | 开发环境 | Docker 环境 |
|------|---------|-------------|
| 登录门户 | http://localhost:3000 | http://localhost |
| 管理平台 | http://localhost:3031 | http://localhost/user |
| SSO API | http://localhost:8080/sso/v1/auth | http://localhost/v1/auth |
| User API | http://localhost:8081/v1/users | http://localhost/v1/users |

### 默认账号

- 用户名: `admin`
- 密码: `admin123`
- 角色: ADMIN（管理员）

---

## 构建与发布

### 构建所有镜像

```bash
cd ops/scripts
./build-all.sh
```

### 单个服务构建

```bash
# 构建 sso-server
bash backend/services/sso-server/deploy/build.sh

# 构建 user-server
bash backend/services/user-server/deploy/build.sh

# 构建 sso-web
bash frontend/apps/sso-web/deploy/build.sh

# 构建 user-web
bash frontend/apps/user-web/deploy/build.sh

# 构建 APISIX
bash ops/scripts/build-apisix.sh
```

---

## 服务维护

### 查看服务状态

```bash
# Docker Compose 方式
docker compose ps

# 查看日志
docker compose logs -f apisix
docker compose logs -f sso-server
docker compose logs -f user-server
```

### 端口占用检查

```bash
# 检查所有服务端口
lsof -ti :8080 -ti :8081 -ti :9090 -ti :9091 -ti :3000 -ti :3031 -ti :80
```

### 常见问题排查

#### 1. 脚本执行权限问题

```bash
# 给所有脚本添加执行权限
find . -name "*.sh" -type f -exec chmod +x {} \;
```

#### 2. gRPC 连接失败

**排查步骤**:
1. 确认目标服务是否启动
2. 检查 gRPC 端口是否正确
3. 查看配置文件中 gRPC 地址是否正确

#### 3. 前端代理不生效

**排查步骤**:
1. 确认 `setupProxy.js` 配置正确
2. 确认后端服务已启动
3. 检查端口是否被占用

---

## API 接口文档

### SSO 单点登录接口

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/v1/auth/login` | 用户登录（返回票据） |
| POST | `/v1/auth/register` | 用户注册 |
| POST | `/v1/auth/forgot-password` | 重置密码 |
| GET | `/v1/auth/captcha` | 获取验证码 |
| GET | `/v1/auth/check-captcha` | 检查是否需要验证码 |
| GET | `/v1/auth/validate-ticket` | 验证票据有效性 |

### User 用户中心接口

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/v1/users` | 获取用户列表（分页、搜索） |
| GET | `/v1/users/{id}` | 获取单个用户详情 |
| PUT | `/v1/users/{id}` | 更新用户基本信息 |
| PUT | `/v1/users/{id}/reset-password` | 重置用户密码 |
| PUT | `/v1/users/{id}/toggle-status` | 切换用户启用/禁用状态 |
| DELETE | `/v1/users/{id}` | 删除用户 |

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 2.7.x | 应用框架 |
| gRPC | 1.59 | 服务间通信 |
| Spring Security | - | 安全认证 |
| Spring Data JPA | - | ORM 框架 |
| H2 Database | - | 内存数据库 |
| Lombok | - | 代码简化 |
| JSR-380 | - | 参数校验 |

### 前端

| 技术 | 说明 |
|------|------|
| React 18 | UI 框架 |
| React Router v6 | 路由管理 |
| Axios | HTTP 客户端 |
| http-proxy-middleware | 开发环境代理 |
| CSS3 | 样式 |

### 基础设施

| 技术 | 说明 |
|------|------|
| APISIX | API 网关 |
| etcd | 配置存储 |
| Nginx | 前端静态资源服务 |
| Maven | 项目构建、依赖管理 |
| npm | 前端包管理 |
| Docker | 容器化部署 |

---

## 开发规范

### 微服务开发规范

1. 各微服务独立开发、部署
2. 微服务之间通过 gRPC 调用
3. 前端 API 请求使用相对路径，由网关转发
4. 框架层变更需谨慎评估，发布后更新所有服务依赖
5. 所有配置必须支持多环境切换，敏感配置使用环境变量注入
6. DTO 与 Entity 分离，避免直接暴露数据库实体
7. 使用 BCrypt 加密存储所有用户密码

### 配置文件规范

| 文件 | 用途 | 激活方式 |
|------|------|----------|
| `application.yml` | 默认配置（生产环境） | 默认加载 |
| `application-dev.yml` | 开发环境覆盖 | `SPRING_PROFILES_ACTIVE=dev` |

---

## 版本历史

### v4.0.0 (当前版本 - APISIX 网关版)
- ✅ **架构重构**：移除 Eureka 注册中心和 Spring Gateway
- ✅ **APISIX 网关**：使用 APISIX 作为业务网关
- ✅ **gRPC 通信**：服务间调用改用 gRPC 高性能通信
- ✅ **HTTP 代理**：前端调用使用相对路径 + 代理
- ✅ **端口统一**：容器环境所有服务 HTTP 端口统一为 8080
- ✅ **配置简化**：只保留 application.yml 和 application-dev.yml

### v3.0.0
- 域名架构重构
- API 版本管理

### v2.0.0
- Eureka 服务注册与发现
- Spring Cloud Gateway
- OpenFeign 服务调用

### v1.0.0
- 基础微服务架构
- SSO 单点登录功能
- 用户中心 CRUD 功能
