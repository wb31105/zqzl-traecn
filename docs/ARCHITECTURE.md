# 技术架构说明

## 一、整体架构

### 1.1 架构设计理念

本项目采用**轻量化微服务架构**，核心设计原则：

- **简化基础设施**：移除冗余的注册中心，采用静态地址 + Docker 网络通信
- **高性能通信**：服务间采用 gRPC 高性能 RPC 框架
- **统一网关入口**：使用 APISIX 作为 API 网关，负责路由转发、负载均衡
- **前后端分离**：前端采用 React SPA，后端提供 RESTful API

### 1.2 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        客户端浏览器                                │
└──────────────────────────────────┬──────────────────────────────┘
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

### 1.3 技术栈

| 层级         | 技术选型            | 版本      | 说明          |
| ---------- | --------------- | ------- | ----------- |
| **网关层**    | APISIX          | 2.15    | 高性能 API 网关  |
| **配置存储**   | etcd            | 3.5     | APISIX 配置存储 |
| **前端框架**   | React           | 18.x    | SPA 单页应用    |
| **前端路由**   | React Router    | 6.x     | 路由管理        |
| **后端框架**   | Spring Boot     | 2.7.18  | Java 微服务框架  |
| **服务间通信**  | gRPC            | 1.59.0  | 高性能 RPC 框架  |
| **ORM 框架** | Spring Data JPA | -       | 数据持久化       |
| **数据库**    | H2              | 2.1.214 | 内存数据库（开发环境） |
| **安全框架**   | Spring Security | -       | 安全认证        |
| **构建工具**   | Maven           | 3.8+    | 后端构建        |
| **构建工具**   | npm             | -       | 前端构建        |
| **容器化**    | Docker          | -       | 容器化部署       |

***

## 二、项目目录结构

```
zqzl-traecn/
├── docs/                           # 项目文档目录
│   ├── README.md                   # 项目概览（本文件）
│   ├── ARCHITECTURE.md             # 技术架构说明
│   ├── BUSINESS_FLOW.md            # 业务流程说明
│   ├── DEPLOYMENT.md               # 部署指南
│   └── API.md                      # API 接口文档
│
├── ops/                            # 运维配置
│   ├── docker/
│   │   └── apisix/                 # APISIX 网关配置
│   │       ├── Dockerfile
│   │       ├── config.yaml         # APISIX 主配置
│   │       └── apisix.yaml         # 路由规则配置
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
│       │   │   │   ├── client/      # gRPC 客户端
│       │   │   │   ├── controller/  # REST API 控制器
│       │   │   │   ├── service/     # 业务逻辑
│       │   │   │   ├── dto/         # 数据传输对象
│       │   │   │   └── config/      # 配置类
│       │   │   ├── proto/           # gRPC Proto 定义
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
│           │   │   ├── client/      # gRPC 客户端
│           │   │   ├── controller/  # REST API 控制器
│           │   │   ├── service/     # 业务逻辑
│           │   │   ├── dto/         # 数据传输对象
│           │   │   ├── entity/      # 数据库实体
│           │   │   ├── repository/  # 数据访问层
│           │   │   ├── util/        # 工具类
│           │   │   └── config/      # 配置类
│           │   ├── proto/           # gRPC Proto 定义
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
│       │   │   ├── components/      # React 组件
│       │   │   └── setupProxy.js    # 开发环境代理配置
│       │   ├── .env.development     # 开发环境配置
│       │   └── package.json
│       │
│       └── user-web/                # 管理平台前端
│           ├── deploy/
│           │   ├── Dockerfile
│           │   ├── nginx.conf
│           │   └── build.sh
│           ├── src/
│           │   ├── components/      # React 组件
│           │   └── setupProxy.js    # 开发环境代理配置
│           ├── .env.development     # 开发环境配置
│           └── package.json
│
├── docker-compose.yml               # Docker 编排文件
└── README.md                        # 项目入口文档
```

***

## 三、核心服务说明

### 3.1 SSO 认证服务 (sso-server)

**职责**：

- 用户登录认证
- 票据生成与验证
- 用户注册（转发给 user-server）
- 忘记密码（转发给 user-server）
- 图形验证码生成
- 手机验证码发送

**核心组件**：

- `AuthService`：认证业务逻辑
- `TicketService`：票据管理
- `CaptchaService`：图形验证码服务
- `VerificationCodeService`：手机验证码服务
- `UserServiceClient`：gRPC 客户端，调用 user-server
- `SsoGrpcServiceImpl`：gRPC 服务端，提供票据验证

**端口配置**：

- HTTP: 8080（开发环境）
- gRPC: 9090

### 3.2 用户中心服务 (user-server)

**职责**：

- 用户数据 CRUD 管理
- 用户密码验证
- JWT Token 生成
- 用户权限管理
- 用户状态管理（启用/禁用）

**核心组件**：

- `UserService`：用户业务逻辑
- `UserRepository`：数据访问层
- `JwtUtil`：JWT 工具类
- `CaptchaService`：图形验证码服务
- `SsoClient`：gRPC 客户端，调用 sso-server
- `UserGrpcServiceImpl`：gRPC 服务端，提供用户相关服务

**端口配置**：

- HTTP: 8081（开发环境）、8080（容器环境）
- gRPC: 9091

### 3.3 APISIX 网关

**职责**：

- 统一入口路由
- 路径重写
- 负载均衡
- 跨域处理

**路由规则**：

- `/` → sso-web:80（登录门户）
- `/user/*` → user-web:80（管理平台）
- `/v1/auth/*` → sso-server:8080（认证服务）
- `/v1/users/*` → user-server:8080（用户服务）

***

## 四、服务间通信机制

### 4.1 gRPC 通信

**Proto 定义位置**：

- [user.proto](../backend/services/user-server/src/main/proto/user.proto)
- [sso.proto](../backend/services/sso-server/src/main/proto/sso.proto)

**UserService gRPC 接口**：

```protobuf
service UserService {
  rpc ValidateLogin(LoginRequest) returns (LoginResponse) {}
  rpc Register(RegisterRequest) returns (RegisterResponse) {}
  rpc ForgotPassword(ForgotPasswordRequest) returns (ForgotPasswordResponse) {}
  rpc FindUserByIdentifier(FindUserRequest) returns (FindUserResponse) {}
}
```

**SsoService gRPC 接口**：

```protobuf
service SsoService {
  rpc ValidateTicket(TicketValidationRequest) returns (TicketValidationResponse) {}
}
```

### 4.2 调用关系

```
sso-server → gRPC → user-server
  - 登录验证
  - 用户注册
  - 忘记密码
  - 查找用户

user-server → gRPC → sso-server
  - 票据验证
```

***

## 五、数据存储设计

### 5.1 数据库表结构

**用户表 (users)**：

| 字段                | 类型            | 说明           | 约束                           |
| ----------------- | ------------- | ------------ | ---------------------------- |
| id                | Long          | 主键           | PRIMARY KEY, AUTO\_INCREMENT |
| username          | String        | 用户名          | UNIQUE, NOT NULL             |
| password          | String        | 密码（BCrypt加密） | NOT NULL                     |
| email             | String        | 邮箱           | UNIQUE                       |
| phone             | String        | 手机号          | <br />                       |
| nickname          | String        | 昵称           | <br />                       |
| avatar            | String        | 头像URL        | <br />                       |
| role              | String        | 角色           | ADMIN/USER                   |
| enabled           | Boolean       | 是否启用         | <br />                       |
| login\_attempts   | Integer       | 登录失败次数       | <br />                       |
| last\_login\_time | LocalDateTime | 最后登录时间       | <br />                       |
| created\_at       | LocalDateTime | 创建时间         | <br />                       |
| updated\_at       | LocalDateTime | 更新时间         | <br />                       |

### 5.2 内存存储

**sso-server 内存存储**：

- `captchaStore`：图形验证码（ConcurrentHashMap）
- `ticketStore`：登录票据（ConcurrentHashMap）
- `codeStorage`：手机验证码（ConcurrentHashMap）
- `loginAttempts`：登录失败计数（ConcurrentHashMap）
- `verifiedUsers`：忘记密码验证用户（ConcurrentHashMap）

***

## 六、配置管理

### 6.1 后端配置文件

每个服务包含两个配置文件：

| 文件                    | 用途         | 激活方式                         |
| --------------------- | ---------- | ---------------------------- |
| `application.yml`     | 默认配置（生产环境） | 默认加载                         |
| `application-dev.yml` | 开发环境覆盖     | `SPRING_PROFILES_ACTIVE=dev` |

### 6.2 环境变量说明

**sso-server 环境变量**：

| 变量名                      | 说明                  | 默认值         |
| ------------------------ | ------------------- | ----------- |
| `SPRING_PROFILES_ACTIVE` | Spring Profile      | dev         |
| `SERVER_PORT`            | HTTP 端口             | 8080        |
| `GRPC_SERVER_PORT`       | gRPC 端口             | 9090        |
| `USER_GRPC_HOST`         | user-server gRPC 地址 | user-server |
| `USER_GRPC_PORT`         | user-server gRPC 端口 | 9091        |

**user-server 环境变量**：

| 变量名                      | 说明                 | 默认值        |
| ------------------------ | ------------------ | ---------- |
| `SPRING_PROFILES_ACTIVE` | Spring Profile     | dev        |
| `SERVER_PORT`            | HTTP 端口            | 8080       |
| `GRPC_SERVER_PORT`       | gRPC 端口            | 9091       |
| `SSO_GRPC_HOST`          | sso-server gRPC 地址 | sso-server |
| `SSO_GRPC_PORT`          | sso-server gRPC 端口 | 9090       |

***

## 七、安全设计

### 7.1 密码安全

- 使用 BCrypt 加密存储用户密码
- 密码强度要求：6-100 位字符

### 7.2 登录安全

- 登录失败次数限制
- 图形验证码机制（失败1次后启用）
- 票据一次性使用机制

### 7.3 接口安全

- CORS 跨域配置
- 无状态 Session 策略
- 关键接口参数校验（JSR-380）

