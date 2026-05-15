# ZQZL 微服务架构平台

基于微服务架构的企业级应用平台，采用分层架构设计，支持多环境部署。

---

## 项目架构

### 整体目录结构

```
zqzl-traecn/
├── backend/                           # 后端工程
│   ├── frameworks/                   # 框架层（独立项目，可单独发布）
│   │   └── zqzl-framework/          # 统一依赖管理父POM
│   ├── common/                       # 公共模块（工具类、通用配置等）
│   └── services/                     # 业务微服务集合
│       ├── sso-server/              # SSO 单点登录服务（端口：8080）
│       │   └── 作用：统一登录网关，票据生成/验证，调用用户中心认证
│       └── user-server/             # 用户中心服务（端口：8081）
│           └── 作用：用户CRUD、注册/登录验证、角色权限管理
├── frontend/                          # 前端工程
│   ├── apps/                         # 业务应用集合
│   │   ├── sso-web/                 # SSO 登录门户
│   │   │   └── 作用：提供统一登录页面，票据展示
│   │   └── user-web/                # 用户管理后台
│   │       └── 作用：用户列表、编辑、启用/禁用、重置密码
│   └── common/                       # 前端公共模块（组件库、工具类等）
├── docs/                              # 项目文档
├── .gitignore                        # Git 忽略配置
└── README.md                         # 项目说明文档
```

### 微服务调用关系

```
sso-web (登录门户)
    ↓ HTTP
sso-server (8080)
    ↓ HTTP 调用
user-server (8081)
    ↓ JPA
H2 Database
```

### 各服务职责

| 服务名称 | 端口 | 职责 |
|---------|------|------|
| **sso-server** | 8080 | 1. 接收登录请求并转发给user-server<br>2. 验证通过后生成SSO票据(ST-xxx)<br>3. 提供票据验证接口<br>4. 从user-server获取验证码<br>*无数据库，票据存储在内存中* |
| **user-server** | 8081 | 1. 用户CRUD管理<br>2. 用户名/密码认证<br>3. 验证码生成/验证<br>4. 用户注册、忘记密码<br>5. 用户启用/禁用<br>6. 重置密码<br>7. 分页查询、关键词搜索 |

---

## 快速开始

### 1. 构建框架层（首次必须执行）

```bash
cd backend/frameworks/zqzl-framework
mvn clean install
```

### 2. 启动后端服务

**启动用户中心服务（必须先启动）：**
```bash
cd backend/services/user-server
mvn spring-boot:run
```
运行在: http://localhost:8081/user

**启动SSO单点登录服务：**
```bash
cd backend/services/sso-server
mvn spring-boot:run
```
运行在: http://localhost:8080/sso

### 3. 启动前端应用

**启动用户管理后台：**
```bash
cd frontend/apps/user-web
npm install
npm start
```
运行在: http://localhost:3000

**启动SSO登录门户：**
```bash
cd frontend/apps/sso-web
npm install
npm start
```
运行在: http://localhost:3001 (建议修改端口)

## 默认账号

- 用户名: `admin`
- 密码: `admin123`
- 角色: ADMIN（管理员）

---

## API 接口文档

### user-server 用户中心接口 (8081)

#### 认证接口

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/api/auth/login` | 用户登录验证 |
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/forgot-password` | 重置密码 |
| GET | `/api/auth/captcha` | 获取验证码 |
| GET | `/api/auth/check-captcha` | 检查是否需要验证码 |

#### 用户管理接口

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/users` | 获取用户列表（分页、搜索）|
| GET | `/api/users/{id}` | 获取单个用户详情 |
| GET | `/api/users/username/{username}` | 根据用户名获取用户 |
| PUT | `/api/users/{id}` | 更新用户基本信息（邮箱、手机号、昵称、角色）|
| PUT | `/api/users/{id}/reset-password` | 重置用户密码 |
| PUT | `/api/users/{id}/toggle-status` | 切换用户启用/禁用状态 |
| DELETE | `/api/users/{id}` | 删除用户 |

### sso-server 单点登录接口 (8080)

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/api/auth/login` | SSO登录（调用user-server认证后返回票据）|
| GET | `/api/auth/captcha` | 获取验证码（转发到user-server）|
| GET | `/api/auth/check-captcha` | 检查是否需要验证码（转发到user-server）|
| GET | `/api/auth/validate-ticket` | 验证票据有效性 |

---

## 技术栈

### 后端
- Spring Boot 2.7.x
- Spring Security
- Spring Data JPA (user-server)
- H2 Database (user-server)
- RestTemplate (服务间调用)
- JSR-380 参数校验
- Lombok

### 前端
- React 18
- React Router v6 (仅在user-web使用)
- Axios（HTTP 客户端）
- CSS3（响应式样式）

---

## 功能特性

### 用户中心 (user-server + user-web)
1. **用户认证**：用户名密码登录、验证码机制
2. **用户注册**：完整的注册表单验证
3. **忘记密码**：通过用户名+邮箱重置密码
4. **用户管理**：
   - 分页查询列表
   - 按用户名、邮箱、昵称搜索
   - 编辑用户基本信息（不包含启用状态和密码）
   - 快捷操作按钮：启用/禁用、重置密码、删除
   - 固定表格高度，内容滚动
5. **角色权限**：ADMIN管理员 / USER普通用户

### SSO单点登录 (sso-server + sso-web)
1. **统一登录入口**：登录请求转发到user-server验证
2. **票据机制**：登录成功后生成 ST-xxx 格式票据
3. **票据验证**：提供票据验证接口
4. **验证码**：验证码来自user-server服务

---

## 开发规范

- 各微服务独立开发、部署
- 微服务之间通过HTTP调用（RestTemplate）
- 框架层变更需谨慎评估，发布后更新所有服务依赖
- 公共模块变更需谨慎评估影响
- 前后端接口遵循 RESTful 规范
- 代码提交前请确保本地测试通过
- 所有配置必须支持多环境切换，敏感配置使用环境变量注入
- DTO与Entity分离，避免直接暴露数据库实体
- 使用BCrypt加密存储所有用户密码
- 所有输入参数必须进行长度、格式校验
- 用户操作按钮按功能分类，提高易用性
