# SSO 单点登录系统 - 真实业务流程文档

## 系统架构概述（v4.0 - APISIX 网关版）

本系统采用前后端分离、轻量化微服务架构，使用 APISIX 网关 + gRPC 服务间通信：

### 前端应用
- **SSO 认证前端 (sso-web)**: 端口 3000，React 应用，提供登录、注册、忘记密码页面
- **用户管理前端 (user-web)**: 端口 3031，React 应用，提供用户管理功能

### 后端服务
- **SSO 认证服务 (sso-server)**: HTTP 端口 8080，gRPC 端口 9090，Spring Boot 应用
  - 内部组件: AuthService, CaptchaService, TicketService, UserServiceClient (gRPC)
  - 负责: 登录认证、注册转发、密码重置转发、验证码生成、票据管理
  
- **用户服务 (user-server)**: HTTP 端口 8081，gRPC 端口 9091，Spring Boot 应用
  - 内部组件: UserService, CaptchaService, JwtUtil
  - 负责: 用户数据管理、密码验证、用户CRUD操作

### 基础设施
- **APISIX 网关**: 端口 80，路由转发、HTTP 代理
- **etcd**: 端口 2379，APISIX 配置存储

### 数据存储
- **数据库 (DB)**: 用户数据持久化存储（H2 内存数据库）
- **内存存储**: ConcurrentHashMap 用于验证码、票据、登录失败次数（服务内部）

---

## 1. SSO 登录流程（真实数据流）

### 流程图 (Mermaid)

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
    SSO_Web->>SSO_Server: 4. GET /sso/api/auth/check-captcha?username=xxx
    Note over SSO_Server: SSO内部检查 loginAttempts 计数
    
    alt 需要验证码(失败次数>=1)
        SSO_Server-->>SSO_Web: 返回 true
        SSO_Web->>SSO_Server: 5. GET /sso/api/auth/captcha
        Note over SSO_Server: 内部调用 CaptchaService.generateCaptcha()
        SSO_Server-->>SSO_Web: 返回 captchaKey + captchaImage(base64)
        SSO_Web->>User: 显示验证码输入框
    else 不需要验证码
        SSO_Server-->>SSO_Web: 返回 false
    end
    
    User->>SSO_Web: 6. 输入密码(和验证码)，点击登录
    SSO_Web->>SSO_Server: 7. POST /sso/api/auth/login
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
    Note over User_Server: 内部调用 UserService.login()
    
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
        User_Server-->>SSO_Server: {success: false, message: "用户名或密码错误", requireCaptcha: ?}
        Note over SSO_Server: loginAttempts.merge(username, 1, +)
        alt 失败次数达到阈值
            SSO_Server-->>SSO_Web: 返回 requireCaptcha: true
            SSO_Web->>SSO_Server: GET /sso/api/auth/captcha
            SSO_Web->>User: 显示验证码输入框
        end
        SSO_Server-->>SSO_Web: 返回错误响应
        SSO_Web->>User: 显示错误信息
    else 登录成功
        User_Server->>DB: UPDATE user SET login_attempts=0, last_login_time=NOW()
        User_Server-->>SSO_Server: {success: true, token: JWT, username: xxx}
        
        Note over SSO_Server: 内部调用 TicketService.generateTicket()
        Note over SSO_Server: 生成 ST-UUID 存入内存 Map
        
        SSO_Server-->>SSO_Web: 10. {success: true, token: ST-xxx, username: xxx}
        SSO_Web->>SSO_Web: 11. localStorage.setItem('token', ST-xxx)
        
        alt 有 redirect 参数
            SSO_Web->>User: 重定向到 redirect 回调地址
            Note over User: URL: {redirect}/sso/callback?ticket=ST-xxx&username=xxx
        else 无 redirect
            SSO_Web->>User: 显示登录成功
        end
    end
```

### 真实核心要点

1. **验证码机制**:
   - CaptchaService 是 sso-server 内部的 Service 类，不是独立服务
   - 验证码存储在 sso-server 内存的 ConcurrentHashMap
   - 验证成功后立即删除 key（一次性使用）

2. **登录失败计数**:
   - sso-server 和 user-server 各维护一份登录失败计数
   - sso-server: ConcurrentHashMap 内存存储
   - user-server: 数据库 user 表的 login_attempts 字段

3. **服务间调用**:
   - sso-server 通过 UserServiceClient + gRPC 调用 user-server
   - gRPC 地址: `static://user-server:9091` (Docker) 或 `static://localhost:9091` (开发环境)

4. **票据机制**:
   - TicketService 是 sso-server 内部的 Service 类
   - 票据格式: `ST-` + UUID
   - 票据存储在 sso-server 内存的 ConcurrentHashMap
   - 票据验证后立即删除（一次性使用）

---

## 2. 用户注册流程（真实数据流）

### 流程图 (Mermaid)

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
    
    SSO_Web->>SSO_Server: 4. POST /sso/api/auth/send-verification-code
    Note over SSO_Web,SSO_Server: 发送 {phone, type: 'register'}
    
    Note over SSO_Server: 内部调用 VerificationCodeService.generateAndSendCode()
    Note over SSO_Server: 生成 6位数字验证码<br>存入 ConcurrentHashMap<br>有效期5分钟
    SSO_Server-->>SSO_Web: 返回 {success: true, message: "验证码已发送"}
    Note over SSO_Server: 控制台打印验证码（模拟短信）
    
    User->>SSO_Web: 5. 输入收到的验证码
    
    SSO_Web->>SSO_Server: 6. POST /sso/api/auth/register
    
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

### 真实接口说明

| 步骤 | 接口 | 方法 | 所属服务 |
|------|------|------|----------|
| 发送手机验证码 | `/sso/api/auth/send-verification-code` | POST | sso-server |
| 提交注册 | `/sso/api/auth/register` | POST | sso-server |
| 内部调用注册 | `/user/api/auth/register` | POST | user-server |

### 流程特点

- ✅ 手机号为必填项，用于接收验证码
- ✅ 邮箱为可选项
- ✅ 通过手机验证码验证用户身份，防止恶意注册
- ✅ 验证码有效期5分钟

---

## 3. 忘记密码流程（真实数据流）

### 流程图 (Mermaid)

```mermaid
sequenceDiagram
    participant User as 用户
    participant SSO_Web as SSO前端(3000)
    participant SSO_Server as SSO服务(8080)
    participant User_Server as 用户服务(8081)
    participant DB as 数据库

    User->>SSO_Web: 1. 点击"忘记密码"，跳转 /forgot-password
    SSO_Web->>SSO_Server: 2. GET /sso/api/auth/captcha
    Note over SSO_Server: 内部调用 CaptchaService.generateCaptcha()
    SSO_Server-->>SSO_Web: 返回 {captchaKey, captchaImage}
    
    User->>SSO_Web: 3. 第一步：验证身份
    Note over SSO_Web: 字段: identifier(用户名/手机号/邮箱), captcha
    
    SSO_Web->>SSO_Server: 4. POST /sso/api/auth/forgot-password/verify
    
    Note over SSO_Server: 内部调用 CaptchaService.validateCaptcha()
    alt 图形验证码错误
        SSO_Server-->>SSO_Web: {success: false, message: "图形验证码错误"}
        SSO_Web->>SSO_Server: 刷新验证码
        SSO_Web->>User: 显示错误
    end
    
    Note over SSO_Server: 图形验证码验证通过，移除 captchaKey
    
    SSO_Server->>User_Server: 5. GET /user/api/auth/find-user?identifier=xxx
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
    
    SSO_Web->>SSO_Server: 9. POST /sso/api/auth/send-verification-code
    Note over SSO_Web,SSO_Server: 根据第一步找到的用户手机号发送验证码
    
    Note over SSO_Server: 发送手机验证码（控制台打印）
    
    User->>SSO_Web: 10. 输入手机验证码
    
    SSO_Web->>SSO_Server: 11. POST /sso/api/auth/forgot-password
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

### 真实接口说明

| 步骤 | 接口 | 方法 | 所属服务 |
|------|------|------|----------|
| 获取图形验证码 | `/sso/api/auth/captcha` | GET | sso-server |
| 第一步：验证身份 | `/sso/api/auth/forgot-password/verify` | POST | sso-server |
| 查找用户（内部） | `/user/api/auth/find-user` | GET | user-server |
| 发送手机验证码 | `/sso/api/auth/send-verification-code` | POST | sso-server |
| 第二步：提交重置 | `/sso/api/auth/forgot-password` | POST | sso-server |
| 内部调用重置密码 | `/user/api/auth/forgot-password` | POST | user-server |

### 流程特点

- ✅ 两步式验证，安全性更高
- ✅ 第一步只需要输入用户名/手机号/邮箱（三选一）+ 图形验证码
- ✅ 后端自动识别用户输入的identifier类型
- ✅ 第二步通过手机验证码确保是用户本人操作
- ✅ verifyToken 有效期15分钟
- ✅ 必须输入两次密码确认，防止输入错误

---

## 4. 用户管理流程（真实数据流）

### 4.1 用户查询列表流程

```mermaid
sequenceDiagram
    participant Admin as 管理员
    participant User_Web as 用户管理前端(3001)
    participant User_Server as 用户服务(8081)
    participant DB as 数据库

    Admin->>User_Web: 1. 进入用户管理页面
    User_Web->>User_Server: 2. GET /user/api/users?page=0&size=10&keyword=xxx
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
    User_Web->>User_Server: 重新发起 GET /user/api/users 请求
    User_Server-->>User_Web: 返回对应数据
```

### 4.2 编辑用户信息流程

```mermaid
sequenceDiagram
    participant Admin as 管理员
    participant User_Web as 用户管理前端(3001)
    participant User_Server as 用户服务(8081)
    participant DB as 数据库

    Admin->>User_Web: 1. 点击某用户的"编辑"按钮
    User_Web->>User_Web: 2. 弹出模态框，填充当前用户信息
    
    Admin->>User_Web: 3. 修改字段: email, phone, nickname, role
    User_Web->>User_Server: 4. PUT /user/api/users/{id}
    
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
    participant User_Web as 用户管理前端(3001)
    participant User_Server as 用户服务(8081)
    participant DB as 数据库

    Admin->>User_Web: 1. 点击"启用"或"禁用"按钮
    User_Web->>Admin: 2. 弹出确认对话框
    Admin->>User_Web: 3. 确认操作
    
    User_Web->>User_Server: 4. PUT /user/api/users/{id}/toggle-status
    
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
    participant User_Web as 用户管理前端(3001)
    participant User_Server as 用户服务(8081)
    participant DB as 数据库

    Admin->>User_Web: 1. 点击"重置密码"按钮
    User_Web->>User_Web: 2. 弹出重置密码模态框
    
    Admin->>User_Web: 3. 输入: newPassword, confirmPassword
    User_Web->>User_Web: 4. 前端验证
    Note over User_Web: - 两次密码一致<br>- 密码长度 >= 6
    
    User_Web->>User_Server: 5. PUT /user/api/users/{id}/reset-password
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
    participant User_Web as 用户管理前端(3001)
    participant User_Server as 用户服务(8081)
    participant DB as 数据库

    Admin->>User_Web: 1. 点击"删除"按钮
    User_Web->>Admin: 2. 弹出确认对话框(警告: 此操作不可恢复!)
    Admin->>User_Web: 3. 确认删除
    
    User_Web->>User_Server: 4. DELETE /user/api/users/{id}
    
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

---

## 5. SSO 票据验证流程（真实数据流）

```mermaid
sequenceDiagram
    participant User as 用户
    participant App as 业务应用
    participant SSO_Server as SSO服务(8080)
    participant User_Server as 用户服务(8081)

    User->>App: 1. 未登录状态访问业务应用
    App->>User: 2. 重定向到 SSO 登录页
    Note over User: URL: http://localhost:3000?redirect=http://app/callback
    
    User->>SSO_Server: 3. 完成 SSO 登录流程(见登录流程图)
    SSO_Server-->>User: 4. 登录成功，重定向到应用回调地址
    Note over User: URL: http://app/callback?ticket=ST-xxx&username=xxx
    
    User->>App: 5. 访问应用回调地址
    App->>SSO_Server: 6. GET /sso/api/auth/validate-ticket?ticket=ST-xxx
    
    Note over SSO_Server: 内部调用 TicketService.validateTicket()
    Note over SSO_Server: 从 ConcurrentHashMap 查找 ticket 对应的 username
    
    alt 票据有效
        Note over SSO_Server: TicketService.removeTicket() 从 Map 删除
        SSO_Server-->>App: 7. {success: true, username: xxx, message: "验证成功"}
        App->>User_Server: 8. 可选: GET /user/api/users/username/{username} 获取用户详情
        User_Server-->>App: 返回用户信息
        App->>App: 9. 创建应用本地会话(Session/JWT)
        App->>User: 10. 进入应用首页
    else 票据无效/已使用
        SSO_Server-->>App: {success: false, message: "无效的票据"}
        App->>User: 重定向到 SSO 重新登录
    end
```

---

## 6. 系统真实实体与存储关系

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

---

## 7. 真实接口清单汇总

### SSO 服务 (8080) 接口

| 接口路径 | 方法 | 功能 | 内部调用 |
|---------|------|------|----------|
| `/sso/api/auth/login` | POST | 登录验证 | UserServiceClient.validateLogin() |
| `/sso/api/auth/register` | POST | 用户注册 | UserServiceClient.register() |
| `/sso/api/auth/forgot-password` | POST | 忘记密码 | UserServiceClient.forgotPassword() |
| `/sso/api/auth/captcha` | GET | 获取验证码 | CaptchaService.generateCaptcha() |
| `/sso/api/auth/check-captcha` | GET | 检查是否需要验证码 | 检查 loginAttempts Map |
| `/sso/api/auth/validate-ticket` | GET | 验证票据 | TicketService.validateTicket() |

### 用户服务 (8081) 接口

| 接口路径 | 方法 | 功能 | 内部调用 |
|---------|------|------|----------|
| `/user/api/auth/login` | POST | 登录验证(SSO调用) | UserService.login() |
| `/user/api/auth/register` | POST | 用户注册(SSO调用) | UserService.register() |
| `/user/api/auth/forgot-password` | POST | 忘记密码(SSO调用) | UserService.forgotPassword() |
| `/user/api/auth/captcha` | GET | 获取验证码 | CaptchaService.generateCaptcha() |
| `/user/api/auth/check-captcha` | GET | 检查是否需要验证码 | UserService.shouldShowCaptcha() |
| `/user/api/users` | GET | 分页查询用户列表 | UserService.getAllUsers() |
| `/user/api/users/{id}` | GET | 根据ID查询用户 | UserService.getUserById() |
| `/user/api/users/username/{username}` | GET | 根据用户名查询 | UserService.getUserByUsername() |
| `/user/api/users/{id}` | PUT | 更新用户信息 | UserService.updateUser() |
| `/user/api/users/{id}/reset-password` | PUT | 重置用户密码 | UserService.resetPassword() |
| `/user/api/users/{id}/toggle-status` | PUT | 启用/禁用用户 | UserService.toggleUserStatus() |
| `/user/api/users/{id}` | DELETE | 删除用户 | UserService.deleteUser() |

---

## 8. 真实服务间调用关系

```mermaid
graph TD
    SSO_Web[SSO前端 3000] -->|HTTP/AJAX| SSO_Server[SSO服务 8080]
    User_Web[用户管理前端 3001] -->|HTTP/AJAX| User_Server[用户服务 8081]
    
    SSO_Server -->|RestTemplate HTTP| User_Server
    
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

---

## 总结（v4.0 - APISIX 网关版）

本系统真实实现了一个完整的 SSO 单点登录系统，包含：

1. ✅ **两个前端应用**: SSO认证前端(3000) + 用户管理前端(3031)
2. ✅ **两个后端服务**: sso-server(8080) + user-server(8081)
3. ✅ **APISIX 网关**: 统一入口，路由转发，HTTP 代理
4. ✅ **服务间通信**: sso-server 和 user-server 通过 gRPC 高性能通信
5. ✅ **内存存储**: sso-server 用 ConcurrentHashMap 存验证码、票据、登录失败计数
6. ✅ **数据库存储**: user-server 持久化用户数据
7. ✅ **无注册中心**: 通过 Docker 网络 + 静态地址通信
8. ✅ **无独立中间服务**: CaptchaService、TicketService 都是服务内部类，不是独立服务
