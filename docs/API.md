# API 接口文档

## 一、SSO 单点登录接口

**基础路径**：`/v1/auth`

### 1.1 用户登录

**接口说明**：用户登录验证，成功后返回登录票据

| 项 | 说明 |
|----|------|
| **方法** | POST |
| **路径** | `/v1/auth/login` |
| **Content-Type** | application/json |

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | String | 是 | 用户名 |
| password | String | 是 | 密码 |
| captcha | String | 否 | 图形验证码（失败1次后需要） |
| captchaKey | String | 否 | 图形验证码Key（失败1次后需要） |

**请求示例**：
```json
{
  "username": "admin",
  "password": "admin123",
  "captcha": "A1B2",
  "captchaKey": "uuid-key"
}
```

**响应参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 是否成功 |
| message | String | 消息 |
| token | String | 登录票据（ST-xxx） |
| requireCaptcha | boolean | 是否需要验证码 |
| username | String | 用户名 |

**响应示例**：
```json
{
  "success": true,
  "message": "登录成功",
  "token": "ST-550e8400-e29b-41d4-a716-446655440000",
  "requireCaptcha": false,
  "username": "admin"
}
```

---

### 1.2 用户注册

**接口说明**：新用户注册

| 项 | 说明 |
|----|------|
| **方法** | POST |
| **路径** | `/v1/auth/register` |
| **Content-Type** | application/json |

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | String | 是 | 用户名（3-50位，字母数字下划线） |
| password | String | 是 | 密码（6-100位） |
| confirmPassword | String | 是 | 确认密码 |
| phone | String | 是 | 手机号（11位） |
| email | String | 否 | 邮箱 |
| verificationCode | String | 是 | 手机验证码 |

**请求示例**：
```json
{
  "username": "testuser",
  "password": "123456",
  "confirmPassword": "123456",
  "phone": "13800138000",
  "email": "test@example.com",
  "verificationCode": "123456"
}
```

**响应参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 是否成功 |
| message | String | 消息 |
| userId | String | 用户ID |

---

### 1.3 忘记密码-第一步（验证身份）

**接口说明**：验证用户身份，获取验证令牌

| 项 | 说明 |
|----|------|
| **方法** | POST |
| **路径** | `/v1/auth/forgot-password/verify` |
| **Content-Type** | application/json |

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| identifier | String | 是 | 用户名/手机号/邮箱 |
| captcha | String | 是 | 图形验证码 |
| captchaKey | String | 是 | 图形验证码Key |

**响应参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 是否成功 |
| message | String | 消息 |
| verifyToken | String | 验证令牌（第二步需要） |
| email | String | 用户邮箱（脱敏） |
| phone | String | 用户手机号（脱敏） |

---

### 1.4 忘记密码-第二步（重置密码）

**接口说明**：使用验证令牌重置密码

| 项 | 说明 |
|----|------|
| **方法** | POST |
| **路径** | `/v1/auth/forgot-password` |
| **Content-Type** | application/json |

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| verifyToken | String | 是 | 第一步返回的验证令牌 |
| newPassword | String | 是 | 新密码（6-100位） |
| confirmPassword | String | 是 | 确认新密码 |
| verificationCode | String | 是 | 手机验证码 |
| selectedContact | String | 否 | 选择的联系方式（email/phone） |

**响应参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 是否成功 |
| message | String | 消息 |

---

### 1.5 获取图形验证码

**接口说明**：获取图形验证码

| 项 | 说明 |
|----|------|
| **方法** | GET |
| **路径** | `/v1/auth/captcha` |

**响应参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| captchaKey | String | 验证码Key |
| captchaImage | String | 验证码图片（base64） |

---

### 1.6 检查是否需要验证码

**接口说明**：检查用户是否需要输入图形验证码

| 项 | 说明 |
|----|------|
| **方法** | GET |
| **路径** | `/v1/auth/check-captcha` |

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | String | 是 | 用户名 |

**响应**：`true` / `false`

---

### 1.7 发送手机验证码

**接口说明**：发送手机验证码（注册或忘记密码）

| 项 | 说明 |
|----|------|
| **方法** | POST |
| **路径** | `/v1/auth/send-verification-code` |
| **Content-Type** | application/json |

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| phone | String | 否 | 手机号 |
| email | String | 否 | 邮箱 |
| type | String | 是 | 类型：register / forgot_password |

**响应参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 是否成功 |
| message | String | 消息 |

---

### 1.8 验证登录票据

**接口说明**：验证SSO登录票据有效性

| 项 | 说明 |
|----|------|
| **方法** | GET |
| **路径** | `/v1/auth/validate-ticket` |

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ticket | String | 是 | 登录票据（ST-xxx） |

**响应参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 是否成功 |
| message | String | 消息 |
| username | String | 用户名 |

---

## 二、用户中心接口

**基础路径**：`/v1/users`

### 2.1 获取用户列表

**接口说明**：分页查询用户列表，支持搜索

| 项 | 说明 |
|----|------|
| **方法** | GET |
| **路径** | `/v1/users` |

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | int | 否 | 页码，默认0 |
| size | int | 否 | 每页条数，默认10 |
| keyword | String | 否 | 搜索关键词（用户名/邮箱/昵称） |
| sortBy | String | 否 | 排序字段，默认id |
| sortDir | String | 否 | 排序方向，默认desc |

**响应参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| content | Array | 用户列表 |
| totalPages | int | 总页数 |
| totalElements | long | 总条数 |
| number | int | 当前页码 |
| size | int | 每页条数 |

**用户对象**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 用户ID |
| username | String | 用户名 |
| email | String | 邮箱 |
| phone | String | 手机号 |
| nickname | String | 昵称 |
| avatar | String | 头像URL |
| role | String | 角色（ADMIN/USER） |
| enabled | boolean | 是否启用 |
| loginAttempts | int | 登录失败次数 |
| lastLoginTime | LocalDateTime | 最后登录时间 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

---

### 2.2 根据ID获取用户

**接口说明**：根据用户ID获取用户详情

| 项 | 说明 |
|----|------|
| **方法** | GET |
| **路径** | `/v1/users/{id}` |

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 用户ID |

**响应参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 是否成功 |
| message | String | 消息 |
| data | Object | 用户对象 |

---

### 2.3 根据用户名获取用户

**接口说明**：根据用户名获取用户详情

| 项 | 说明 |
|----|------|
| **方法** | GET |
| **路径** | `/v1/users/username/{username}` |

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | String | 是 | 用户名 |

---

### 2.4 更新用户信息

**接口说明**：更新用户基本信息

| 项 | 说明 |
|----|------|
| **方法** | PUT |
| **路径** | `/v1/users/{id}` |
| **Content-Type** | application/json |

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 用户ID |

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| email | String | 否 | 邮箱 |
| phone | String | 否 | 手机号 |
| nickname | String | 否 | 昵称 |
| avatar | String | 否 | 头像URL |
| role | String | 否 | 角色（ADMIN/USER） |

**响应参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 是否成功 |
| message | String | 消息 |
| data | Object | 更新后的用户对象 |

---

### 2.5 重置用户密码

**接口说明**：管理员重置用户密码

| 项 | 说明 |
|----|------|
| **方法** | PUT |
| **路径** | `/v1/users/{id}/reset-password` |
| **Content-Type** | application/json |

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 用户ID |

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| newPassword | String | 是 | 新密码（6-100位） |

---

### 2.6 切换用户状态

**接口说明**：启用/禁用用户

| 项 | 说明 |
|----|------|
| **方法** | PUT |
| **路径** | `/v1/users/{id}/toggle-status` |

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 用户ID |

---

### 2.7 删除用户

**接口说明**：删除用户（不可恢复）

| 项 | 说明 |
|----|------|
| **方法** | DELETE |
| **路径** | `/v1/users/{id}` |

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | Long | 是 | 用户ID |

---

## 三、内部 gRPC 接口

### 3.1 UserService gRPC 接口

**Proto 定义**：[user.proto](../backend/services/user-server/src/main/proto/user.proto)

#### 3.1.1 ValidateLogin - 验证登录

```protobuf
rpc ValidateLogin(LoginRequest) returns (LoginResponse) {}
```

**请求**：
```protobuf
message LoginRequest {
  string username = 1;
  string password = 2;
}
```

**响应**：
```protobuf
message LoginResponse {
  bool success = 1;
  string message = 2;
  string token = 3;
  string username = 4;
}
```

#### 3.1.2 Register - 用户注册

```protobuf
rpc Register(RegisterRequest) returns (RegisterResponse) {}
```

#### 3.1.3 ForgotPassword - 忘记密码

```protobuf
rpc ForgotPassword(ForgotPasswordRequest) returns (ForgotPasswordResponse) {}
```

#### 3.1.4 FindUserByIdentifier - 查找用户

```protobuf
rpc FindUserByIdentifier(FindUserRequest) returns (FindUserResponse) {}
```

---

### 3.2 SsoService gRPC 接口

**Proto 定义**：[sso.proto](../backend/services/sso-server/src/main/proto/sso.proto)

#### 3.2.1 ValidateTicket - 验证票据

```protobuf
rpc ValidateTicket(TicketValidationRequest) returns (TicketValidationResponse) {}
```

**请求**：
```protobuf
message TicketValidationRequest {
  string ticket = 1;
}
```

**响应**：
```protobuf
message TicketValidationResponse {
  bool valid = 1;
  string username = 2;
  string message = 3;
}
```

---

## 四、公共响应格式

### 4.1 统一响应结构

```json
{
  "success": true,
  "message": "操作成功",
  "data": {}
}
```

### 4.2 分页响应结构

```json
{
  "content": [],
  "totalPages": 10,
  "totalElements": 100,
  "number": 0,
  "size": 10
}
```

### 4.3 错误响应示例

```json
{
  "success": false,
  "message": "用户名或密码错误"
}
```
