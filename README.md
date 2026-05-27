# ZQZL 微服务架构平台

基于轻量化微服务架构，采用 APISIX 网关 + gRPC 服务间通信，支持 Docker 容器化部署。

---

## 快速导航

| 文档 | 说明 |
|------|------|
| [技术架构说明](docs/ARCHITECTURE.md) | 整体架构设计、技术栈、目录结构 |
| [业务流程说明](docs/BUSINESS_FLOW.md) | 登录、注册、用户管理等业务流程图 |
| [部署指南](docs/DEPLOYMENT.md) | 本地开发、Docker 部署步骤 |
| [API 接口文档](docs/API.md) | REST API 接口详细说明 |
| [项目问题分析与整改建议](docs/PROJECT_ISSUES_ANALYSIS.md) | 当前项目全部问题、根因分析、整改方案 |
| [SSO认证体系历史问题分析](docs/SSO_ISSUE_ANALYSIS.md) | SSO认证体系改造前问题记录（历史文档） |

---

## 项目概览

### 架构特点

- ✅ **APISIX 网关**：统一入口，路由转发
- ✅ **gRPC 通信**：服务间高性能 RPC 通信
- ✅ **无注册中心**：Docker 网络 + 静态地址
- ✅ **前后端分离**：React SPA + Spring Boot 微服务
- ✅ **容器化部署**：Docker Compose 一键部署

### 服务列表

| 服务 | 开发端口 | 说明 |
|------|---------|------|
| sso-server | 8080 | 单点登录服务 |
| user-server | 8081 | 用户中心服务 |
| sso-web | 3000 | 登录门户前端 |
| user-web | 3031 | 用户管理前端 |
| APISIX | 80 | API 网关 |

### 快速开始

#### 本地开发

```bash
# 1. 构建框架层
cd backend/frameworks/zqzl-framework
mvn clean install -DskipTests

# 2. 启动后端服务
cd backend/services/user-server
mvn spring-boot:run

cd backend/services/sso-server
mvn spring-boot:run

# 3. 启动前端应用
cd frontend/apps/sso-web
npm install && npm start

cd frontend/apps/user-web
npm install && npm start
```

#### Docker 部署

```bash
# 1. 构建所有镜像
bash ops/scripts/build-all.sh

# 2. 启动所有服务
docker compose up -d
```

### 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ADMIN |

---

## 文档结构

```
zqzl-traecn/
├── docs/                           # 项目文档
│   ├── ARCHITECTURE.md           # 技术架构
│   ├── BUSINESS_FLOW.md        # 业务流程
│   ├── DEPLOYMENT.md           # 部署指南
│   └── API.md                    # API 文档
├── backend/                        # 后端代码
├── frontend/                       # 前端代码
├── ops/                            # 运维配置
└── docker-compose.yml             # Docker 编排
```
