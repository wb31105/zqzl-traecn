# ZQZL 微服务架构平台

基于微服务架构的企业级应用平台，采用分层架构设计，支持多环境部署。

---

## 目录

- [架构概览](#架构概览)
- [服务列表](#服务列表)
- [快速开始](#快速开始)
- [日志查询](#日志查询)
- [服务维护](#服务维护)
- [API 接口文档](#api-接口文档)
- [技术栈](#技术栈)

---

## 架构概览

### 整体架构图（域名架构版）

```
┌─────────────────────────────────────────────────────────────────┐
│                        用户浏览器 / 客户端                        │
│                                                                 │
│        ┌─────────────────┐    ┌─────────────────┐              │
│        │ sso.bw.com      │    │ admin.bw.com    │              │
│        │ (登录门户)       │    │ (管理平台)       │              │
│        └────────┬────────┘    └────────┬────────┘              │
│                 │                        │                       │
└─────────────────┼────────────────────────┼───────────────────────┘
                  │                        │
                  ▼                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Nginx 反向代理 (端口:80)                       │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Server: sso.bw.com      →  sso-web (3000) 登录页面       │  │
│  │  Server: admin.bw.com    →  user-web (3031) 用户管理      │  │
│  │  Server: api.bw.com      →  gateway-server (9000) API 网关│  │
│  └───────────────────────────────────────────────────────────┘  │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│              Spring Cloud Gateway 后端网关 (端口:9000)           │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  /v1/auth/**  →  sso-server (8080)  单点登录服务           │  │
│  │  /v1/users/** →  user-server (8081) 用户中心服务           │  │
│  └───────────────────────────────────────────────────────────┘  │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│               Eureka 服务注册与发现中心 (端口:8761)               │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              sso-server (8080)  已注册                     │  │
│  │              user-server (8081) 已注册                     │  │
│  │              gateway-server (9000) 已注册                  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                       业务微服务集群                               │
│  ┌──────────────────┐    ┌──────────────────────────────────┐  │
│  │  sso-server      │    │  user-server                      │  │
│  │  (端口:8080)     │    │  (端口:8081)                      │  │
│  │  单点登录服务     │    │  用户CRUD、认证、权限管理         │  │
│  │                  │    │  H2 数据库                        │  │
│  └──────────────────┘    └──────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 域名架构规范

#### 核心域名规则

| 域名 | 用途 | 说明 |
|------|------|------|
| **sso.bw.com** | SSO 单点登录门户 | 统一登录入口、注册、找回密码 |
| **admin.bw.com** | 管理平台入口 | 用户管理、系统配置等后台功能 |
| **api.bw.com** | 后端 API 接口 | 所有微服务 API 统一入口，带版本号 |

#### API 版本管理规则

✅ **强制版本号**：所有后端 API 路径必须包含版本号前缀
- 认证相关：`api.bw.com/v1/auth/**`
- 用户相关：`api.bw.com/v1/users/**`

✅ **版本升级策略**：
- 小版本迭代（兼容）：在 `/v1/` 下直接更新
- 大版本升级（不兼容）：新增 `/v2/`，保留 `/v1/` 过渡期

#### 前端环境变量配置

所有前端域名通过环境变量配置，支持多环境切换：

```bash
# .env.development / .env.production / .env.test
REACT_APP_SSO_DOMAIN=http://sso.bw.com      # SSO 域名
REACT_APP_ADMIN_DOMAIN=http://admin.bw.com  # 管理平台域名
REACT_APP_API_DOMAIN=http://api.bw.com      # API 域名
```

### 调用流程说明

1. **用户访问**: 根据功能访问不同的域名
   - 登录：`http://sso.bw.com`
   - 管理：`http://admin.bw.com`
   - API：`http://api.bw.com`

2. **前端路由**: Nginx 根据 `server_name` 分发到对应的前端应用
3. **API 路由**: 前端通过独立的 `api.bw.com` 域名调用后端接口
4. **服务发现**: Gateway 通过 Eureka 发现并调用后端微服务
5. **服务间调用**: sso-server 通过 OpenFeign 调用 user-server

---

## 服务列表

### 后端服务

| 服务名称 | 端口 | 上下文路径 | API 路径前缀 | 职责 |
|---------|------|-----------|-------------|------|
| **eureka-server** | 8761 | / | - | 服务注册与发现中心 |
| **gateway-server** | 9000 | / | /v1/** | 后端 API 网关、负载均衡、路由转发、版本管理 |
| **sso-server** | 8080 | / | /v1/auth | 单点登录服务、票据生成/验证、转发认证请求 |
| **user-server** | 8081 | / | /v1/users | 用户CRUD、注册/登录验证、验证码、角色权限 |

### 前端服务（域名架构）

| 服务名称 | 内部端口 | 外部域名 | 职责 |
|---------|---------|---------|------|
| **Nginx Gateway** | 80 | - | 多域名反向代理、统一入口 |
| **sso-web** | 3000 | `sso.bw.com` | 统一登录门户、注册/找回密码 |
| **user-web** | 3031 | `admin.bw.com` | 用户管理后台 |

### API 域名服务

| 域名 | 映射后端服务 | 说明 |
|------|------------|------|
| `api.bw.com` | gateway-server:9000 | 所有后端 API 统一入口，带版本号 |

---

## 快速开始

### 前置条件

- JDK 11+
- Maven 3.6+
- Node.js 14+
- Nginx (可选，用于统一入口)

### 方式一：一键启动（推荐）

项目根目录提供了一键启动脚本：

```bash
# 进入项目根目录
cd /Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn

# 一键启动所有服务
./start-all.sh

# 停止所有服务
./stop-all.sh
```

**启动顺序**:
1. Eureka 注册中心 (8761)
2. User 后端服务 (8081)
3. SSO 后端服务 (8080)
4. Gateway 网关服务 (9000)
5. SSO 前端 (3000)
6. User 前端 (3031)
7. Nginx 前端网关 (80)

### 方式二：手动启动（开发调试）

#### 1. 构建框架层（首次必须执行）

```bash
cd backend/frameworks/zqzl-framework
mvn clean install
```

#### 2. 启动后端服务

**启动 Eureka 注册中心（必须先启动）**:
```bash
cd backend/services/eureka-server
mvn spring-boot:run
```
访问: http://localhost:8761

**启动用户中心服务**:
```bash
cd backend/services/user-server
mvn spring-boot:run
```
运行在: http://localhost:8081/user

**启动 SSO 单点登录服务**:
```bash
cd backend/services/sso-server
mvn spring-boot:run
```
运行在: http://localhost:8080/sso

**启动 Gateway 网关服务**:
```bash
cd backend/services/gateway-server
mvn spring-boot:run
```
运行在: http://localhost:9000

#### 3. 启动前端应用

**启动 SSO 登录门户**:
```bash
cd frontend/apps/sso-web
npm install
npm start
```
运行在: http://localhost:3000

**启动用户管理后台**:
```bash
cd frontend/apps/user-web
npm install
npm start
```
运行在: http://localhost:3031

#### 4. 启动 Nginx 前端网关

```bash
cd frontend/gateway
./start-nginx.sh
```

### 域名配置（必须）

使用域名架构前，需要在本地 hosts 文件添加域名映射：

**macOS / Linux**:
```bash
sudo echo "127.0.0.1 sso.bw.com admin.bw.com api.bw.com" >> /etc/hosts
```

**Windows** (以管理员身份运行 cmd):
```
echo 127.0.0.1 sso.bw.com admin.bw.com api.bw.com >> C:\Windows\System32\drivers\etc\hosts
```

### 验证启动

1. 访问 Eureka 控制台: http://localhost:8761，确认所有服务已注册
2. 访问 SSO 登录门户: http://sso.bw.com
3. 访问管理平台: http://admin.bw.com（未登录会自动跳转到 SSO）
4. 使用默认账号登录: `admin` / `admin123`

---

## 日志查询

### 后端服务日志

所有 Spring Boot 服务的日志输出在控制台。如果需要持久化日志，可以配置日志文件。

#### 查看服务输出日志

每个服务在独立的终端窗口运行，可以直接查看对应终端的输出。

#### 配置日志文件（可选）

在各服务的 `application.yml` 中添加：

```yaml
logging:
  file:
    name: logs/${spring.application.name}.log
  level:
    root: INFO
    com.zqzl: DEBUG
```

### Nginx 网关日志

Nginx 日志位于 `frontend/gateway/logs/` 目录：

```bash
# 访问日志
tail -f frontend/gateway/logs/access.log

# 错误日志
tail -f frontend/gateway/logs/error.log
```

### 前端应用日志

前端应用日志输出在浏览器控制台。

- Chrome: F12 → Console 标签
- Firefox: F12 → 控制台

---

## 服务维护

### 查看服务状态

#### 统一访问入口

**所有应用都通过 Nginx 网关（端口 80）访问：**

| 应用 | 访问地址 | 说明 |
|------|---------|------|
| **SSO 登录门户** | http://localhost | 统一登录入口，首页 |
| SSO 登录备用 | http://localhost/sso/ | 登录页面备用路径 |
| **用户管理后台** | http://localhost/user-web/ | 用户管理系统 |
| Eureka 控制台 | http://localhost:8761 | 服务注册中心 |

#### Eureka 控制台

访问 http://localhost:8761，可以查看：
- 已注册的服务列表
- 服务实例状态（UP/DOWN）
- 服务健康状况

#### Gateway 路由信息

Gateway 集成了 Actuator，可以查看路由配置（通过 Nginx 访问）：

```bash
# 查看所有路由（推荐：通过 Nginx 访问）
curl http://localhost/actuator/gateway/routes

# 或直接访问 Gateway
curl http://localhost:9000/actuator/gateway/routes

# 查看网关全局过滤器
curl http://localhost/actuator/gateway/globalfilters
```

### 常用运维命令

#### 端口占用检查

```bash
# 检查所有服务端口
lsof -ti :8761 -ti :9000 -ti :8080 -ti :8081 -ti :3000 -ti :3031 -ti :80
```

#### 强制停止服务

```bash
# 停止占用指定端口的进程
kill -9 $(lsof -ti :8080)

# 或者使用停止脚本
./stop-all.sh
```

### 服务扩缩容

#### 后端服务水平扩展

由于使用了 Eureka + Ribbon 负载均衡，可以启动多个相同的服务实例：

```bash
# 启动第一个 user-server 实例（端口 8081）
cd backend/services/user-server
mvn spring-boot:run

# 启动第二个 user-server 实例（端口 8082）
cd backend/services/user-server
mvn spring-boot:run -Dserver.port=8082
```

Gateway 会自动发现并进行负载均衡。

### 常见问题排查

#### 1. 服务注册失败

**症状**: Eureka 控制台看不到服务
**排查步骤**:
```bash
# 检查 Eureka 是否正常运行
curl http://localhost:8761

# 检查服务配置中的 Eureka 地址是否正确
grep -A 5 "eureka:" backend/services/*/src/main/resources/application.yml
```

#### 2. 网关路由失败

**症状**: API 请求返回 404 或 503
**排查步骤**:
```bash
# 查看 Gateway 路由配置
curl http://localhost:9000/actuator/gateway/routes

# 检查目标服务是否在 Eureka 中注册
# 访问 Eureka 控制台查看服务状态
```

#### 3. 跨域 (CORS) 问题

**症状**: 
- 前端请求报错 "No 'Access-Control-Allow-Origin' header"
- 浏览器控制台显示 "Invalid CORS request"
- API 请求返回 HTTP 403 状态码

**架构说明**:
当前系统采用 **双层 CORS 配置** 确保兼容性：
```
浏览器 → Nginx (80) → Gateway (9000) → 后端服务 (8080/8081)
                                       ↓              ↓
                                   CORS 配置       CORS 配置
```

**CORS 配置位置**:
1. **Spring Cloud Gateway (统一入口)** - `backend/services/gateway-server/src/main/resources/application.yml`
   - 全局 CORS 配置，处理所有 API 请求
   - 使用 `allowedOriginPatterns: "*"` 允许所有来源
   - 支持所有 HTTP 方法 (GET, POST, PUT, DELETE, OPTIONS)
   - Nginx 只做透明转发，不处理 CORS

2. **后端业务服务 (sso-server/user-server)** - `SecurityConfig.java`
   - 各服务独立配置 CORS，确保直接访问时也能工作
   - 使用 `setAllowedOriginPatterns(Arrays.asList("*"))`
   - 已移除 Controller 上的 `@CrossOrigin` 局部注解，避免冲突

**排查步骤**:
```bash
# 1. 检查请求路径是否通过 Nginx
# 应该通过 http://localhost/sso/api/... 或 http://localhost/user/api/...
# 而不是直接访问 http://localhost:8080 或 http://localhost:9000

# 2. 检查 OPTIONS 预检请求是否正常
curl -X OPTIONS -i http://localhost/sso/api/auth/captcha

# 3. 查看 Gateway 日志确认路由是否正确
# 在 Gateway 控制台查看路由转发日志

# 4. 重启后端服务（修改 CORS 配置后需要重启）
cd backend/services/gateway-server && mvn spring-boot:run
cd backend/services/sso-server && mvn spring-boot:run
cd backend/services/user-server && mvn spring-boot:run
```

**配置原则**:
- ✅ 所有前端 API 请求统一通过 Nginx (端口 80)
- ✅ CORS 配置只在 Gateway 层处理，避免重复配置
- ✅ 使用 `allowedOriginPatterns` 而非 `allowedOrigins`
- ✅ 确保 OPTIONS 请求能够正确响应

#### 4. 登录流程排查

**完整登录流程**:
```
1. 访问 http://localhost (Nginx → sso-web 3000)
2. 输入用户名密码点击登录
3. 前端调用 POST /sso/api/auth/login (Nginx → Gateway → sso-server)
4. sso-server 通过 OpenFeign 调用 user-server 验证用户
5. sso-server 生成票据 (ticket) 并返回
6. 如果有 redirect 参数，跳转到回调地址
7. 回调页面调用 /sso/api/auth/validate-ticket 验证票据
8. 验证成功后进入用户管理页面
```

**排查步骤**:
1. 检查前端 API_BASE_URL 配置是否为 `http://localhost`
2. 检查浏览器 Network 面板，确认请求地址和响应头
3. 检查 sso-server 控制台，确认 OpenFeign 调用 user-server 是否成功
4. 检查 Eureka 控制台，确认 user-server 和 sso-server 都已注册

#### 5. Nginx 启动失败

**症状**: 80 端口被占用
**解决方案**:
```bash
# 查找占用 80 端口的进程
sudo lsof -ti :80

# 停止占用进程或修改 Nginx 配置端口
# 修改 frontend/gateway/nginx.conf 中的 listen 端口
```

#### 6. 前端应用路由问题（子路径部署）

**症状**:
- 访问 `/user-web/` 报 `Failed to load module script: Expected a JavaScript-or-Wasm module script but the server responded with a MIME type of "text/html"`
- 控制台警告 `No routes matched location "/user-web/"`
- 未登录状态下访问业务地址不跳转登录页

**问题原因**:
- React 应用在子路径（/user-web、/sso）下部署时，需要正确配置 `homepage` 和 React Router 的 `basename`
- 静态资源路径不正确导致 JS/CSS 文件加载失败
- 路由跳转逻辑未考虑子路径前缀

**已修复配置**:

**user-web 配置 (frontend/apps/user-web/)**:
1. `package.json`: 添加 `"homepage": "/user-web"`
2. `src/App.js`: Router 添加 `basename="/user-web"`
3. `public/index.html`: 添加 `<base href="%PUBLIC_URL%/" />`
4. 修正 SSO_WEB_URL 为 `http://localhost/sso`
5. 所有跳转路径添加 `/user-web` 前缀

**sso-web 配置 (frontend/apps/sso-web/)**:
1. `package.json`: 添加 `"homepage": "/sso"`
2. `src/App.js`: Router 添加 `basename="/sso"`
3. `public/index.html`: 添加 `<base href="%PUBLIC_URL%/" />`
4. 所有内部链接（登录、注册、忘记密码）添加 `/sso` 前缀
5. 回调 URL 构建逻辑根据 redirect 参数动态添加路径前缀

**Nginx 配置 (frontend/gateway/nginx.conf)**:
- 确保 `rewrite` 规则正确去除路径前缀后转发到对应前端服务
- 静态资源路径正确映射

**验证修复**:
```bash
# 1. 访问用户管理（未登录状态应自动跳转到 SSO 登录页）
open http://localhost/user-web/

# 2. 访问登录页
open http://localhost/sso/

# 3. 登录成功后应正确回调到 /user-web/sso/callback
# 4. 票据验证成功后跳转到 /user-web/users
```

**开发注意事项**:
- ✅ 所有前端内部跳转（a 标签、window.location、navigate）都要包含子路径前缀
- ✅ 使用 `process.env.PUBLIC_URL` 或配置的 basename 来构建路径
- ✅ Nginx 的 rewrite 规则要与 homepage 配置匹配
- ✅ 通配符路由 `*` 要配置，处理未匹配路径的重定向
- ✅ SSO 回调要正确构建 redirect URL 的路径部分

---

## API 接口文档

### 通过 API 域名访问（推荐）

所有 API 通过统一域名 `http://api.bw.com` 访问，带版本号：

#### SSO 单点登录接口 (v1 版本)

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/v1/auth/login` | 用户登录（返回票据） |
| POST | `/v1/auth/register` | 用户注册 |
| POST | `/v1/auth/forgot-password` | 重置密码 |
| GET | `/v1/auth/captcha` | 获取验证码 |
| GET | `/v1/auth/check-captcha` | 检查是否需要验证码 |
| GET | `/v1/auth/validate-ticket` | 验证票据有效性 |

#### User 用户中心接口 (v1 版本)

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/v1/users` | 获取用户列表（分页、搜索） |
| GET | `/v1/users/{id}` | 获取单个用户详情 |
| PUT | `/v1/users/{id}` | 更新用户基本信息 |
| PUT | `/v1/users/{id}/reset-password` | 重置用户密码 |
| PUT | `/v1/users/{id}/toggle-status` | 切换用户启用/禁用状态 |
| DELETE | `/v1/users/{id}` | 删除用户 |

### 直接访问服务（调试用）

也可以直接访问后端服务端口进行调试：
- SSO 服务: http://localhost:8080/v1/auth
- User 服务: http://localhost:8081/v1/users

### API 版本规范

**版本号位置**：URL 路径前缀，如 `/v1/auth/login`

**版本管理策略**：
1. **v1（当前稳定版）**：生产环境使用
2. **v2（开发中）**：新功能开发，不兼容 v1 时启用
3. **版本兼容期**：新版本发布后，旧版本保留 3 个月过渡期

**版本请求示例**：
```bash
# v1 版本（推荐）
curl http://api.bw.com/v1/auth/login

# 未来 v2 版本
curl http://api.bw.com/v2/auth/login
```

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 2.7.x | 应用框架 |
| Spring Cloud | 2021.0.x | 微服务框架 |
| Spring Cloud Netflix Eureka | - | 服务注册与发现 |
| Spring Cloud Gateway | - | API 网关 |
| Spring Cloud OpenFeign | - | 声明式服务调用 |
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
| CSS3 | 样式 |

### 基础设施

| 技术 | 说明 |
|------|------|
| Nginx | 前端反向代理、负载均衡 |
| Maven | 项目构建、依赖管理 |
| npm | 前端包管理 |

---

## 默认账号

- 用户名: `admin`
- 密码: `admin123`
- 角色: ADMIN（管理员）

---

## 开发规范

### 微服务开发规范

1. 各微服务独立开发、部署
2. 微服务之间通过 OpenFeign 调用，不直接依赖
3. 所有 API 请求通过网关，不直接调用后端服务
4. 框架层变更需谨慎评估，发布后更新所有服务依赖
5. 公共模块变更需谨慎评估影响
6. 前后端接口遵循 RESTful 规范
7. 代码提交前请确保本地测试通过
8. 所有配置必须支持多环境切换，敏感配置使用环境变量注入
9. DTO 与 Entity 分离，避免直接暴露数据库实体
10. 使用 BCrypt 加密存储所有用户密码

### 域名与路由规范（强制）

✅ **前端域名规范**：
- SSO 登录门户统一使用 `sso.bw.com`
- 管理平台统一使用 `admin.bw.com`
- 不允许使用路径前缀（如 `/sso`、`/user-web`）区分应用

✅ **后端 API 域名规范**：
- 所有 API 统一使用 `api.bw.com` 域名
- 强制添加版本号前缀：`/v1/`、`/v2/` 等
- 示例：`api.bw.com/v1/auth/login`

✅ **前端环境变量配置规范**：
```bash
# 正确配置
REACT_APP_SSO_DOMAIN=http://sso.bw.com
REACT_APP_ADMIN_DOMAIN=http://admin.bw.com
REACT_APP_API_DOMAIN=http://api.bw.com

# 禁止硬编码域名
# ❌ const SSO_URL = 'http://localhost/sso'
```

✅ **后端 Controller 路径规范**：
```java
// 正确配置
@RestController
@RequestMapping("/v1/auth")  // 带版本号
public class AuthController { ... }

// 禁止无版本号
// ❌ @RequestMapping("/api/auth")
```

✅ **Nginx 配置规范**：
- 每个域名独立一个 `server` 配置块
- 使用 `server_name` 区分不同域名
- API 请求统一转发到 gateway-server

---

## 版本历史

### v3.0.0 (当前版本 - 域名架构版)
- ✅ **域名架构重构**：实现三域名分离架构
  - `sso.bw.com` - SSO 单点登录门户
  - `admin.bw.com` - 管理平台入口
  - `api.bw.com` - 后端 API 统一入口
- ✅ **API 版本管理**：所有接口统一添加 `/v1` 版本前缀
  - 认证接口：`/v1/auth/**`
  - 用户接口：`/v1/users/**`
  - 支持平滑版本升级
- ✅ **前端环境变量重构**：所有域名通过环境变量配置
  - 支持多环境切换（dev / test / prod）
  - 移除硬编码域名
- ✅ **后端路径规范**：移除服务上下文路径前缀
  - sso-server: 移除 `/sso` 上下文
  - user-server: 移除 `/user` 上下文
  - Controller 统一使用 `/v1/` 前缀
- ✅ **Nginx 多域名配置**：基于 server_name 的虚拟主机配置
- ✅ **文档更新**：完整的域名架构说明、规范文档

### v2.0.0
- ✅ 新增 Eureka 服务注册与发现中心
- ✅ 新增 Spring Cloud Gateway 后端网关
- ✅ 新增 Nginx 前端反向代理网关
- ✅ 实现服务发现与负载均衡
- ✅ 统一前端入口，解决跨域问题
- ✅ 集成 OpenFeign 声明式服务调用
- ✅ 更新启动脚本，支持一键启停所有服务
- ✅ 完善文档，添加架构图、运维指南

### v1.0.0
- 基础微服务架构
- SSO 单点登录功能
- 用户中心 CRUD 功能
