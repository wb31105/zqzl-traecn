# 部署指南

## 一、环境要求

### 1.1 本地开发环境

| 软件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 11+ | Java 开发环境 |
| Maven | 3.8+ | 后端构建工具 |
| Node.js | 16+ | 前端运行环境 |
| npm | - | 前端包管理工具 |
| Docker | - | 容器化部署 |
| Docker Compose | - | 容器编排 |

### 1.2 端口规划

**开发环境端口**：

| 服务 | HTTP 端口 | gRPC 端口 | 说明 |
|------|----------|----------|------|
| sso-server | 8080 | 9090 | 单点登录服务 |
| user-server | 8081 | 9091 | 用户中心服务 |
| sso-web | 3000 | - | 登录门户前端 |
| user-web | 3031 | - | 管理平台前端 |

**Docker 环境端口**：

| 服务 | 容器端口 | 映射端口 | 说明 |
|------|----------|----------|------|
| etcd | 2379 | 2379 | APISIX 配置存储 |
| APISIX | 9080 | 80 | 网关 HTTP |
| APISIX | 9443 | 443 | 网关 HTTPS |
| sso-server | 8080 | 8080 | 认证服务 |
| user-server | 8080 | 8081 | 用户服务 |
| sso-web | 80 | 3000 | 登录门户 |
| user-web | 80 | 3031 | 管理平台 |

---

## 二、本地开发部署

### 2.1 初始化项目

```bash
# 克隆项目
git clone <repository-url>
cd zqzl-traecn

# 给所有脚本添加执行权限
find . -name "*.sh" -type f -exec chmod +x {} \;
```

### 2.2 构建框架层（首次必须执行）

```bash
cd backend/frameworks/zqzl-framework
mvn clean install -DskipTests
```

### 2.3 启动后端服务

**方式一：Maven 启动**

```bash
# 启动 user-server (端口 8081, gRPC 9091)
cd backend/services/user-server
mvn spring-boot:run

# 启动 sso-server (端口 8080, gRPC 9090)
cd backend/services/sso-server
mvn spring-boot:run
```

**方式二：Jar 包启动**

```bash
# 构建 user-server
cd backend/services/user-server
mvn clean package -DskipTests
java -jar target/user-server-1.0.0.jar

# 构建 sso-server
cd backend/services/sso-server
mvn clean package -DskipTests
java -jar target/sso-server-1.0.0.jar
```

### 2.4 启动前端应用

```bash
# 启动 sso-web (端口 3000)
cd frontend/apps/sso-web
npm install
npm start

# 启动 user-web (端口 3031)
cd frontend/apps/user-web
npm install
npm start
```

### 2.5 访问地址（开发环境）

| 服务 | 地址 | 说明 |
|------|------|------|
| 登录门户 | http://localhost:3000 | SSO 认证前端 |
| 管理平台 | http://localhost:3031 | 用户管理后台 |
| SSO API | http://localhost:8080/sso/v1/auth | 认证服务 API |
| User API | http://localhost:8081/v1/users | 用户服务 API |

### 2.6 默认测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ADMIN（管理员） |

---

## 三、Docker Compose 部署

### 3.1 构建所有服务镜像

```bash
# 方式一：使用构建脚本
bash ops/scripts/build-all.sh

# 方式二：分别构建
# 构建后端服务
bash backend/services/sso-server/deploy/build.sh
bash backend/services/user-server/deploy/build.sh

# 构建前端应用
bash frontend/apps/sso-web/deploy/build.sh
bash frontend/apps/user-web/deploy/build.sh

# 构建 APISIX 网关
bash ops/scripts/build-apisix.sh
```

### 3.2 启动所有服务

```bash
# 启动所有服务
docker compose up -d

# 查看服务状态
docker compose ps

# 查看服务日志
docker compose logs -f apisix
docker compose logs -f sso-server
docker compose logs -f user-server

# 停止所有服务
docker compose down
```

### 3.3 访问地址（Docker 环境）

| 服务 | 地址 | 说明 |
|------|------|------|
| 登录门户 | http://localhost | SSO 认证前端（通过 APISIX） |
| 管理平台 | http://localhost/user | 用户管理后台（通过 APISIX） |
| SSO API | http://localhost/v1/auth | 认证服务 API（通过 APISIX） |
| User API | http://localhost/v1/users | 用户服务 API（通过 APISIX） |

---

## 四、单个服务构建说明

### 4.1 后端服务构建

**sso-server 构建**：

```bash
cd backend/services/sso-server

# 方式一：使用脚本
bash deploy/build.sh

# 方式二：手动构建
mvn clean package -DskipTests
docker build -f deploy/Dockerfile -t zqzl/sso-server:latest .
```

**user-server 构建**：

```bash
cd backend/services/user-server

# 方式一：使用脚本
bash deploy/build.sh

# 方式二：手动构建
mvn clean package -DskipTests
docker build -f deploy/Dockerfile -t zqzl/user-server:latest .
```

### 4.2 前端应用构建

**sso-web 构建**：

```bash
cd frontend/apps/sso-web

# 方式一：使用脚本
bash deploy/build.sh

# 方式二：手动构建
npm install
npm run build
docker build -f deploy/Dockerfile -t zqzl/sso-web:latest .
```

**user-web 构建**：

```bash
cd frontend/apps/user-web

# 方式一：使用脚本
bash deploy/build.sh

# 方式二：手动构建
npm install
npm run build
docker build -f deploy/Dockerfile -t zqzl/user-web:latest .
```

### 4.3 APISIX 网关构建

```bash
# 方式一：使用脚本
bash ops/scripts/build-apisix.sh

# 方式二：手动构建
cd ops/docker/apisix
docker build -t zqzl/apisix-gateway:latest .
```

---

## 五、配置说明

### 5.1 后端配置文件

每个服务包含两个配置文件：

| 文件 | 用途 | 激活方式 |
|------|------|----------|
| `application.yml` | 默认配置（生产环境） | 默认加载 |
| `application-dev.yml` | 开发环境覆盖 | `SPRING_PROFILES_ACTIVE=dev` |

**配置优先级**：
1. 命令行参数
2. 环境变量
3. `application-dev.yml`（如果激活 dev profile）
4. `application.yml`

### 5.2 环境变量说明

**sso-server 环境变量**：

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `SPRING_PROFILES_ACTIVE` | Spring Profile | dev |
| `SERVER_PORT` | HTTP 端口 | 8080 |
| `GRPC_SERVER_PORT` | gRPC 端口 | 9090 |
| `USER_GRPC_HOST` | user-server gRPC 地址 | user-server |
| `USER_GRPC_PORT` | user-server gRPC 端口 | 9091 |

**user-server 环境变量**：

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `SPRING_PROFILES_ACTIVE` | Spring Profile | dev |
| `SERVER_PORT` | HTTP 端口 | 8080 |
| `GRPC_SERVER_PORT` | gRPC 端口 | 9091 |
| `SSO_GRPC_HOST` | sso-server gRPC 地址 | sso-server |
| `SSO_GRPC_PORT` | sso-server gRPC 端口 | 9090 |

### 5.3 前端配置

**开发环境配置**（`.env.development`）：

```
# API 地址（相对路径，由代理转发）
REACT_APP_API_BASE_URL=
```

**生产环境配置**（`.env`）：

```
# API 地址（相对路径，由网关转发）
REACT_APP_API_BASE_URL=
```

---

## 六、常见问题排查

### 6.1 脚本执行权限问题

**问题**：`Permission denied` 或 `sh: permission denied`

**解决方案**：
```bash
# 给所有脚本添加执行权限
find . -name "*.sh" -type f -exec chmod +x {} \;

# 或单独给某个脚本
chmod +x ops/scripts/build-apisix.sh
```

### 6.2 gRPC 连接失败

**问题**：`UNAVAILABLE: Connection refused`

**排查步骤**：
1. 确认目标服务是否启动
2. 检查 gRPC 端口是否正确
3. 查看配置文件中 gRPC 地址是否正确
4. 确认 Docker 网络是否正常

### 6.3 前端代理不生效

**问题**：开发环境接口调用失败

**排查步骤**：
1. 确认 `setupProxy.js` 配置正确
2. 确认后端服务已启动
3. 检查端口是否被占用

### 6.4 Docker 构建失败

**问题**：`COPY failed: file not found`

**排查步骤**：
1. 确认在正确目录执行构建
2. 检查 Dockerfile 中的 COPY 路径
3. 确认源码文件存在

### 6.5 端口占用问题

**问题**：端口被占用导致服务无法启动

**解决方案**：
```bash
# 检查所有服务端口
lsof -ti :8080 -ti :8081 -ti :9090 -ti :9091 -ti :3000 -ti :3031 -ti :80

# 杀死占用端口的进程
kill $(lsof -ti :8080 -ti :8081 -ti :9090 -ti :9091 -ti :3000 -ti :3031 -ti :80)
```

### 6.6 etcd 镜像问题

**问题**：`manifest for bitnami/etcd:3.5.9 not found`

**原因**：bitnami/etcd:3.5.9 镜像已被移除或不存在

**解决方案**：
使用可用的镜像版本（如 3.5.12），已在 `docker-compose.yml` 中修复。

---

## 七、服务维护

### 7.1 查看服务状态

```bash
# Docker Compose 方式
docker compose ps

# 查看日志
docker compose logs -f apisix
docker compose logs -f sso-server
docker compose logs -f user-server
```

### 7.2 重启单个服务

```bash
# 重启 sso-server
docker compose restart sso-server

# 重启 user-server
docker compose restart user-server
```

### 7.3 重新构建并启动

```bash
# 重新构建 sso-server 镜像
bash backend/services/sso-server/deploy/build.sh

# 重启服务
docker compose up -d --force-recreate sso-server
```

---

## 八、版本历史

| 版本 | 日期 | 更新内容 | 更新人 |
|------|------|----------|--------|
| v4.0 | 2026-05-21 | 架构重构：APISIX 网关 + gRPC 服务间调用 | 系统 |
| v3.0 | 2026-05-19 | 域名架构升级，三域名分离 | 系统 |
| v2.0 | 2026-05-19 | Docker 容器化部署架构设计 | 系统 |
| v1.0 | 2026-05-19 | 初始版本 | 系统 |
