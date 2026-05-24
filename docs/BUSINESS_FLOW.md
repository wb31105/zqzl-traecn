# 业务流程说明

## 重要说明：已完成标准 OAuth2 授权码模式改造

本文档包含**改造前**和**改造后**两套流程说明。当前生产环境使用**标准 OAuth2 授权码模式**。

***

## 一、（改造后）OAuth2 授权码登录流程（当前使用）

### 1.1 整体架构（前后端分离 + 双协议支持）

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                            浏览器/客户端                                                  │
│  ┌──────────────────┐                          ┌──────────────────┐                                      │
│  │   user-web       │                          │    sso-web       │                                      │
│  │   (3001)         │                          │    (3000)         │  ← 纯前端登录页面（React）            │
│  │   OAuth2 Client  │                          │    SSO Portal     │                                      │
│  └────────┬─────────┘                          └────────┬─────────┘                                      │
└───────────┼─────────────────────────────────────────────┼──────────────────────────────────────────────────┘
            │                                             │
            │ 1. 未登录访问                               │ 未登录时 Spring Security 302 重定向
            ▼                                             ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│  sso-server (8080) - OAuth2 授权服务器（纯后端API）                                                      │
│                                                                                                          │
│  ┌──────────────────────────────────────────────────────────────────────────────────────────────────┐   │
│  │  OAuth2 标准 HTTP 端点（第三方应用）                                                                 │   │
│  │  • GET  /oauth2/authorize      - 授权端点（未登录→302→sso-web登录页）                              │   │
│  │  • POST /oauth2/token          - 令牌端点（第三方使用HTTP）                                          │   │
│  │  • GET  /oauth2/jwks           - 公钥端点（公开）                                                    │   │
│  │  • GET  /userinfo              - 用户信息端点                                                         │   │
│  │  • POST /logout                - 登出（销毁Session）                                                 │   │
│  └──────────────────────────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                                          │
│  ┌──────────────────────────────────────────────────────────────────────────────────────────────────┐   │
│  │  内部 gRPC 服务（微服务间调用）                                                                      │   │
│  │  • ExchangeToken     - 授权码换Token（内部服务用gRPC，高性能）                                       │   │
│  │  • ValidateToken     - Token 验证                                                                    │   │
│  │  • RevokeToken       - Token 撤销                                                                    │   │
│  │  • GetClientInfo     - 客户端信息查询                                                                 │   │
│  └──────────────────────────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                                          │
│  ┌──────────────────────────────────────────────────────────────────────────────────────────────────┐   │
│  │  客户端管理（白名单）                                                                               │   │
│  │  • GET/POST/PUT/DELETE /v1/oauth2/clients    - 客户端CRUD                                         │   │
│  │  • GET  /v1/oauth2/clients/validate-redirect  - 回调地址白名单校验                                 │   │
│  │  🔒 安全机制：redirect_uri 必须与注册完全匹配，不支持通配符，防止开放重定向攻击                       │   │
│  └──────────────────────────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                                          │
│  Session: SSO_SESSION Cookie (HttpOnly, SameSite=Lax, 30分钟超时)                                        │
└──────────────────────────────────────────────────────────┬───────────────────────────────────────────────┘
                                                           │
                                                           │ gRPC 调用
                                                           ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│  user-server (8081) - OAuth2 资源服务器                                                                 │
│                                                                                                          │
│  ┌──────────────────────────────────────────────────────────────────────────────────────────────────┐   │
│  │  Token 交换（双协议支持）                                                                            │   │
│  │  • HTTP: POST /v1/oauth2/token  → 内部调用 sso gRPC → 返回 Token                                     │   │
│  │  • gRPC: 直接调用 SsoService.ExchangeToken                                                          │   │
│  └──────────────────────────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                                          │
│  • JWT Bearer Token 验证 - 所有受保护API（通过 /oauth2/jwks 公钥验签）                                    │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

**架构设计原则**:

| 维度      | 策略  | 说明                                               |
| ------- | --- | ------------------------------------------------ |
| **前后端** | 纯分离 | sso-web (React) 纯前端，sso-server 纯后端API，无Thymeleaf |
| **协议**  | 双轨制 | 内部微服务 → gRPC（高性能、强类型）；第三方应用 → HTTP（标准、兼容）        |
| **安全**  | 白名单 | redirect\_uri 严格完全匹配，防止开放重定向攻击                   |

***

### 1.2 详细时序图（前后端交互）

```mermaid
sequenceDiagram
    participant U as 用户
    participant UW as user-web前端(3001)
    participant SS as sso-server(8080)
    participant US as user-server(8081)

    Note over U,US: ═══════════ 阶段1：触发授权请求 ═══════════
    
    U->>UW: 1. 访问 http://admin.local.bw.com:3002/users
    UW->>UW: 2. ProtectedRoute 检查 access_token
    
    alt localStorage 有有效 token
        UW->>US: 3. 携带 Bearer Token 请求 API
        US->>US: 4. JWT 签名验证（通过 JWKS）
        US-->>UW: 返回用户数据
        UW->>U: 显示页面
    else 无 token 或 token 已过期
        UW->>U: 3. 重定向到授权服务器
        Note right of UW: URL: /oauth2/authorize?<br>response_type=code<br>&client_id=user-web-client<br>&redirect_uri=http://admin.local.bw.com:3002/sso/callback<br>&scope=openid profile read write<br>&state=xyz123
    end

    Note over U,US: ═══════════ 阶段2：SSO 登录认证 ═══════════
    
    U->>SS: 4. 访问授权端点 /oauth2/authorize
    SS->>SS: 5. 检查是否有 SSO_SESSION Cookie
    
    alt 已登录（Session有效）
        SS->>SS: 6. 验证 client_id、redirect_uri
        SS->>U: 7. 生成 code，302 重定向到回调
    else 未登录（Session无效）
        SS->>U: 6. 302 重定向到 /login?continue=/oauth2/authorize?...
        U->>SS: 7. GET /login - 显示登录页面
        Note right of SS: 返回 Thymeleaf 登录表单
        U->>SS: 8. 输入用户名密码，POST /login
        SS->>US: 9. gRPC 调用 validateLogin - 验证密码
        US-->>SS: 验证通过
        SS->>SS: 10. 创建 Session，设置 SSO_SESSION Cookie
        Note right of SS: Session 时效 30 分钟
        SS->>U: 11. 302 重定向回 /oauth2/authorize?...
    end

    Note over U,US: ═══════════ 阶段3：获取授权码 ═══════════
    
    U->>SS: 12. 再次访问 /oauth2/authorize?...（已登录）
    SS->>SS: 13. 校验 OAuth2 参数
    Note right of SS: • client_id 是否存在<br>• redirect_uri 是否匹配注册<br>• scope 是否合法
    
    alt 不需要用户授权（已配置不需要consent）
        SS->>SS: 14. 生成 Authorization Code（加密存储）
        SS->>U: 15. 302 重定向到客户端回调
        Note right of U: URL: http://admin.local.bw.com:3002/sso/callback?<br>code=ac_abc123...<br>&state=xyz123
    else 需要用户授权
        SS->>U: 14. 显示授权确认页面
        U->>SS: 15. 用户点击"同意授权"
        SS->>SS: 16. 生成 Authorization Code
        SS->>U: 17. 302 重定向回调
    end

    Note over U,US: ═══════════ 阶段4：授权码换令牌 ═══════════
    
    U->>UW: 16. 访问 /sso/callback?code=ac_abc123
    UW->>UW: 17. SsoCallback 组件获取 code 参数
    UW->>US: 18. POST /v1/oauth2/token { code: "ac_abc123" }
    Note right of UW: 前端调用自己的后端，不直接接触 client_secret
    
    US->>SS: 19. POST /oauth2/token（后端间调用）
    Note right of US: Authorization: Basic base64(client_id:client_secret)<br>Form Data:<br>  grant_type=authorization_code<br>  code=ac_abc123<br>  redirect_uri=...
    
    SS->>SS: 20. 验证参数
    Note right of SS: • client_id + client_secret 认证<br>• code 有效且未过期<br>• code 对应该 client_id
    
    SS->>US: 21. 返回 Token 响应
    Note right of SS: {<br>  "access_token": "eyJ...", (JWT, 2小时)<br>  "refresh_token": "rt_...", (30天)<br>  "token_type": "Bearer",<br>  "expires_in": 7200,<br>  "scope": "openid profile read write"<br>}
    
    US->>UW: 22. 返回 { success: true, data: tokenData }
    
    UW->>UW: 23. 保存 Token 到 localStorage
    Note right of UW: • access_token<br>• refresh_token<br>• expires_in<br>• login_time
    
    UW->>U: 24. 重定向到 originalPath（如 /users）

    Note over U,US: ═══════════ 阶段5：后续 API 请求 ═══════════
    
    U->>UW: 25. 访问 /users 页面
    UW->>US: 26. GET /v1/users
    Note right of UW: Header: Authorization: Bearer eyJ...
    
    US->>US: 27. JWT 验证
    Note right of US: • 解析 JWT Header 获取 kid<br>• 请求 /oauth2/jwks 获取公钥<br>• 验证签名<br>• 检查 exp 过期时间<br>• 检查 issuer
    
    alt Token 有效
        US-->>UW: 28. 返回数据
        UW->>U: 显示页面
    else Token 已过期
        US-->>UW: 401 Unauthorized
        UW->>U: Axios 拦截器触发，重定向到 SSO 重新登录
    end
```

***

### 1.3 核心交互环节说明

#### 🔑 环节1：授权请求构造（user-web 前端）

**代码位置**: [App.js - redirectToSSO](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/src/App.js#L40-L56)

```javascript
// 构造 OAuth2 授权请求
const params = new URLSearchParams({
  response_type: 'code',           // 授权码模式
  client_id: 'user-web-client',    // 客户端ID
  redirect_uri: 'http://admin.local.bw.com:3002/sso/callback',  // 回调地址
  scope: 'openid profile read write',  // 权限范围
  state: Math.random().toString(36).substring(2, 15)  // CSRF 防护
});
window.location.href = `http://api.local.bw.com:8080/oauth2/authorize?${params}`;
```

**关键参数说明**:

| 参数                   | 作用                  | 示例值                                  |
| -------------------- | ------------------- | ------------------------------------ |
| `response_type=code` | 指定使用授权码模式           | 必填                                   |
| `client_id`          | 客户端标识，必须提前注册        | `user-web-client`                    |
| `redirect_uri`       | 授权成功后回调地址，必须与注册完全一致 | `http://admin.local.bw.com:3002/sso/callback` |
| `scope`              | 请求的权限范围，空格分隔        | `openid profile read write`          |
| `state`              | 随机值，用于 CSRF 防护      | 随机字符串                                |

***

#### 🔑 环节2：SSO 会话认证（sso-server 后端）

**代码位置**: [SecurityConfig.java - 表单登录配置](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/config/SecurityConfig.java#L32-L58)

```java
// Session-Cookie 配置
.sessionManagement()
    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
    .and()
.formLogin()
    .loginPage("/login")              // 登录页面
    .loginProcessingUrl("/login")     // 表单提交地址
    .defaultSuccessUrl("/oauth2/authorize", true)
    .permitAll()
    .and()
.logout()
    .logoutUrl("/logout")
    .logoutSuccessUrl("/login?logout")
    .invalidateHttpSession(true)
    .deleteCookies("SSO_SESSION")     // 登出删除Cookie
```

**Session Cookie 属性**（配置在 application.yml）:

| 属性       | 值             | 作用             |
| -------- | ------------- | -------------- |
| Name     | `SSO_SESSION` | Cookie 名称      |
| HttpOnly | `true`        | 禁止 JS 读取，防 XSS |
| SameSite | `Lax`         | 防 CSRF         |
| Secure   | `false` (开发)  | HTTPS 传输       |
| Max-Age  | 30分钟          | Session 超时     |

***

#### 🔑 环节3：授权码生成与回调（sso-server OAuth2 端点）

**代码位置**: [AuthorizationServerConfig.java - 授权服务器配置](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/config/AuthorizationServerConfig.java)

```java
// 客户端配置
return RegisteredClient.withId(client.getId().toString())
        .clientId(client.getClientId())
        .clientSecret(client.getClientSecret())  // BCrypt 加密存储
        .clientAuthenticationMethod(CLIENT_SECRET_BASIC)  // Basic 认证
        .authorizationGrantType(AUTHORIZATION_CODE)       // 授权码模式
        .authorizationGrantType(REFRESH_TOKEN)            // 刷新令牌
        .redirectUri(client.getRedirectUri())  // 严格匹配
        .scope(client.getScope())
        .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofHours(2))    // Access Token 2小时
                .refreshTokenTimeToLive(Duration.ofDays(30))  // Refresh Token 30天
                .build())
        .build();
```

**安全机制**:

- 授权码（Code）**一次性有效**，使用后立即失效
- 授权码默认有效期 **5分钟**
- 授权码与 client\_id 绑定，不能被其他客户端使用
- redirect\_uri **严格匹配**，不支持通配符

***

#### 🔑 环节4：授权码安全交换（双协议支持）

**协议策略**：

| 使用场景      | 协议       | 优点                |
| --------- | -------- | ----------------- |
| **内部微服务** | **gRPC** | 高性能、强类型、服务治理、链路追踪 |
| **第三方应用** | **HTTP** | 标准兼容、无需SDK、跨语言    |

***

##### 方案A：内部服务 gRPC 调用（推荐）

**代码位置**: [OAuth2GrpcServiceImpl.java - gRPC Token交换](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/grpc/OAuth2GrpcServiceImpl.java)

**Proto 定义** ([sso.proto](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/proto/sso.proto)):

```protobuf
service SsoService {
  rpc ExchangeToken(TokenExchangeRequest) returns (TokenExchangeResponse) {}
  rpc ValidateToken(TokenValidationRequest) returns (TokenValidationResponse) {}
  rpc RevokeToken(RevokeTokenRequest) returns (RevokeTokenResponse) {}
  rpc GetClientInfo(ClientInfoRequest) returns (ClientInfoResponse) {}
}

message TokenExchangeRequest {
  string code = 1;                    
  string client_id = 2;              
  string client_secret = 3;          
  string redirect_uri = 4;           
  string grant_type = 5;             
}
```

**gRPC 服务端实现**:

```java
@Override
public void exchangeToken(TokenExchangeRequest request, 
                          StreamObserver<TokenExchangeResponse> responseObserver) {
    // 1. 验证客户端存在
    Client client = clientRepository.findByClientId(request.getClientId());
    
    // 2. 验证 client_secret（BCrypt 匹配）
    if (!passwordEncoder.matches(request.getClientSecret(), client.getClientSecret())) {
        response.onError("客户端密钥错误");
        return;
    }
    
    // 3. 🔒 白名单校验：redirect_uri 必须完全匹配
    if (!client.getRedirectUri().equals(request.getRedirectUri())) {
        response.onError("回调地址与注册地址不匹配");
        return;
    }
    
    // 4. 调用内部 OAuth2 端点获取 Token
    // ...
    
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
}
```

***

##### 方案B：第三方 HTTP 调用（OAuth2 标准）

**代码位置**: [OAuth2Controller.java - HTTP Token交换](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/user-server/src/main/java/com/user/controller/OAuth2Controller.java#L31-L67)

```java
@PostMapping("/token")
public ResponseEntity<Map<String, Object>> exchangeToken(@RequestBody Map<String, String> request) {
    String code = request.get("code");
    
    // Basic Auth: client_id:client_secret
    HttpHeaders headers = new HttpHeaders();
    headers.setBasicAuth(clientId, clientSecret);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "authorization_code");
    params.add("code", code);
    params.add("redirect_uri", redirectUri);

    // 调用标准 OAuth2 /oauth2/token 端点
    HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
    ResponseEntity<Map> response = restTemplate.postForEntity(tokenUri, entity, Map.class);
    
    return ResponseEntity.ok(Map.of("success", true, "data", response.getBody()));
}
```

**为什么要经过 user-server 中转？**

1. ✅ **保护 client\_secret**：绝不能暴露给前端
2. ✅ **便于统一管理**：Token 刷新、日志、审计都在后端
3. ✅ **可扩展性**：可添加额外的业务逻辑（如创建本地用户）

***

#### 🔑 环节5：JWT 资源验证（user-server 资源服务器）

**代码位置**: [SecurityConfig.java - OAuth2 资源服务器](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/user-server/src/main/java/com/user/config/SecurityConfig.java#L32-L50)

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeRequests()
            .antMatchers("/v1/oauth2/**", "/h2-console/**").permitAll()
            .anyRequest().authenticated()  // 其他所有API需认证
            .and()
        .oauth2ResourceServer()  // 启用 OAuth2 资源服务器
            .jwt();               // 使用 JWT Token
    return http.build();
}

@Bean
public JwtDecoder jwtDecoder() {
    // 从授权服务器获取公钥验证签名
    return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
}
```

**JWT 验证流程**:

1. 请求头携带 `Authorization: Bearer <token>`
2. Spring Security 解析 JWT Header，获取 `kid`（密钥ID）
3. 调用 `GET /oauth2/jwks` 获取 RSA 公钥集合
4. 使用对应公钥验证签名（防篡改）
5. 验证 `exp`（过期时间）、`iss`（发行者）等声明

***

### 1.4 登出流程

```mermaid
sequenceDiagram
    participant U as 用户
    participant UW as user-web前端
    participant SS as sso-server

    U->>UW: 1. 点击"退出登录"
    UW->>UW: 2. 清空 localStorage (token等)
    
    Note right of UW: // 清除所有认证信息
    localStorage.clear();
    
    UW->>U: 3. 重定向到 SSO 登出端点
    Note right of U: URL: http://api.local.bw.com:8080/logout
    
    SS->>SS: 4. 销毁 Session
    Note right of SS: • session.invalidate()<br>• 删除 SSO_SESSION Cookie
    
    SS->>U: 5. 重定向到登录页面
    Note right of U: /login?logout
```

***

### 1.5 Axios 拦截器配置（前端自动处理）

**代码位置**: [App.js - Axios 拦截器](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/src/App.js#L8-L30)

```javascript
// 请求拦截器：自动添加 Bearer Token
axios.interceptors.request.use(
  (config) => {
    const accessToken = localStorage.getItem('access_token');
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
  }
);

// 响应拦截器：401 自动重定向登录
axios.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      localStorage.clear();
      redirectToSSO();  // 重新走 OAuth2 流程
    }
    return Promise.reject(error);
  }
);
```

***

## （改造前）SSO 登录流程

### 1.1 流程图

```mermaid
sequenceDiagram
    participant User as 用户
    participant SSO_Web as SSO前端(3000)
    participant SSO_Server as SSO服务(8080)
    participant User_Server as 用户服务(8081)
    participant DB as 数据库

    User->>SSO_Web: 1. 访问登录页面 /login
    SSO_Web->>SSO_Web: 2. 检查URL参数是否有 redirect
    
    User->>SSO_Web: 3. 输入用户名
    SSO_Web->>SSO_Server: 4. GET /v1/auth/check-captcha?username=xxx
    Note over SSO_Server: SSO内部检查 loginAttempts 计数
    
    alt 需要验证码(失败次数>=1)
        SSO_Server-->>SSO_Web: 返回 true
        SSO_Web->>SSO_Server: 5. GET /v1/auth/captcha
        Note over SSO_Server: 内部调用 CaptchaService.generateCaptcha()
        SSO_Server-->>SSO_Web: 返回 captchaKey + captchaImage(base64)
        SSO_Web->>User: 显示验证码输入框
    else 不需要验证码
        SSO_Server-->>SSO_Web: 返回 false
    end
    
    User->>SSO_Web: 6. 输入密码(和验证码)，点击登录
    SSO_Web->>SSO_Server: 7. POST /v1/auth/login
    Note over SSO_Web,SSO_Server: 请求体: {username, password, captcha?, captchaKey?}
    
    alt 需要验证验证码
        Note over SSO_Server: 内部调用 CaptchaService.validateCaptcha()
        alt 验证码错误
            SSO_Server-->>SSO_Web: {success: false, message: "验证码错误", requireCaptcha: true}
            SSO_Web->>SSO_Server: 重新获取验证码
            SSO_Web->>User: 显示错误，刷新验证码
        end
    end
    
    SSO_Server->>User_Server: 8. gRPC ValidateLoginRequest
    Note over SSO_Server,User_Server: UserServiceClient.validateLogin() (gRPC)
    Note over User_Server: 内部调用 UserGrpcServiceImpl.validateLogin()
    
    User_Server->>DB: 9. SELECT * FROM user WHERE username = ?
    DB-->>User_Server: 返回用户记录
    
    alt 用户不存在
        User_Server-->>SSO_Server: {success: false, message: "用户名或密码错误"}
        Note over SSO_Server: loginAttempts.merge(username, 1, +)
        SSO_Server-->>SSO_Web: 返回错误响应
        SSO_Web->>User: 显示错误信息
    else 用户已禁用(enabled=false)
        User_Server-->>SSO_Server: {success: false, message: "账号已被禁用"}
        SSO_Server-->>SSO_Web: 返回错误响应
        SSO_Web->>User: 显示错误信息
    else 密码错误
        User_Server->>DB: UPDATE user SET login_attempts = login_attempts + 1
        User_Server-->>SSO_Server: {success: false, message: "用户名或密码错误"}
        Note over SSO_Server: loginAttempts.merge(username, 1, +)
        SSO_Server-->>SSO_Web: 返回错误响应
        Note over SSO_Web: 检查返回的 requireCaptcha 决定是否显示验证码
        SSO_Web->>User: 显示错误信息
    else 登录成功
        User_Server->>DB: UPDATE user SET login_attempts=0, last_login_time=NOW()
        User_Server-->>SSO_Server: {success: true, token: JWT, username: xxx}
        
        Note over SSO_Server: 内部调用 TicketService.generateTicket()
        Note over SSO_Server: 生成 ST-UUID 存入内存 Map
        Note over SSO_Server: 注意: 返回给前端的是 ticket(ST-xxx)，不是 JWT
        
        SSO_Server-->>SSO_Web: 10. {success: true, token: ST-xxx, username: xxx}
        SSO_Web->>SSO_Web: 11. localStorage.setItem('token', ST-xxx)
        
        alt 有 redirect 参数
            SSO_Web->>User: 重定向到 redirect 回调地址
            Note over User: URL: {redirect}/sso/callback?originalPath={path}&ticket=ST-xxx&username=xxx
        else 无 redirect
            SSO_Web->>User: 显示登录成功
        end
    end
```

### 1.2 核心要点

1. **验证码机制**:
   - CaptchaService 是 sso-server 内部的 Service 类，不是独立服务
   - 验证码存储在 sso-server 内存的 ConcurrentHashMap
   - 验证成功后立即删除 key（一次性使用）
2. **登录失败计数**:
   - sso-server 和 user-server 各维护一份登录失败计数
   - sso-server: ConcurrentHashMap 内存存储
   - user-server: 数据库 user 表的 login\_attempts 字段
3. **服务间调用**:
   - sso-server 通过 UserServiceClient + gRPC 调用 user-server
   - gRPC 地址: `static://user-server:9091` (Docker) 或 `static://localhost:9091` (开发环境)
4. **票据机制**:
   - TicketService 是 sso-server 内部的 Service 类
   - 票据格式: `ST-` + UUID
   - 票据存储在 sso-server 内存的 ConcurrentHashMap
   - 票据验证后立即删除（一次性使用）

***

## 二、用户注册流程

### 2.1 流程图

```mermaid
sequenceDiagram
    participant User as 用户
    participant SSO_Web as SSO前端(3000)
    participant SSO_Server as SSO服务(8080)
    participant User_Server as 用户服务(8081)
    participant DB as 数据库

    User->>SSO_Web: 1. 点击注册，跳转 /register
    
    User->>SSO_Web: 2. 填写注册表单
    Note over SSO_Web: 字段: username, password, confirmPassword, email(可选), phone(必填), nickname(可选)
    
    SSO_Web->>SSO_Web: 3. 前端表单验证
    Note over SSO_Web: - 密码一致<br>- 用户名格式<br>- 邮箱格式<br>- 手机号格式
    
    SSO_Web->>SSO_Server: 4. POST /v1/auth/send-verification-code
    Note over SSO_Web,SSO_Server: 发送 {phone, type: 'register'}
    
    Note over SSO_Server: 内部调用 VerificationCodeService.generateAndSendCode()
    Note over SSO_Server: 生成 6位数字验证码<br>存入 ConcurrentHashMap<br>有效期5分钟
    SSO_Server-->>SSO_Web: 返回 {success: true, message: "验证码已发送"}
    Note over SSO_Server: 控制台打印验证码（模拟短信）
    
    User->>SSO_Web: 5. 输入收到的验证码
    
    SSO_Web->>SSO_Server: 6. POST /v1/auth/register
    
    Note over SSO_Server: 内部调用 VerificationCodeService.verifyCode()
    alt 验证码错误或已过期
        SSO_Server-->>SSO_Web: {success: false, message: "验证码错误或已过期"}
        SSO_Web->>User: 显示错误
    end
    
    Note over SSO_Server: 验证通过，移除验证码
    
    SSO_Server->>User_Server: 7. gRPC RegisterRequest
    Note over SSO_Server,User_Server: UserServiceClient.register() (gRPC)
    
    Note over User_Server: 内部调用 UserService.register()
    
    alt 两次密码不一致
        User_Server-->>SSO_Server: {success: false, message: "两次输入的密码不一致"}
        SSO_Server-->>SSO_Web: 返回错误
    else 用户名已存在
        User_Server-->>SSO_Server: {success: false, message: "用户名已存在"}
        SSO_Server-->>SSO_Web: 返回错误
    else 手机号已被注册
        User_Server-->>SSO_Server: {success: false, message: "手机号已被注册"}
        SSO_Server-->>SSO_Web: 返回错误
    else 邮箱已被注册
        User_Server-->>SSO_Server: {success: false, message: "邮箱已被注册"}
        SSO_Server-->>SSO_Web: 返回错误
    end
    
    User_Server->>DB: 8. INSERT INTO user (...)
    Note over User_Server,DB: - password 用 BCrypt 加密<br>- role = 'USER'<br>- enabled = true<br>- login_attempts = 0
    
    DB-->>User_Server: 返回保存的用户记录
    User_Server-->>SSO_Server: {success: true, message: "注册成功"}
    SSO_Server-->>SSO_Web: 9. 返回成功响应
    SSO_Web->>User: 显示成功页面，提供"返回登录"链接
```

### 2.2 流程特点

- 手机号为必填项，用于接收验证码
- 邮箱为可选项
- 通过手机验证码验证用户身份，防止恶意注册
- 验证码有效期5分钟

***

## 三、忘记密码流程

### 3.1 流程图

```mermaid
sequenceDiagram
    participant User as 用户
    participant SSO_Web as SSO前端(3000)
    participant SSO_Server as SSO服务(8080)
    participant User_Server as 用户服务(8081)
    participant DB as 数据库

    User->>SSO_Web: 1. 点击"忘记密码"，跳转 /forgot-password
    SSO_Web->>SSO_Server: 2. GET /v1/auth/captcha
    Note over SSO_Server: 内部调用 CaptchaService.generateCaptcha()
    SSO_Server-->>SSO_Web: 返回 {captchaKey, captchaImage}
    
    User->>SSO_Web: 3. 第一步：验证身份
    Note over SSO_Web: 字段: identifier(用户名/手机号/邮箱), captcha
    
    SSO_Web->>SSO_Server: 4. POST /v1/auth/forgot-password/verify
    
    Note over SSO_Server: 内部调用 CaptchaService.validateCaptcha()
    alt 图形验证码错误
        SSO_Server-->>SSO_Web: {success: false, message: "图形验证码错误"}
        SSO_Web->>SSO_Server: 刷新验证码
        SSO_Web->>User: 显示错误
    end
    
    Note over SSO_Server: 图形验证码验证通过，移除 captchaKey
    
    SSO_Server->>User_Server: 5. gRPC FindUserRequest
    Note over SSO_Server,User_Server: UserServiceClient.findUserByIdentifier()
    
    Note over User_Server: 内部调用 UserService.findUserByIdentifier()
    Note over User_Server: 根据identifier自动判断类型<br>含@视为邮箱<br>1开头11位视为手机号<br>否则视为用户名
    
    User_Server->>DB: 6. SELECT * FROM user WHERE username=? OR phone=? OR email=?
    DB-->>User_Server: 返回用户记录
    
    alt 用户不存在
        User_Server-->>SSO_Server: {success: false, message: "用户不存在"}
        SSO_Server-->>SSO_Web: 返回错误
    end
    
    Note over SSO_Server: 生成 verifyToken (UUID)<br>存入 ConcurrentHashMap<br>有效期15分钟<br>关联用户的username/email/phone
    
    SSO_Server-->>SSO_Web: 7. 返回 {success: true, verifyToken, email, phone}
    
    User->>SSO_Web: 8. 第二步：重置密码
    Note over SSO_Web: 字段: newPassword, confirmPassword, verificationCode
    
    SSO_Web->>SSO_Server: 9. POST /v1/auth/send-verification-code
    Note over SSO_Web,SSO_Server: 根据第一步找到的用户手机号发送验证码
    
    Note over SSO_Server: 发送手机验证码（控制台打印）
    
    User->>SSO_Web: 10. 输入手机验证码
    
    SSO_Web->>SSO_Server: 11. POST /v1/auth/forgot-password
    Note over SSO_Web,SSO_Server: {newPassword, confirmPassword, verificationCode, verifyToken}
    
    Note over SSO_Server: 验证 verifyToken 是否有效
    alt verifyToken 无效或已过期
        SSO_Server-->>SSO_Web: {success: false, message: "验证已过期，请重新验证"}
    end
    
    Note over SSO_Server: 验证手机验证码
    alt 手机验证码错误或已过期
        SSO_Server-->>SSO_Web: {success: false, message: "验证码错误或已过期"}
    end
    
    SSO_Server->>User_Server: 12. gRPC ForgotPasswordRequest
    
    Note over User_Server: 内部调用 UserService.forgotPassword()
    
    alt 两次密码不一致
        User_Server-->>SSO_Server: {success: false, message: "两次输入的密码不一致"}
        SSO_Server-->>SSO_Web: 返回错误
    end
    
    User_Server->>DB: 13. UPDATE user SET password=?, login_attempts=0
    Note over User_Server,DB: 新密码使用 BCrypt 加密
    
    DB-->>User_Server: 更新成功
    User_Server-->>SSO_Server: {success: true, message: "密码重置成功"}
    SSO_Server-->>SSO_Web: 14. 返回成功响应
    SSO_Web->>User: 显示成功页面，提供"返回登录"链接
```

### 3.2 流程特点

- 两步式验证，安全性更高
- 第一步只需要输入用户名/手机号/邮箱（三选一）+ 图形验证码
- 后端自动识别用户输入的identifier类型
- 第二步通过手机验证码确保是用户本人操作
- verifyToken 有效期15分钟
- 必须输入两次密码确认，防止输入错误

***

## 四、用户管理流程

### 4.1 用户查询列表流程

```mermaid
sequenceDiagram
    participant Admin as 管理员
    participant User_Web as 用户管理前端(3031)
    participant User_Server as 用户服务(8081)
    participant DB as 数据库

    Admin->>User_Web: 1. 进入用户管理页面
    User_Web->>User_Server: 2. GET /v1/users?page=0&size=10&keyword=xxx
    Note over User_Web,User_Server: 参数: page(页码), size(每页条数), keyword(搜索关键词，可选)
    
    Note over User_Server: 内部调用 UserService.getAllUsers()
    
    alt 有 keyword
        User_Server->>DB: SELECT * FROM user WHERE username LIKE ? OR email LIKE ? OR nickname LIKE ? LIMIT ?, ?
        DB-->>User_Server: 返回匹配的用户列表
    else 无 keyword
        User_Server->>DB: SELECT * FROM user LIMIT ?, ?
        DB-->>User_Server: 返回用户列表分页
    end
    
    User_Server-->>User_Web: 3. 返回 Page<UserResponse>
    Note over User_Server,User_Web: 包含: content(用户列表), totalPages, totalElements, number, size
    
    User_Web->>Admin: 4. 渲染用户列表表格
    Note over Admin: 显示字段: ID, 用户名, 昵称, 邮箱, 手机号, 角色, 状态, 最后登录, 创建时间
    
    Admin->>User_Web: 5. 操作: 搜索/翻页/调整每页条数
    User_Web->>User_Server: 重新发起 GET /v1/users 请求
    User_Server-->>User_Web: 返回对应数据
```

### 4.2 编辑用户信息流程

```mermaid
sequenceDiagram
    participant Admin as 管理员
    participant User_Web as 用户管理前端(3031)
    participant User_Server as 用户服务(8081)
    participant DB as 数据库

    Admin->>User_Web: 1. 点击某用户的"编辑"按钮
    User_Web->>User_Web: 2. 弹出模态框，填充当前用户信息
    
    Admin->>User_Web: 3. 修改字段: email, phone, nickname, role
    User_Web->>User_Server: 4. PUT /v1/users/{id}
    
    Note over User_Server: 内部调用 UserService.updateUser()
    
    User_Server->>DB: 5. SELECT * FROM user WHERE id = ?
    DB-->>User_Server: 返回用户记录
    
    alt 用户不存在
        User_Server-->>User_Web: {success: false, message: "用户不存在"}
        User_Web->>Admin: 显示错误提示
    end
    
    alt 修改了 email
        User_Server->>DB: 6. SELECT * FROM user WHERE email = ?
        alt 邮箱已被其他用户使用
            User_Server-->>User_Web: {success: false, message: "邮箱已被其他用户使用"}
            User_Web->>Admin: 显示错误提示
        end
    end
    
    User_Server->>DB: 7. UPDATE user SET email=?, phone=?, nickname=?, role=? WHERE id=?
    DB-->>User_Server: 返回更新后的用户
    User_Server-->>User_Web: 8. {success: true, message: "更新成功", data: UserResponse}
    User_Web->>Admin: 显示成功提示，关闭弹窗，刷新用户列表
```

### 4.3 启用/禁用用户流程

```mermaid
sequenceDiagram
    participant Admin as 管理员
    participant User_Web as 用户管理前端(3031)
    participant User_Server as 用户服务(8081)
    participant DB as 数据库

    Admin->>User_Web: 1. 点击"启用"或"禁用"按钮
    User_Web->>Admin: 2. 弹出确认对话框
    Admin->>User_Web: 3. 确认操作
    
    User_Web->>User_Server: 4. PUT /v1/users/{id}/toggle-status
    
    Note over User_Server: 内部调用 UserService.toggleUserStatus()
    
    User_Server->>DB: 5. SELECT * FROM user WHERE id = ?
    DB-->>User_Server: 返回用户记录
    
    alt 用户不存在
        User_Server-->>User_Web: {success: false, message: "用户不存在"}
        User_Web->>Admin: 显示错误
    end
    
    User_Server->>DB: 6. UPDATE user SET enabled = NOT enabled WHERE id = ?
    DB-->>User_Server: 更新成功
    
    User_Server-->>User_Web: 7. {success: true, message: "用户已启用/禁用"}
    User_Web->>Admin: 显示成功提示，刷新用户列表
```

### 4.4 管理员重置密码流程

```mermaid
sequenceDiagram
    participant Admin as 管理员
    participant User_Web as 用户管理前端(3031)
    participant User_Server as 用户服务(8081)
    participant DB as 数据库

    Admin->>User_Web: 1. 点击"重置密码"按钮
    User_Web->>User_Web: 2. 弹出重置密码模态框
    
    Admin->>User_Web: 3. 输入: newPassword, confirmPassword
    User_Web->>User_Web: 4. 前端验证
    Note over User_Web: - 两次密码一致<br>- 密码长度 >= 6
    
    User_Web->>User_Server: 5. PUT /v1/users/{id}/reset-password
    Note over User_Web,User_Server: 请求体: {newPassword}
    
    Note over User_Server: 内部调用 UserService.resetPassword()
    
    User_Server->>DB: 6. SELECT * FROM user WHERE id = ?
    DB-->>User_Server: 返回用户记录
    
    alt 用户不存在
        User_Server-->>User_Web: {success: false, message: "用户不存在"}
        User_Web->>Admin: 显示错误
    end
    
    User_Server->>DB: 7. UPDATE user SET password = ?, login_attempts = 0 WHERE id = ?
    Note over User_Server,DB: password 用 BCrypt 加密
    
    DB-->>User_Server: 更新成功
    User_Server-->>User_Web: 8. {success: true, message: "密码重置成功"}
    User_Web->>Admin: 显示成功提示，关闭弹窗
```

### 4.5 删除用户流程

```mermaid
sequenceDiagram
    participant Admin as 管理员
    participant User_Web as 用户管理前端(3031)
    participant User_Server as 用户服务(8081)
    participant DB as 数据库

    Admin->>User_Web: 1. 点击"删除"按钮
    User_Web->>Admin: 2. 弹出确认对话框(警告: 此操作不可恢复!)
    Admin->>User_Web: 3. 确认删除
    
    User_Web->>User_Server: 4. DELETE /v1/users/{id}
    
    Note over User_Server: 内部调用 UserService.deleteUser()
    
    User_Server->>DB: 5. SELECT * FROM user WHERE id = ?
    DB-->>User_Server: 返回用户记录
    
    alt 用户不存在
        User_Server-->>User_Web: {success: false, message: "用户不存在"}
        User_Web->>Admin: 显示错误
    end
    
    User_Server->>DB: 6. DELETE FROM user WHERE id = ?
    DB-->>User_Server: 删除成功
    
    User_Server-->>User_Web: 7. {success: true, message: "删除成功"}
    User_Web->>Admin: 显示成功提示，刷新用户列表
```

***

## 五、SSO 票据验证流程

```mermaid
sequenceDiagram
    participant User as 用户
    participant App_Web as 业务应用前端(user-web)
    participant SSO_Web as SSO前端(3000)
    participant SSO_Server as SSO服务(8080)

    User->>App_Web: 1. 未登录状态访问业务应用 /user
    App_Web->>App_Web: 2. ProtectedRoute 检查 localStorage 是否有 sso_token
    alt 没有 sso_token
        App_Web->>User: 3. 重定向到 SSO 登录页
        Note over User: URL: http://sso.local.bw.com:3001?redirect=http://admin.local.bw.com:3002
    end
    
    User->>SSO_Web: 4. 完成 SSO 登录流程(见登录流程图)
    SSO_Web->>User: 5. 登录成功，重定向到应用回调地址
    Note over User: URL: http://admin.local.bw.com:3002/sso/callback?originalPath=/users&ticket=ST-xxx&username=xxx
    
    User->>App_Web: 6. 访问应用回调地址 /user/sso/callback
    App_Web->>App_Web: 7. SsoCallback 组件获取 URL 参数(ticket, username, originalPath)
    
    App_Web->>SSO_Server: 8. GET /v1/auth/validate-ticket?ticket=ST-xxx
    Note over App_Web,SSO_Server: 前端直接调用 REST API 验证票据
    
    Note over SSO_Server: 内部调用 TicketService.validateTicket()
    Note over SSO_Server: 从 ConcurrentHashMap 查找 ticket 对应的 username
    
    alt 票据有效
        Note over SSO_Server: TicketService.removeTicket() 从 Map 删除
        SSO_Server-->>App_Web: 9. {success: true, username: xxx, message: "验证成功"}
        App_Web->>App_Web: 10. 保存到 localStorage
        Note over App_Web: localStorage.setItem('sso_token', ticket)<br>localStorage.setItem('username', username)
        App_Web->>User: 11. 跳转到 originalPath(如 /users)
    else 票据无效/已使用
        SSO_Server-->>App_Web: {success: false, message: "无效的票据"}
        App_Web->>User: 显示错误，2秒后跳转到 SSO 重新登录
    end
```

### 5.1 关键实现说明

1. **票据验证是前端直接调用 REST API**
   - 业务应用前端 (user-web) 的 SsoCallback 组件直接调用 `/v1/auth/validate-ticket`
   - 不是后端调用，也不是 gRPC 调用
2. **没有创建应用本地会话**
   - 验证成功后只是将 ticket 保存到 localStorage 的 'sso\_token'
   - 没有生成新的 JWT 或 Session
   - 也没有调用 user-server 获取用户详情
3. **会话检查是纯前端实现**
   - ProtectedRoute 组件通过检查 localStorage.getItem('sso\_token') 判断是否登录
   - 没有与后端进行会话验证
4. **每个前端应用都需要自己的 callback 路由**
   - user-web 有 `/sso/callback` 路由
   - 新的前端应用也需要实现类似的 SsoCallback 组件

***

## 六、实体与存储关系

```mermaid
erDiagram
    USER {
        Long id PK "主键"
        String username UK "用户名(唯一)"
        String password "BCrypt加密密码"
        String email UK "邮箱(唯一，可为空)"
        String phone "手机号(可为空)"
        String nickname "昵称"
        String avatar "头像URL(可为空)"
        String role "角色: ADMIN / USER"
        Boolean enabled "是否启用: true / false"
        Integer login_attempts "登录失败次数"
        LocalDateTime last_login_time "最后登录时间"
        LocalDateTime created_at "创建时间"
        LocalDateTime updated_at "更新时间"
    }
    
    note left of USER: 数据库持久化存储<br>user-server 维护
    
    CAPTCHA_SSO {
        String key PK "UUID"
        String code "4位验证码"
    }
    
    note right of CAPTCHA_SSO: ConcurrentHashMap 内存存储<br>sso-server 内部 CaptchaService 维护
    
    TICKET_SSO {
        String ticket PK "ST-UUID"
        String username "关联用户名"
    }
    
    note right of TICKET_SSO: ConcurrentHashMap 内存存储<br>sso-server 内部 TicketService 维护
    
    LOGIN_ATTEMPTS_SSO {
        String username PK
        Integer count "失败次数"
    }
    
    note right of LOGIN_ATTEMPTS_SSO: ConcurrentHashMap 内存存储<br>sso-server 内部 AuthService 维护
```

***

## 七、服务间调用关系

```mermaid
graph TD
    SSO_Web[SSO前端 3000] -->|HTTP/AJAX| SSO_Server[SSO服务 8080]
    User_Web[用户管理前端 3031] -->|HTTP/AJAX| User_Server[用户服务 8081]
    
    SSO_Server -->|gRPC| User_Server
    
    SSO_Server -->|内存 Map| CAPTCHA[图形验证码存储]
    SSO_Server -->|内存 Map| VERIFICATION_CODE[手机验证码存储]
    SSO_Server -->|内存 Map| VERIFY_TOKEN[重置密码验证Token]
    SSO_Server -->|内存 Map| TICKET[票据存储]
    SSO_Server -->|内存 Map| ATTEMPTS[登录失败计数]
    
    User_Server -->|JDBC/JPA| DB[(数据库)]
    
    style SSO_Web fill:#90EE90
    style User_Web fill:#90EE90
    style SSO_Server fill:#87CEEB
    style User_Server fill:#87CEEB
    style DB fill:#FFB6C1
```

