# SSO 单点登录系统

前后端分离的SSO单点登录系统，支持用户名密码验证和验证码功能。

## 项目结构

```
sso-system/
├── backend/          # 后端 Spring Boot 应用
│   ├── src/
│   └── pom.xml
└── frontend/         # 前端 React 应用
    ├── src/
    └── package.json
```

## 功能特性

- 用户名密码登录验证
- 密码错误1次后显示验证码输入
- 验证码可点击刷新
- JWT Token 认证
- H2 内存数据库
- CORS 跨域支持

## 默认账号

- 用户名: `admin`
- 密码: `admin123`

## 启动说明

### 1. 启动后端服务

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

后端服务运行在: `http://localhost:8080/sso`

### 2. 启动前端服务

```bash
cd frontend
npm install
npm start
```

前端服务运行在: `http://localhost:3000`

## API 接口

### 登录
- POST `/api/auth/login`
- 请求体: `{ username, password, captcha?, captchaKey? }`

### 获取验证码
- GET `/api/auth/captcha`
- 返回: `{ captchaKey, captchaImage }`

### 检查是否需要验证码
- GET `/api/auth/check-captcha?username=xxx`
- 返回: `true/false`

## 技术栈

### 后端
- Spring Boot 2.7.x
- Spring Security
- Spring Data JPA
- H2 Database
- JWT
- BCrypt 密码加密

### 前端
- React 18
- Axios
- CSS3
