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
│   │       └── pom.xml
│   ├── common/                       # 公共模块（工具类、通用配置等）
│   └── services/                     # 业务微服务集合
│       └── sso-server/               # SSO 单点登录服务
│           ├── src/
│           │   └── main/
│           │       ├── java/
│           │       └── resources/
│           │           ├── application.yml           # 主配置
│           │           ├── application-dev.yml       # 开发环境
│           │           ├── application-test.yml      # 测试环境
│           │           └── application-prod.yml      # 生产环境
│           └── pom.xml
├── frontend/                          # 前端工程
│   ├── apps/                         # 业务应用集合
│   │   └── sso-web/                 # SSO 前端应用
│   │       ├── public/
│   │       ├── src/
│   │       ├── package.json
│   │       ├── .env.development     # 开发环境
│   │       ├── .env.test            # 测试环境
│   │       └── .env.production      # 生产环境
│   └── common/                       # 前端公共模块（组件库、工具类等）
├── docs/                              # 项目文档
├── .gitignore                        # Git 忽略配置
└── README.md                         # 项目说明文档
```

### 架构分层说明

#### 1. 框架层 (frameworks)
- **zqzl-framework**: 独立的 Maven 父 POM 项目，统一管理所有微服务的依赖版本
- 可独立发布到 Maven 仓库，供各业务服务继承使用
- 包含：Spring Boot 版本管理、常用依赖版本管理、插件配置等

#### 2. 公共模块层 (common)
- 存放各服务共享的工具类、通用配置、实体基类等
- 减少代码重复，提高复用性

#### 3. 业务服务层 (services)
- 各业务微服务独立开发、部署
- 继承 zqzl-framework 父 POM
- 每个服务包含完整的多环境配置

---

## 多环境配置规范

### 后端多环境配置

#### 配置文件说明

| 配置文件 | 环境 | 说明 |
|---------|------|------|
| `application.yml` | 主配置 | 公共配置，默认激活 dev 环境 |
| `application-dev.yml` | 开发环境 | 本地开发使用，H2 内存数据库，DEBUG 日志 |
| `application-test.yml` | 测试环境 | 测试环境使用，H2 文件数据库，INFO 日志 |
| `application-prod.yml` | 生产环境 | 生产环境使用，支持外部数据库配置，WARN 日志 |

#### 环境切换方式

**方式1：修改主配置文件**
```yaml
# application.yml
spring:
  profiles:
    active: dev  # 可切换为 test / prod
```

**方式2：启动命令指定**
```bash
# 开发环境
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 测试环境
mvn spring-boot:run -Dspring-boot.run.profiles=test

# 生产环境
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

**方式3：JAR 包启动时指定**
```bash
java -jar sso-server-1.0.0.jar --spring.profiles.active=prod
```

#### 生产环境数据库配置
生产环境支持通过环境变量注入数据库配置：
```bash
export DB_URL=jdbc:mysql://localhost:3306/zqzl_sso
export DB_DRIVER=com.mysql.cj.jdbc.Driver
export DB_USERNAME=zqzl_user
export DB_PASSWORD=your_password
export DB_DIALECT=org.hibernate.dialect.MySQL8Dialect
```

---

### 前端多环境配置

#### 配置文件说明

| 配置文件 | 环境 | 说明 |
|---------|------|------|
| `.env.development` | 开发环境 | 本地 API 地址，开启调试 |
| `.env.test` | 测试环境 | 测试环境 API 地址 |
| `.env.production` | 生产环境 | 生产环境 API 地址，关闭 SourceMap |

#### 环境变量说明

| 变量名 | 说明 |
|-------|------|
| `REACT_APP_ENV` | 当前环境标识 |
| `REACT_APP_API_BASE_URL` | API 基础地址 |
| `REACT_APP_DEBUG` | 是否开启调试模式 |
| `REACT_APP_MOCK` | 是否使用 Mock 数据 |
| `PORT` | 开发服务器端口 |
| `GENERATE_SOURCEMAP` | 是否生成 SourceMap |

#### 启动命令

```bash
# 安装依赖
cd frontend/apps/sso-web
npm install

# 开发环境启动（默认）
npm start
# 或
npm run start:dev

# 测试环境启动
npm run start:test

# 生产环境启动
npm run start:prod
```

#### 构建命令

```bash
# 生产环境构建（默认）
npm run build

# 开发环境构建
npm run build:dev

# 测试环境构建
npm run build:test

# 生产环境构建
npm run build:prod
```

---

## 功能特性

### SSO 单点登录服务

- 用户名密码登录验证
- 密码错误 1 次后显示验证码输入
- 验证码可点击刷新
- JWT Token 认证
- 支持多环境配置
- CORS 跨域支持

## 默认账号

- 用户名: `admin`
- 密码: `admin123`

---

## 快速开始

### 1. 构建框架层（首次必须执行）

```bash
cd backend/frameworks/zqzl-framework
mvn clean install
```

### 2. 启动后端 SSO 服务

```bash
cd backend/services/sso-server

# 开发环境（默认）
mvn spring-boot:run

# 指定环境启动
mvn spring-boot:run -Dspring-boot.run.profiles=test
```

后端服务运行在: `http://localhost:8080/sso`

### 3. 启动前端 SSO 应用

```bash
cd frontend/apps/sso-web
npm install

# 开发环境启动
npm start

# 指定环境启动
npm run start:test
```

前端服务运行在: `http://localhost:3000`

---

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

---

## 技术栈

### 后端
- Spring Boot 2.7.x
- Spring Security
- Spring Data JPA
- H2 Database (开发/测试)
- MySQL (生产推荐)
- JWT
- BCrypt 密码加密
- Maven 多模块管理

### 前端
- React 18
- Axios
- CSS3
- dotenv 环境配置管理

---

## 新增微服务指南

### 新增后端微服务

1. 在 `backend/services/` 下创建新服务目录
2. 创建服务的 pom.xml，继承 zqzl-framework：
```xml
<parent>
    <groupId>com.zqzl.framework</groupId>
    <artifactId>zqzl-framework</artifactId>
    <version>1.0.0</version>
    <relativePath>../../frameworks/zqzl-framework/pom.xml</relativePath>
</parent>
```
3. 按标准 Spring Boot 项目结构开发
4. 添加多环境配置文件（application-dev/test/prod.yml）

### 新增前端应用

1. 在 `frontend/apps/` 下创建新应用目录
2. 按标准 React/Vue 项目结构初始化
3. 添加多环境配置文件（.env.development/test/production）
4. 可复用 `frontend/common/` 下的公共组件

---

## 开发规范

- 各微服务独立开发、部署
- 框架层变更需谨慎评估，发布后更新所有服务依赖
- 公共模块变更需谨慎评估影响
- 前后端接口遵循 RESTful 规范
- 代码提交前请确保本地测试通过
- 所有配置必须支持多环境切换，敏感配置使用环境变量注入
