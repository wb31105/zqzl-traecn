# SSO 认证体系问题分析与解决方案

## 重要说明：已完成标准 OAuth2 授权码模式改造

本文档分析的**所有问题已通过标准 OAuth2.0 Authorization Code 模式重构解决**。

改造后架构详见：
- [BUSINESS_FLOW.md - OAuth2 授权码登录流程](BUSINESS_FLOW.md#一oauth2-授权码登录流程)
- 本章节剩余内容为改造前问题分析记录

---

## 一、（改造前）当前实现现状概述

### 1.1 实际运行流程

```
浏览器访问 /user
    → 前端 ProtectedRoute 检查 localStorage('sso_token')
    → 没有则重定向到 SSO 登录页 http://localhost:3000?redirect=http://localhost/user
    → 用户输入用户名密码
    → sso-web 调用 sso-server /v1/auth/login
    → sso-server 通过 gRPC 调用 user-server 验证密码
    → user-server 返回 JWT 给 sso-server
    → sso-server 忽略 JWT，生成 ST-xxx ticket 存入内存 Map
    → sso-server 返回 ticket 给 sso-web
    → sso-web 重定向到 http://localhost/user/sso/callback?originalPath=/users&ticket=ST-xxx&username=xxx
    → user-web 的 SsoCallback 组件拿到 ticket
    → user-web 直接调用 sso-server /v1/auth/validate-ticket?ticket=ST-xxx
    → sso-server 从 Map 查找并删除 ticket，返回 username
    → user-web 将 ticket 存入 localStorage('sso_token')
    → 跳转 /users
    → 后续所有请求，后端 API 不做任何 Token 校验
```

### 1.2 关键代码位置

| 功能 | 文件 | 行号 |
|------|------|------|
| SSO 登录请求 | [Login.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/sso-web/src/components/Login.js#L76) | 76 |
| 回调 URL 拼接（有Bug） | [Login.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/sso-web/src/components/Login.js#L85-L88) | 85-88 |
| 前端票据验证 | [SsoCallback.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/src/components/SsoCallback.js#L30-L60) | 30-60 |
| 前端路由保护 | [App.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/src/App.js#L9-L19) | 9-19 |
| 后端票据生成 | [AuthService.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/service/AuthService.java#L82-L89) | 82-89 |
| 后端票据验证接口 | [AuthController.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/controller/AuthController.java#L62-L75) | 62-75 |
| user-server 安全配置（全开放） | [SecurityConfig.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/user-server/src/main/java/com/user/config/SecurityConfig.java#L34) | 34 |
| 未使用的 gRPC 票据验证客户端 | [SsoClient.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/user-server/src/main/java/com/user/client/SsoClient.java) | 全文 |
| 网关路由配置 | [apisix.yaml](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/ops/docker/apisix/apisix.yaml) | 全文 |

---

## 二、与标准 OAuth 2.0 流程的差异分析

### 2.1 标准 OAuth 2.0 Authorization Code 流程

```
┌──────────┐   1. 未登录访问应用    ┌──────────────────┐
│  浏览器   │ ──────────────────────►│  业务应用(后端)    │
└──────────┘                         └────────┬─────────┘
                                              │ 2. 重定向到授权服务器
                                              ▼
┌──────────┐   3. 用户登录+授权     ┌──────────────────┐
│  浏览器   │ ──────────────────────►│  授权服务器(SSO)  │
└──────────┘                         └────────┬─────────┘
                                              │ 4. 生成 Authorization Code
                                              │    重定向回 redirect_uri?code=xxx
                                              ▼
┌──────────┐   5. 携带 code 回调    ┌──────────────────┐
│  浏览器   │ ──────────────────────►│  业务应用(后端)    │
└──────────┘                         └────────┬─────────┘
                                              │ 6. 后端用 code + client_secret
                                              │    调用授权服务器 /token 接口
                                              ▼
                                      ┌──────────────────┐
                                      │  授权服务器(SSO)  │
                                      │  验证 code + secret
                                      │  返回 Access Token
                                      └────────┬─────────┘
                                              ▼
                                      ┌──────────────────┐
                                      │  业务应用(后端)    │
                                      │  保存 Access Token
                                      │  创建本地 Session
                                      │  返回给浏览器
                                      └──────────────────┘
```

### 2.2 逐项对比差异

| 对比维度 | 标准 OAuth 2.0 | 当前实现 | 差异说明 |
|----------|---------------|----------|----------|
| **票据类型** | Authorization Code（一次性，短时效） | ST-xxx ticket（一次性，无时效） | 类似但无过期时间 |
| **票据验证方** | 业务应用**后端** | 业务应用**前端** | **核心差异1：验证方位置错误** |
| **认证凭证** | client_id + client_secret | 无 | **核心差异2：无应用身份认证** |
| **票据交换** | Code → Access Token（后端交换） | 无交换，直接存原始 ticket | **核心差异3：缺少凭证交换环节** |
| **会话凭证** | Access Token（有签名、有过期时间） | 原始 ST-xxx 字符串（无签名、无过期） | **核心差异4：凭证不安全** |
| **scope 权限** | 支持 scope 权限范围 | 无 | 无权限范围控制 |
| **用户授权** | 需要用户显式授权 | 无 | 无授权确认环节 |
| **后续请求认证** | Bearer Token 放 Header，后端校验 | 无，后端全开放 | **核心差异5：后端无认证** |
| **票据传输** | 通过 302 重定向，URL 携带 | 通过 JS 拼接 URL，302 重定向 | 传输方式类似但前端处理 |

### 2.3 结论：不属于 OAuth 2.0

当前实现**不是 OAuth 2.0**，最关键的差异是：

1. **没有 client_id/client_secret** - 业务应用没有在 SSO 服务器注册身份
2. **前端直接验证票据** - 违反了 OAuth 的"凭证由后端持有"原则
3. **没有 Access Token 交换** - Code 应该在后端交换成 Access Token，而不是直接存本地
4. **后端 API 完全不认证** - OAuth 要求后续请求携带 Access Token 并验证

---

## 三、与标准 CAS 流程的差异分析

### 3.1 标准 CAS 流程

```
┌──────────┐   1. 未登录访问应用    ┌──────────────────┐
│  浏览器   │ ──────────────────────►│  业务应用(后端)    │
└──────────┘                         └────────┬─────────┘
                                              │ 2. 检查 Session，无则重定向
                                              │    CAS Server/login?service=xxx
                                              ▼
┌──────────┐   3. 用户登录          ┌──────────────────┐
│  浏览器   │ ──────────────────────►│  CAS Server       │
└──────────┘                         └────────┬─────────┘
                                              │ 4. 验证成功，生成 ST(Service Ticket)
                                              │    重定向回 service?ticket=ST-xxx
                                              ▼
┌──────────┐   5. 携带 ticket 回调  ┌──────────────────┐
│  浏览器   │ ──────────────────────►│  业务应用(后端)    │
└──────────┘                         └────────┬─────────┘
                                              │ 6. 后端调用 CAS Server
                                              │    /serviceValidate?ticket=ST-xxx
                                              │    &service=xxx
                                              ▼
                                      ┌──────────────────┐
                                      │  CAS Server       │
                                      │  验证 ticket 有效
                                      │  返回 username
                                      └────────┬─────────┘
                                              ▼
                                      ┌──────────────────┐
                                      │  业务应用(后端)    │
                                      │  创建本地 Session
                                      │  设置 Cookie
                                      │  返回给浏览器
                                      └──────────────────┘
```

### 3.2 逐项对比差异

| 对比维度 | 标准 CAS | 当前实现 | 差异说明 |
|----------|----------|----------|----------|
| **票据格式** | ST-xxx（Service Ticket） | ST-xxx | ✅ 格式一致 |
| **票据验证方** | 业务应用**后端** | 业务应用**前端** | **核心差异1：验证方位置错误** |
| **验证方式** | 后端 HTTP 调用 CAS Server | 前端 JS 调用 REST API | **核心差异2：调用方位置错误** |
| **会话创建** | 后端创建 Session，设 Cookie | 前端存 localStorage | **核心差异3：会话存储位置错误** |
| **后续请求认证** | Session Cookie 自动携带，后端校验 | 无，后端全开放 | **核心差异4：后端无认证** |
| **service 参数** | 重定向时带 service 参数 | 带 redirect 参数 | ✅ 类似 |
| **票据有效期** | 短时效（通常几秒） | 无过期限制，直到被消费 | ❌ 无过期时间 |
| **单点登出** | 支持 SLO（Single Logout） | 不支持 | ❌ 无法实现 |
| **Proxy 认证** | 支持 Proxy Granting Ticket | 不支持 | ❌ 无代理认证能力 |
| **应用注册** | CAS Server 注册 service URL | 无注册，redirect 任意传 | ❌ 无应用白名单 |

### 3.3 结论：不是完整的 CAS

当前实现**借鉴了 CAS 的票据格式（ST-xxx），但关键环节的位置都错了**：

标准 CAS 的核心设计原则：**票据验证和会话创建必须在后端完成**

当前实现把这两个关键环节放到了前端，导致：
- 票据暴露给浏览器，可被截获和伪造
- 会话凭证不安全，容易被 XSS 攻击
- 后端 API 没有任何保护

---

## 四、当前存在的全部问题清单

### 4.1 Bug 类问题（需要立即修复）

#### Bug-1: 登录回调 URL 拼接错误

**现象**：修改网关代码后，页面定格在 `http://localhost/sso/callback`

**根因**：[Login.js#L85-L88](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/sso-web/src/components/Login.js#L85-L88)

```javascript
const url = new URL(redirect);
const originalPath = url.pathname || '/';
const callbackUrl = `${url.origin}/sso/callback?originalPath=${encodeURIComponent(originalPath)}`;
//                        ^^^^^^^^^^^^
//                        只取了 origin，丢掉了 redirect 的路径前缀
//                        例如 redirect 是 http://localhost/user
//                        拼出来是 http://localhost/sso/callback （正确应为 /user/sso/callback）
```

**影响**：
- `/user/sso/callback` → 正确路由到 user-web
- `/sso/callback` → 被兜底路由 `/*` 转给 sso-web → sso-web 中没有此路由 → 白屏/卡死

---

#### Bug-2: 票据验证接口对所有人开放

**根因**：[AuthController.java#L62-L75](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/controller/AuthController.java#L62-L75)

```java
@GetMapping("/validate-ticket")
public ResponseEntity<Map<String, Object>> validateTicket(@RequestParam String ticket) {
    // 任何人只要知道 ticket 就能调用
    // 没有验证调用方身份
}
```

**影响**：攻击者如果截获到 URL 中的 ticket，可以直接调用此接口验证并消费 ticket

---

### 4.2 安全类问题（需要重构解决）

#### SEC-1: 后端 API 完全无认证

**严重程度**：🔴 致命

**根因**：[SecurityConfig.java#L34](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/user-server/src/main/java/com/user/config/SecurityConfig.java#L34)

```java
.antMatchers("/api/auth/**", "/api/sso/**", "/api/users/**", "/h2-console/**").permitAll()
```

**影响**：
- 任何人直接 `curl http://localhost:8081/v1/users` 就能获取所有用户数据
- 不需要任何 token、cookie、session
- 整个后端等于裸奔

---

#### SEC-2: 前端直接验证票据（票据暴露给浏览器）

**严重程度**：🔴 高危

**根因**：[SsoCallback.js#L30-L60](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/src/components/SsoCallback.js#L30-L60)

```javascript
// 前端直接调 REST API 验证票据
const response = await axios.get(`${API_BASE_URL}/v1/auth/validate-ticket`, {
  params: { ticket }
});
```

**问题**：
- 标准做法：票据由**后端**持有和验证，不暴露给浏览器
- 当前做法：票据在 URL 中，浏览器可见，JS 可读

**影响**：
- ticket 在 URL 中会被记录到浏览器历史、服务器日志、代理日志
- 容易被 XSS 攻击窃取
- 容易被中间人攻击截获

---

#### SEC-3: 会话凭证是原始 ticket，无签名无过期

**严重程度**：🔴 高危

**根因**：[SsoCallback.js#L40-L41](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/src/components/SsoCallback.js#L40-L41)

```javascript
localStorage.setItem('sso_token', ticket);  // 直接存原始 ticket
localStorage.setItem('username', username);
```

**问题**：
- ticket 是一次性的，消费后就从 Map 删除了
- 但 localStorage 里还存着，后续请求也不校验
- ticket 没有签名，无法验证真伪
- ticket 没有过期时间，理论上永久有效

**影响**：
- 存的是一个已失效的凭证（ticket 已被消费删除）
- 前端的"登录状态"本质上是个假状态

---

#### SEC-4: 前端路由保护可被绕过

**严重程度**：🟡 中等

**根因**：[App.js#L9-L19](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/src/App.js#L9-L19)

```javascript
const isAuthenticated = !!localStorage.getItem('sso_token');
```

**问题**：
- 只需在浏览器控制台执行 `localStorage.setItem('sso_token', '任意值')` 就能绕过
- 没有与后端验证

---

#### SEC-5: 票据无过期时间

**严重程度**：🟡 中等

**根因**：[TicketService.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/service/TicketService.java)

```java
private final Map<String, String> ticketStore = new ConcurrentHashMap<>();
// 没有过期时间字段，没有定时清理
```

**问题**：
- ticket 永不过期，直到被消费
- 如果用户登录后没有完成回调，ticket 永远留在内存中
- 内存泄漏风险

---

#### SEC-6: 无应用白名单验证

**严重程度**：🟡 中等

**问题**：
- redirect 参数可以是任意 URL
- 没有白名单机制验证 redirect 是合法的应用地址
- 存在开放重定向攻击风险

---

### 4.3 架构类问题（影响可维护性）

#### ARCH-1: 每个前端应用重复实现 SSO 逻辑

**问题**：
- 每个前端应用都要实现 SsoCallback 组件
- 每个前端应用都要实现 ProtectedRoute 组件
- 每个前端应用都要配置 callback 路由

**影响**：
- 代码重复，维护成本高
- 认证逻辑变更时需要同步修改所有应用

---

#### ARCH-2: gRPC 票据验证客户端已存在但未使用

**根因**：[SsoClient.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/user-server/src/main/java/com/user/client/SsoClient.java)

user-server 已经有完整的 gRPC 客户端可以调用 sso-server 验证票据，但完全没被使用。

---

#### ARCH-3: 无法实现单点登出

**问题**：
- 每个前端应用各自管理 localStorage
- A 应用登出时，B 应用的 localStorage 还在
- 没有统一的会话管理

---

#### ARCH-4: 无统一的认证 SDK

**问题**：
- 没有封装统一的前端 SSO SDK
- 没有封装统一的后端认证拦截器
- 每个应用都要自己处理认证逻辑

---

## 五、解决方案

### 5.1 方案A：最小改动修复（推荐短期采用）

**目标**：修复致命安全问题，保持现有架构基本不变

#### 步骤 1：修复登录回调 URL 拼接 Bug

修改 [Login.js#L85-L88](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/sso-web/src/components/Login.js#L85-L88)：

```javascript
// 修改前（有Bug）
const url = new URL(redirect);
const originalPath = url.pathname || '/';
const callbackUrl = `${url.origin}/sso/callback?originalPath=${encodeURIComponent(originalPath)}`;

// 修改后
const url = new URL(redirect);
const originalPath = url.pathname || '/';
const callbackUrl = `${url.origin}${url.pathname}/sso/callback?originalPath=${encodeURIComponent(originalPath)}`;
//                        ^^^^^^^^^^^^^^^^
//                        保留 redirect 的路径前缀
```

#### 步骤 2：user-server 添加 JWT Token 校验

**目标**：后端 API 不再裸奔

1. user-server 新增 `/v1/auth/sso-callback` 接口，接收 ticket
2. 该接口通过 gRPC 调用 sso-server 验证票据（使用已有的 [SsoClient.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/user-server/src/main/java/com/user/client/SsoClient.java)）
3. 验证通过后生成 JWT 返回给前端
4. 添加 JWT 拦截器，校验所有 `/v1/users/*` 请求的 Token

修改 [SecurityConfig.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/user-server/src/main/java/com/user/config/SecurityConfig.java#L34)：

```java
// 修改前
.antMatchers("/api/auth/**", "/api/sso/**", "/api/users/**", "/h2-console/**").permitAll()

// 修改后
.antMatchers("/v1/auth/sso-callback", "/h2-console/**").permitAll()
.anyRequest().authenticated()  // 其他接口需要认证
```

#### 步骤 3：前端改为通过后端验证票据

修改 [SsoCallback.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/src/components/SsoCallback.js#L30-L60)：

```javascript
// 修改前（前端直接调 sso-server）
const response = await axios.get(`${API_BASE_URL}/v1/auth/validate-ticket`, {
  params: { ticket }
});

// 修改后（调 user-server 后端，由后端验证）
const response = await axios.post('/v1/auth/sso-callback', { ticket });
// user-server 后端通过 gRPC 调 sso-server 验证，返回 JWT
// 前端保存 JWT，后续请求带 Authorization header
```

#### 步骤 4：票据添加过期时间

修改 [TicketService.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/service/TicketService.java)：

```java
// 存储结构改为：ticket → { username, expireTime }
private final Map<String, TicketInfo> ticketStore = new ConcurrentHashMap<>();

public String generateTicket(String username) {
    String ticket = "ST-" + UUID.randomUUID().toString();
    ticketStore.put(ticket, new TicketInfo(username, System.currentTimeMillis() + 30000)); // 30秒过期
    return ticket;
}

public String validateTicket(String ticket) {
    TicketInfo info = ticketStore.get(ticket);
    if (info == null) return null;
    if (System.currentTimeMillis() > info.expireTime) {
        ticketStore.remove(ticket);
        return null;
    }
    return info.username;
}
```

---

### 5.2 方案B：标准 CAS 模式重构（推荐中期采用）

**目标**：遵循标准 CAS 流程，票据验证完全在后端完成

#### 流程变化

```
浏览器访问 /user
    → user-server 后端检查 Session/Cookie
    → 无则重定向到 SSO 登录页 ?service=http://localhost/user
    → 用户在 sso-web 登录
    → sso-server 验证成功，生成 ST
    → 重定向到 http://localhost/user?ticket=ST-xxx （直接回业务后端）
    → user-server 后端拿到 ticket
    → user-server 通过 gRPC 调 sso-server 验证
    → 验证通过，user-server 创建本地 Session
    → 返回 Set-Cookie: JSESSIONID=xxx
    → 浏览器后续请求自动携带 Cookie
    → user-server 校验 Session 放行请求
```

#### 关键变化点

| 变化点 | 说明 |
|--------|------|
| 回调地址 | 从前端 `/sso/callback` 改为后端接口（如 `/sso/callback`） |
| 票据验证 | 前端不再参与，完全由后端 gRPC 调用 |
| 会话保持 | 后端创建 Session + Cookie，不再用 localStorage |
| API 认证 | 通过 Session 拦截器自动校验 |
| SsoCallback 组件 | 删除，不再需要 |

#### 需要修改的文件

1. **删除** [SsoCallback.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/src/components/SsoCallback.js) - 不再需要
2. **修改** [App.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/src/App.js) - ProtectedRoute 改为检查后端返回的 Cookie/Session
3. **新增** user-server 的 `/sso/callback` 后端接口 - 处理票据验证
4. **修改** [Login.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/sso-web/src/components/Login.js#L85-L88) - 回调地址改为后端
5. **修改** [SecurityConfig.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/user-server/src/main/java/com/user/config/SecurityConfig.java) - 添加 Session 认证

---

### 5.3 方案C：网关层统一认证（推荐长期采用）

**目标**：认证下沉到 APISIX 网关，后端服务不再关心认证

#### 架构变化

```
                    ┌─────────────────────────┐
                    │    APISIX 网关           │
                    │                         │
  浏览器请求 ──────►│  1. JWT/Cookie 校验      │──── 认证失败 ──► 重定向到登录页
                    │  2. 路由转发             │
                    │                         │
                    └─────────┬───────────────┘
                              │ 认证通过
                              ▼
                    ┌─────────────────────────┐
                    │  后端服务                 │
                    │  不需要关心认证           │
                    │  只关心业务逻辑           │
                    └─────────────────────────┘
```

#### 优势

- 后端服务完全不关心认证，专注业务逻辑
- 认证逻辑统一在网关层，修改不需要改后端
- 所有微服务自动获得认证能力
- 支持多种认证方式（JWT、Session、OAuth）

#### 实现步骤

1. APISIX 添加 `jwt-auth` 或 `openid-connect` 插件
2. 网关统一校验 Token，无效则重定向到 SSO
3. 后端服务移除所有认证相关代码
4. 前端 SDK 封装 SSO 登录逻辑

---

### 5.4 方案对比

| 维度 | 方案A（最小修复） | 方案B（标准CAS） | 方案C（网关认证） |
|------|-------------------|------------------|------------------|
| **改动量** | 小 | 中 | 大 |
| **安全性** | 中等 | 高 | 高 |
| **维护成本** | 中 | 低 | 低 |
| **可扩展性** | 差 | 好 | 好 |
| **实现周期** | 1-2天 | 3-5天 | 5-7天 |
| **适合场景** | 紧急修复 | 正式环境 | 长期架构 |

---

## 六、问题索引（便于快速查找）

| 编号 | 问题类型 | 问题描述 | 严重程度 |
|------|----------|----------|----------|
| Bug-1 | Bug | 登录回调 URL 拼接缺少路径前缀 | 中 |
| Bug-2 | Bug | 票据验证接口无调用方身份验证 | 中 |
| SEC-1 | 安全 | 后端 API 完全无认证 | 致命 |
| SEC-2 | 安全 | 前端直接验证票据，票据暴露 | 高危 |
| SEC-3 | 安全 | 会话凭证是原始 ticket，无签名无过期 | 高危 |
| SEC-4 | 安全 | 前端路由保护可被控制台绕过 | 中 |
| SEC-5 | 安全 | 票据无过期时间 | 中 |
| SEC-6 | 安全 | 无应用白名单，开放重定向风险 | 中 |
| ARCH-1 | 架构 | 每个前端应用重复实现 SSO 逻辑 | 中 |
| ARCH-2 | 架构 | gRPC 票据验证客户端存在但未使用 | 低 |
| ARCH-3 | 架构 | 无法实现单点登出 | 中 |
| ARCH-4 | 架构 | 无统一认证 SDK | 中 |

---

## 七、已完成改造：标准 OAuth2 授权码模式

### 7.1 改造概述

采用 **Spring Security OAuth2 Authorization Server** 实现标准的 OAuth2.0 Authorization Code + PKCE 流程。

### 7.2 改造后架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           SSO 授权服务器 (8080)                           │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  Authorization Server                                               │  │
│  │  • Session-Cookie 会话 (SSO_SESSION)                                │  │
│  │  • OAuth2 端点: /oauth2/authorize, /oauth2/token, /oauth2/jwks     │  │
│  │  • 客户端注册管理 (Client Repository)                               │  │
│  │  • JWK RSA 密钥对 (JWT 签名验证)                                    │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                    │                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  登录页面 (Thymeleaf)                                               │  │
│  │  • GET /login 显示登录表单                                          │  │
│  │  • POST /login 表单提交认证                                         │  │
│  │  • 登出: GET /logout                                                │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────┬──────────────────────────┘
                                               │
                    ┌──────────────────────────┼──────────────────────────┐
                    │                          │                          │
          ┌─────────▼─────────┐      ┌────────▼─────────┐      ┌────────▼─────────┐
          │   user-web (3001)  │      │  third-party-1  │      │  third-party-2  │
          │   用户管理系统     │      │   第三方应用     │      │   第三方应用     │
          │   OAuth2 Client   │      │   OAuth2 Client │      │   OAuth2 Client │
          └─────────┬─────────┘      └──────────────────┘      └──────────────────┘
                    │
          ┌─────────▼─────────┐
          │  user-server(8081)│
          │  资源服务器       │
          │  Bearer JWT 验证  │
          └───────────────────┘
```

### 7.3 关键改进点（解决的问题）

| 原问题 | 改造后解决方案 |
|--------|----------------|
| **SEC-1 后端API裸奔** | user-server 改为 OAuth2 Resource Server，所有 API 需 Bearer Token 验证 |
| **SEC-2 前端验证票据** | 改为标准 OAuth2 流程：Code → Token，Token 由后端安全交换 |
| **SEC-3 凭证不安全** | Access Token 有签名、过期时间（2小时），Refresh Token（30天） |
| **SEC-5 票据无过期** | Token 有明确过期时间，自动刷新机制 |
| **SEC-6 开放重定向** | 客户端必须提前注册 redirect_uri，严格校验 |
| **ARCH-1 重复实现** | 基于标准 OAuth2，有成熟 SDK 可复用 |
| **ARCH-3 无法单点登出** | SSO 统一会话管理，支持全局登出 |

### 7.4 新增核心文件

| 文件 | 说明 |
|------|------|
| [AuthorizationServerConfig.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/config/AuthorizationServerConfig.java) | OAuth2 授权服务器配置 |
| [JwkConfig.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/config/JwkConfig.java) | RSA JWK 密钥配置 |
| [Client.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/entity/Client.java) | OAuth2 客户端实体 |
| [ClientRepository.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/repository/ClientRepository.java) | 客户端仓储 |
| [LoginController.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/controller/LoginController.java) | 登录页面控制器 |
| [UserInfoController.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/controller/UserInfoController.java) | OIDC 用户信息端点 |
| [OAuth2Controller.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/user-server/src/main/java/com/user/controller/OAuth2Controller.java) | 令牌交换接口 |
| [login.html](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/resources/templates/login.html) | SSO 统一登录页面 |

### 7.5 配置项说明（无硬编码）

所有环境相关配置通过环境变量或配置文件设置：

**SSO Server (application.yml):**
```yaml
sso:
  oauth2:
    issuer-uri: ${SSO_ISSUER_URI:http://localhost:8080}
```

**User Server (application.yml):**
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${SSO_ISSUER_URI:http://localhost:8080}
          jwk-set-uri: ${SSO_JWKS_URI:http://localhost:8080/oauth2/jwks}
sso:
  oauth2:
    client-id: ${SSO_CLIENT_ID:user-web-client}
    client-secret: ${SSO_CLIENT_SECRET:user-web-secret-123}
    token-uri: ${SSO_TOKEN_URI:http://localhost:8080/oauth2/token}
    authorization-uri: ${SSO_AUTH_URI:http://localhost:8080/oauth2/authorize}
    redirect-uri: ${SSO_REDIRECT_URI:http://localhost:3001/sso/callback}
```

**前端 (.env.development):**
```env
REACT_APP_OAUTH2_AUTH_URI=http://localhost:8080/oauth2/authorize
REACT_APP_OAUTH2_CLIENT_ID=user-web-client
REACT_APP_OAUTH2_REDIRECT_URI=http://localhost:3001/sso/callback
REACT_APP_OAUTH2_SCOPE=openid profile read write
```
