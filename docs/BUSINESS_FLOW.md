# 业务流程说明

## 一、SSO 登录流程

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

---

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

---

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

---

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
        Note over User: URL: http://localhost:3000?redirect=http://localhost/user
    end
    
    User->>SSO_Web: 4. 完成 SSO 登录流程(见登录流程图)
    SSO_Web->>User: 5. 登录成功，重定向到应用回调地址
    Note over User: URL: http://localhost/user/sso/callback?originalPath=/users&ticket=ST-xxx&username=xxx
    
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
   - 验证成功后只是将 ticket 保存到 localStorage 的 'sso_token'
   - 没有生成新的 JWT 或 Session
   - 也没有调用 user-server 获取用户详情

3. **会话检查是纯前端实现**
   - ProtectedRoute 组件通过检查 localStorage.getItem('sso_token') 判断是否登录
   - 没有与后端进行会话验证

4. **每个前端应用都需要自己的 callback 路由**
   - user-web 有 `/sso/callback` 路由
   - 新的前端应用也需要实现类似的 SsoCallback 组件

---

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

---

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
