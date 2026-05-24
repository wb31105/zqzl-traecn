# 部署指南

## 一、环境规范

### 1.1 环境命名规范

所有项目统一使用 `local` 作为本地开发环境标识，`integration` 作为集成环境（Docker Compose）标识，其他环境配置通过环境变量动态注入。

| 环境类型 | 配置文件命名 | 说明 |
|---------|-------------|------|
| 本地开发环境 | `application-local.yml` (后端) / `.env.local` (前端) | 本地开发使用 |
| 集成环境 | `application.yml` (后端) / `.env` (前端) | Docker Compose 部署 |

### 1.2 运行时版本统一规范

#### 后端 - JDK 版本统一

| 环境 | JDK 版本 | 说明 |
|------|---------|------|
| 本地开发 | Temurin-17.0.15+6 | 本机已安装 |
| Docker 构建 | maven:3.9.6-eclipse-temurin-17 | 构建镜像 |
| Docker 运行 | eclipse-temurin:17-jre | 运行镜像 |

**依赖版本（JDK 17 最兼容）**:
- Spring Boot: 2.7.18 (LTS，官方支持 JDK 17)
- gRPC: 1.62.2
- Protobuf: 3.25.3
- Lombok: 1.18.32
- H2: 2.2.224
- JJWT: 0.11.5

#### 前端 - Node 版本统一

| 环境 | Node 版本 | 说明 |
|------|---------|------|
| 本地开发 | v22.16.0 | 本机已安装 |
| Docker 构建 | node:22-alpine | 构建镜像 |

#### 网关 - APISIX 版本统一

| 环境 | APISIX 版本 | 配置中心 | 是否需要 etcd |
|------|------------|---------|-------------|
| 本地开发 | 2.15.0 | yaml (standalone) | ❌ 不需要 |
| 集成环境 | 2.15.0 | yaml (standalone) | ❌ 不需要 |

> **重要说明**: 本项目使用 APISIX 的 standalone 模式（`config_center: yaml`），所有路由配置通过 `apisix.yaml` 文件加载，**完全不需要 etcd**。

> **APISIX 构建说明**: 本项目使用**一套 Docker 镜像**同时支持本地环境和集成环境，通过**环境变量注入**实现配置差异化，无需维护多套配置文件。

> **实现原理**:
> 1. 镜像打包统一的配置模板 `apisix.yaml.template`（含 `${VAR}` 占位符）
> 2. 容器启动时通过 `docker-entrypoint.sh` 脚本替换环境变量
> 3. 生成最终的 `apisix.yaml` 配置文件

> **环境变量列表**:
> | 变量名 | 说明 | 集成环境值 | 本地环境值 |
> |--------|------|----------|----------|
> | `API_HOST` | API 网关域名 | api.bw.com | api.local.bw.com |
> | `SSO_WEB_HOST` | SSO 登录门户域名 | sso.bw.com | sso.local.bw.com |
> | `ADMIN_WEB_HOST` | 管理平台域名 | admin.bw.com | admin.local.bw.com |
> | `SSO_SERVER_UPSTREAM` | SSO 服务上游地址 | sso-server:8080 | host.docker.internal:8081 |
> | `USER_SERVER_UPSTREAM` | 用户服务上游地址 | user-server:8080 | host.docker.internal:8082 |
> | `SSO_WEB_UPSTREAM` | SSO 前端上游地址 | sso-web:80 | host.docker.internal:3001 |
> | `USER_WEB_UPSTREAM` | 用户前端上游地址 | user-web:80 | host.docker.internal:3002 |

> **网络说明**: 本地环境使用 `host.docker.internal` 特殊域名访问宿主机服务，兼容 Mac/Windows/Linux 各平台 Docker。

### 1.3 脚本目录结构规范

```
ops/scripts/
├── docker/              # 集成环境（Docker）镜像构建脚本
│   ├── build-all.sh     # 构建所有镜像（含 APISIX）
│   ├── build-apisix.sh  # 构建 APISIX 网关镜像
│   ├── build-backend.sh # 构建后端服务镜像
│   └── build-frontend.sh # 构建前端应用镜像
└── local/               # 本地开发脚本
    ├── build.sh         # 本地一键编译
    ├── start.sh         # 本地一键启动/停止
    └── README.md        # 本地脚本使用说明
```

### 1.4 本地环境端口规划

**本地环境域名统一使用 `*.local.bw.com`**

| 服务 | HTTP 端口 | gRPC 端口 | 域名 | 说明 |
|------|----------|----------|------|------|
| APISIX 网关 | 8080 | - | sso.local.bw.com / admin.local.bw.com / api.local.bw.com | API 网关 |
| sso-server | 8081 | 9091 | - | 单点登录服务 |
| user-server | 8082 | 9092 | - | 用户中心服务 |
| sso-web | 3001 | - | sso.local.bw.com | 登录门户前端 |
| user-web | 3002 | - | admin.local.bw.com | 管理平台前端 |

### 1.5 集成环境域名与端口规划

**行业标准端口约定：**
- 对外网关：HTTP 80, HTTPS 443（标准端口）
- APISIX 内部：9080/9443（官方镜像默认端口）
- Spring Boot 后端：8080（约定俗成）
- 前端 Nginx：80（标准）
- gRPC：9090

| 服务 | 域名 | 容器端口 | 映射端口 | 说明 |
|------|------|----------|----------|------|
| APISIX 网关 | - | 9080 / 9443 / 9090 | 80 / 443 / 9090 | 网关入口（标准端口映射） |
| sso-web | sso.bw.com | 80 | - | 登录门户（通过网关访问） |
| user-web | admin.bw.com | 80 | - | 管理平台（通过网关访问） |
| sso-server | api.bw.com | 8080 / 9090 | - | 认证服务（Spring Boot 默认） |
| user-server | api.bw.com | 8080 / 9090 | - | 用户服务（Spring Boot 默认） |

---

## 二、配置文件规范

### 2.1 配置原则

**无默认值原则**：所有配置项必须显式配置，不允许设置默认值。配置了就是配置了，没配置就是没配置，避免因默认值导致不同环境混乱。

### 2.2 后端服务配置

每个后端服务包含两个配置文件：

| 文件 | 用途 | 激活方式 |
|------|------|----------|
| `application.yml` | 基础配置（集成环境） | 默认加载 |
| `application-local.yml` | 本地开发环境具体值 | Spring Profile = `local` |

**配置优先级**：
1. 命令行参数
2. 环境变量
3. `application-local.yml`（如果激活 local profile）
4. `application.yml`

### 2.3 前端应用配置

每个前端应用包含两个配置文件：

| 文件 | 用途 | 加载方式 |
|------|------|----------|
| `.env` | 默认配置（集成环境） | 构建时默认加载 |
| `.env.local` | 本地开发环境配置 | 本地启动时加载 |

### 2.4 APISIX 网关配置

| 文件 | 用途 |
|------|------|
| `config.yaml` / `apisix.yaml` | 集成环境配置（打包进镜像） |
| `config-local.yaml` / `apisix-local.yaml` | 本地开发环境配置（启动时挂载） |

**配置挂载说明**：
- **集成环境**: 使用镜像内默认配置，路由使用 Docker 服务名
- **本地环境**: 启动时通过 volume 挂载本地配置，路由使用 localhost + 端口

### 2.5 清理的无用配置

| 已删除配置项 | 原因 |
|-------------|------|
| `user.service.url` | 服务间调用已使用 gRPC，HTTP 调用配置无用 |
| `sso.service.url` | 服务间调用已使用 gRPC，HTTP 调用配置无用 |

---

## 三、本地开发部署

### 3.1 初始化项目

```bash
# 克隆项目
git clone <repository-url>
cd zqzl-traecn

# 给所有脚本添加执行权限
find . -name "*.sh" -type f -exec chmod +x {} \;
```

### 3.2 配置 Hosts

**重要：本地开发建议配置域名访问**

在 `/etc/hosts` 中添加：

```
127.0.0.1 sso.local.bw.com admin.local.bw.com api.local.bw.com
```

### 3.3 一键编译（推荐）

```bash
# 编译框架层、后端服务、前端应用
bash ops/scripts/local/build.sh
```

编译内容：
- 框架层: `zqzl-framework` (mvn install)
- 后端服务: `user-server`, `sso-server` (mvn package)
- 前端应用: `sso-web`, `user-web` (npm run build:local)

### 3.4 一键启动（推荐）

```bash
# 启动所有服务
bash ops/scripts/local/start.sh

# 查看服务状态
bash ops/scripts/local/start.sh status

# 停止所有服务
bash ops/scripts/local/start.sh stop

# 重启所有服务
bash ops/scripts/local/start.sh restart
```

**APISIX 启动方式说明**:
- 使用统一的纯净 Docker 镜像 `zqzl/apisix-gateway:latest`（与集成环境完全一致）
- 启动时通过 volume 挂载本地配置文件，修改配置无需重建镜像
- 使用 `docker compose -f docker-compose-local.yml up -d` 方式启动
- 通过 `host.docker.internal` 特殊域名访问宿主机服务（兼容 Mac/Windows/Linux）
- 版本与集成环境完全一致 (APISIX 2.15.0, standalone 模式，无需 etcd)

**为什么用 Docker 启动 APISIX 而非本地安装？**

| 对比项 | Docker 启动（推荐） | 本地安装 |
|--------|-------------------|--------|
| 环境一致性 | ✅ 与集成环境 100% 一致 | ❌ 可能因系统环境不同有差异 |
| 依赖安装 | ✅ 无需安装 OpenResty 等依赖 | ❌ 需手动安装 OpenResty、APISIX 及依赖 |
| 配置管理 | ✅ 挂载本地配置文件，修改即生效 | ❌ 配置文件路径不同，需维护两份 |
| 版本控制 | ✅ 镜像版本固定，可回滚 | ❌ 本地版本管理困难 |
| 启动速度 | ⚡ 几秒启动 | ⚡ 安装耗时，启动快 |
| 资源占用 | 📦 约 100MB 内存 | 📦 类似 |

**结论**：推荐使用 Docker 方式启动 APISIX，能最大程度保证**本地开发 ≈ 集成环境**的一致性。

### 3.5 手动启动（可选）

#### 启动后端服务

**方式一：Maven 启动（开发调试）**

```bash
# 启动 user-server (端口 8082, gRPC 9092)
cd backend/services/user-server
mvn spring-boot:run -Dspring.profiles.active=local

# 启动 sso-server (端口 8081, gRPC 9091)
cd backend/services/sso-server
mvn spring-boot:run -Dspring.profiles.active=local
```

**方式二：Jar 包启动**

```bash
# 构建 user-server
cd backend/services/user-server
mvn clean package -DskipTests
java -jar target/user-server-1.0.0.jar --spring.profiles.active=local

# 构建 sso-server
cd backend/services/sso-server
mvn clean package -DskipTests
java -jar target/sso-server-1.0.0.jar --spring.profiles.active=local
```

#### 启动前端应用

```bash
# 启动 sso-web (端口 3001)
cd frontend/apps/sso-web
npm install
npm start

# 启动 user-web (端口 3002)
cd frontend/apps/user-web
npm install
npm start
```

#### 启动 APISIX 网关（Docker）

```bash
# 构建镜像
bash ops/scripts/docker/build-apisix.sh

# 方式一：使用 docker compose（推荐）
docker compose -f docker-compose-local.yml up -d

# 方式二：手动启动
docker run -d \
  --name zqzl-apisix-local \
  -p 8080:9080 \
  --add-host=host.docker.internal:host-gateway \
  -v "$(pwd)/ops/docker/apisix/config-local.yaml:/usr/local/apisix/conf/config.yaml:ro" \
  -v "$(pwd)/ops/docker/apisix/apisix-local.yaml:/usr/local/apisix/conf/apisix.yaml:ro" \
  --restart unless-stopped \
  zqzl/apisix-gateway:latest
```

**关键说明**：
- `host.docker.internal` 是 Docker 内置的特殊 DNS 域名，指向宿主机
- `--add-host=host.docker.internal:host-gateway` 确保 Linux 系统也能正常解析
- 配置文件通过 volume 挂载，修改后重启容器即可生效

### 3.6 访问地址（本地开发环境）

**注意：需先配置 hosts 文件**

| 服务 | 地址 | 说明 |
|------|------|------|
| APISIX 网关统一入口 | http://sso.local.bw.com:8080 | 推荐，通过网关访问 |
| 登录门户（网关） | http://sso.local.bw.com:8080 | SSO 认证前端 |
| 管理平台（网关） | http://admin.local.bw.com:8080 | 用户管理后台 |
| API 网关 | http://api.local.bw.com:8080 | API 统一入口 |
| 登录门户（直连） | http://sso.local.bw.com:3001 | SSO 认证前端 |
| 管理平台（直连） | http://admin.local.bw.com:3002 | 用户管理后台 |
| SSO API | http://api.local.bw.com:8081/sso/v1/auth | 认证服务 API |
| User API | http://api.local.bw.com:8082/v1/users | 用户服务 API |

### 3.7 默认测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ADMIN（管理员） |

---

## 四、集成环境（Docker Compose）部署

### 4.1 配置 hosts（本地集成环境测试）

在 `/etc/hosts` 中添加：

```
127.0.0.1 sso.bw.com admin.bw.com api.bw.com
```

### 4.2 构建所有服务镜像

Docker 构建脚本统一放在 `ops/scripts/docker/` 目录下，层级清晰。

```bash
# 方式一：一键构建所有镜像（含 APISIX）
bash ops/scripts/docker/build-all.sh

# 方式二：分类构建
# 构建 APISIX 网关
bash ops/scripts/docker/build-apisix.sh

# 构建后端服务
bash ops/scripts/docker/build-backend.sh

# 构建前端应用
bash ops/scripts/docker/build-frontend.sh

# 方式三：分别构建单个服务
# 构建后端服务
bash backend/services/sso-server/deploy/build.sh
bash backend/services/user-server/deploy/build.sh

# 构建前端应用
bash frontend/apps/sso-web/deploy/build.sh
bash frontend/apps/user-web/deploy/build.sh
```

**脚本目录结构说明**：
- `build-all.sh`: 总入口，按顺序调用其他构建脚本
- `build-apisix.sh`: 只构建 APISIX 网关镜像（独立模块）
- `build-backend.sh`: 只构建后端服务镜像
- `build-frontend.sh`: 只构建前端应用镜像

> **设计原因**：
> 1. `build-all.sh` 与 `build-apisix.sh` **不是同级关系**，前者是聚合脚本，后者是原子脚本
> 2. 拆分后可以按需构建，比如只改了前端就只运行 `build-frontend.sh`
> 3. 分类后更清晰，符合"单一职责原则"

### 4.3 启动所有服务

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

### 4.4 访问地址（集成环境）

**注意：需先配置 hosts 文件**

| 服务 | 地址 | 说明 |
|------|------|------|
| 登录门户 | http://sso.bw.com | SSO 认证前端（通过 APISIX） |
| 管理平台 | http://admin.bw.com | 用户管理后台（通过 APISIX） |
| SSO API | http://api.bw.com/v1/auth | 认证服务 API（通过 APISIX） |
| User API | http://api.bw.com/v1/users | 用户服务 API（通过 APISIX） |

---

## 五、单个服务构建说明

### 5.1 后端服务构建

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

### 5.2 前端应用构建

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

### 5.3 APISIX 网关构建

```bash
# 方式一：使用脚本
bash ops/scripts/docker/build-apisix.sh

# 方式二：手动构建
cd ops/docker/apisix
docker build -t zqzl/apisix-gateway:latest .
```

**APISIX 镜像使用说明**：

本镜像支持通过环境变量或挂载方式自定义配置：

| 方式 | 说明 | 示例 |
|------|------|------|
| 挂载配置文件 | 推荐，修改配置无需重建镜像 | `-v /path/config.yaml:/usr/local/apisix/conf/config.yaml` |
| 环境变量指定 | 通过 entrypoint 自动复制 | `-e APISIX_CUSTOM_CONFIG=/mount/path/config.yaml` |

**本地环境启动示例**：

```bash
docker run -d \
  --name zqzl-apisix-local \
  --network host \
  -v "$(pwd)/ops/docker/apisix/config-local.yaml:/usr/local/apisix/conf/config.yaml:ro" \
  -v "$(pwd)/ops/docker/apisix/apisix-local.yaml:/usr/local/apisix/conf/apisix.yaml:ro" \
  zqzl/apisix-gateway:latest
```

---

## 六、环境变量说明

### 6.1 sso-server 环境变量（必须显式配置，无默认值）

| 变量名 | 说明 | 集成环境配置示例 |
|--------|------|--------------|
| `SPRING_PROFILES_ACTIVE` | Spring Profile | prod |
| `SERVER_PORT` | HTTP 端口 | 8080 |
| `GRPC_SERVER_PORT` | gRPC 端口 | 9090 |
| `USER_GRPC_HOST` | user-server gRPC 地址 | user-server |
| `USER_GRPC_PORT` | user-server gRPC 端口 | 9090 |
| `SSO_ISSUER_URI` | OAuth2 Issuer URI | http://api.bw.com |
| `SSO_JWK_SET_URI` | JWK 公钥地址 | http://sso-server:8080/oauth2/jwks |
| `SSO_WEB_LOGIN_URL` | SSO 登录页地址 | http://sso.bw.com/login |
| `SSO_WEB_LOGOUT_URL` | SSO 登出跳转地址 | http://sso.bw.com/login?logout |
| `SSO_CLIENT_USER_WEB_REDIRECT_URI` | user-web 回调地址 | http://admin.bw.com/sso/callback |

### 6.2 user-server 环境变量（必须显式配置，无默认值）

| 变量名 | 说明 | 集成环境配置示例 |
|--------|------|--------------|
| `SPRING_PROFILES_ACTIVE` | Spring Profile | prod |
| `SERVER_PORT` | HTTP 端口 | 8080 |
| `GRPC_SERVER_PORT` | gRPC 端口 | 9090 |
| `SSO_GRPC_HOST` | sso-server gRPC 地址 | sso-server |
| `SSO_GRPC_PORT` | sso-server gRPC 端口 | 9090 |
| `SSO_ISSUER_URI` | JWT Issuer URI | http://api.bw.com |
| `SSO_JWK_SET_URI` | JWK 公钥地址 | http://sso-server:8080/oauth2/jwks |
| `SSO_TOKEN_URI` | OAuth2 Token 地址 | http://api.bw.com/oauth2/token |
| `SSO_AUTHORIZATION_URI` | OAuth2 授权地址 | http://api.bw.com/oauth2/authorize |
| `SSO_REDIRECT_URI` | OAuth2 回调地址 | http://admin.bw.com/sso/callback |
| `SSO_OAUTH2_SCOPE` | OAuth2 权限范围 | openid profile read write |

---

## 七、常见问题排查

### 7.1 脚本执行权限问题

**问题**：`Permission denied` 或 `sh: permission denied`

**解决方案**：
```bash
# 给所有脚本添加执行权限
find . -name "*.sh" -type f -exec chmod +x {} \;

# 或单独给某个脚本
chmod +x ops/scripts/docker/build-apisix.sh
```

### 7.2 gRPC 连接失败

**问题**：`UNAVAILABLE: Connection refused`

**排查步骤**：
1. 确认目标服务是否启动
2. 检查 gRPC 端口是否正确（local: 9091/9092, 集成环境: 9090）
3. 查看配置文件中 gRPC 地址是否正确
4. 确认 Docker 网络是否正常

### 7.3 前端代理不生效

**问题**：开发环境接口调用失败

**排查步骤**：
1. 确认 `setupProxy.js` 配置正确
2. 确认后端服务已启动
3. 检查端口是否被占用（8081/8082）

### 7.4 Docker 构建失败

**问题**：`COPY failed: file not found`

**排查步骤**：
1. 确认在正确目录执行构建
2. 检查 Dockerfile 中的 COPY 路径
3. 确认源码文件存在

### 7.5 端口占用问题

**问题**：端口被占用导致服务无法启动

**解决方案**：
```bash
# 检查所有服务端口
lsof -ti :8080 -ti :8081 -ti :8082 -ti :9091 -ti :9092 -ti :3001 -ti :3002

# 杀死占用端口的进程
kill $(lsof -ti :8081 -ti :8082 -ti :9091 -ti :9092 -ti :3001 -ti :3002)
```

### 7.6 APISIX 网关启动问题

**问题 1**：`sh: /usr/local/apisix/bin/apisix: not found`

**原因**：基础镜像路径问题

**解决方案**：Dockerfile 中使用正确的 CMD 命令

**问题 2**：`ERROR: Admin API can only be used with etcd config_center`

**原因**：`config.yaml` 中设置了 `enable_admin: true`，但当前配置使用 standalone 模式

**解决方案**：将 `enable_admin` 设为 `false`

**问题 3**：本地环境 APISIX 无法连接本地服务

**原因**：Docker 容器内的 `localhost` 指向容器本身，而非宿主机

**解决方案**：
- 使用 `--network host` 网络模式（推荐，已在脚本中配置）
- 或使用 `host.docker.internal` 作为主机名（需 Docker Desktop 支持）

---

## 八、服务维护

### 8.1 查看服务状态

```bash
# Docker Compose 方式（集成环境）
docker compose ps

# 查看日志
docker compose logs -f apisix
docker compose logs -f sso-server
docker compose logs -f user-server
```

### 8.2 重启单个服务

```bash
# 重启 sso-server
docker compose restart sso-server

# 重启 user-server
docker compose restart user-server
```

### 8.3 重新构建并启动

```bash
# 重新构建 sso-server 镜像
bash backend/services/sso-server/deploy/build.sh

# 重启服务
docker compose up -d --force-recreate sso-server
```

---

## 九、版本历史

| 版本 | 日期 | 更新内容 | 更新人 |
|------|------|----------|--------|
| v7.2 | 2026-05-24 | APISIX环境变量注入：一套配置模板+entrypoint变量替换，7个环境变量控制差异化配置；启动脚本保留网关域名访问信息 | 系统 |
| v7.1 | 2026-05-24 | APISIX纯净镜像重构：配置完全不打包，统一挂载；新增docker-compose-local.yml；解决Mac Docker网络兼容性 | 系统 |
| v7.0 | 2026-05-24 | 环境命名统一：Docker环境→集成环境；本地域名统一使用*.local.bw.com；APISIX镜像统一，配置通过挂载区分 | 系统 |
| v6.0 | 2026-05-24 | 环境统一升级：JDK 17 + Node 22，脚本目录重构，依赖版本优化 | 系统 |
| v5.0 | 2026-05-23 | 配置规范统一：local 环境命名、端口规范、域名规范 | 系统 |
| v4.0 | 2026-05-21 | 架构重构：APISIX 网关 + gRPC 服务间调用 | 系统 |
| v3.0 | 2026-05-19 | 域名架构升级，三域名分离 | 系统 |
| v2.0 | 2026-05-19 | Docker 容器化部署架构设计 | 系统 |
| v1.0 | 2026-05-19 | 初始版本 | 系统 |

---

## 十、v7.0 版本更新说明

### 10.1 环境命名统一

**原命名**：
- 本地开发环境
- Docker 环境

**新命名**：
- 本地开发环境（不变）
- 集成环境（原 Docker 环境）

> **原因**：避免"本地 Docker 环境"与"集成环境"产生歧义，明确区分"本机直接运行"与"Docker Compose 集成部署"两种模式。

### 10.2 本地域名统一

**原配置**：
- 使用 `localhost` + 端口访问

**新配置**：
- 统一使用 `*.local.bw.com` 域名
- sso.local.bw.com - 登录门户
- admin.local.bw.com - 管理平台
- api.local.bw.com - API 网关

**Hosts 配置**：
```
127.0.0.1 sso.local.bw.com admin.local.bw.com api.local.bw.com
```

### 10.3 APISIX 环境变量注入（v7.2）

**原方案**：
- 两套配置目录（integration/local）
- docker compose 挂载不同目录
- 配置值硬编码在文件中

**新方案**：
- 一套配置模板 `apisix.yaml.template`（含 `${VAR}` 占位符）
- 容器启动时通过 `docker-entrypoint.sh` 替换环境变量
- 7个环境变量控制所有差异化配置
- 路由ID统一固定（如 `api-user-server-route`）

**环境变量列表**：
| 变量名 | 说明 | 集成环境值 | 本地环境值 |
|--------|------|----------|----------|
| `API_HOST` | API 网关域名 | api.bw.com | api.local.bw.com |
| `SSO_WEB_HOST` | SSO 登录门户域名 | sso.bw.com | sso.local.bw.com |
| `ADMIN_WEB_HOST` | 管理平台域名 | admin.bw.com | admin.local.bw.com |
| `SSO_SERVER_UPSTREAM` | SSO 服务上游地址 | sso-server:8080 | host.docker.internal:8081 |
| `USER_SERVER_UPSTREAM` | 用户服务上游地址 | user-server:8080 | host.docker.internal:8082 |
| `SSO_WEB_UPSTREAM` | SSO 前端上游地址 | sso-web:80 | host.docker.internal:3001 |
| `USER_WEB_UPSTREAM` | 用户前端上游地址 | user-web:80 | host.docker.internal:3002 |

**配置文件目录**：
```
ops/docker/apisix/
├── Dockerfile              # 镜像构建文件
├── apisix.yaml.template    # 路由配置模板（含环境变量占位符）
├── config.yaml             # APISIX基础配置（固定）
└── docker-entrypoint.sh    # 启动时变量替换脚本
```

**优势**：
1. ✅ 一套模板，无需维护多套配置文件
2. ✅ 环境变量清晰可查，便于调试
3. ✅ 修改配置只需改 docker compose 的 environment 即可
4. ✅ 路由 ID 统一，避免歧义
5. ✅ 支持动态扩展新的环境变量

### 10.4 启动脚本打印信息统一

**原方案**：
- 后端服务显示 `http://localhost:8082`
- 前端服务显示 `http://sso.local.bw.com:3001`
- 格式不统一

**新方案**：
- 后端/前端服务统一只显示端口号（如 `端口: 8082`）
- **网关启动和状态显示保留完整域名访问地址**
- 使用者清楚知道配置 hosts 和访问入口

### 10.5 配置文件更新

**后端 application-local.yml**：
- `sso.oauth2.issuer-uri`: http://api.local.bw.com
- `sso.web.login-url`: http://sso.local.bw.com/login
- `sso.web.logout-success-url`: http://sso.local.bw.com/login?logout
- `sso.client.user-web-redirect-uri`: http://admin.local.bw.com/sso/callback

**前端 .env.local**：
- `REACT_APP_SSO_SERVER_URL`: http://api.local.bw.com
- `REACT_APP_API_SERVER_URL`: http://api.local.bw.com
- `REACT_APP_SSO_AUTH_URL`: http://api.local.bw.com/oauth2/authorize
- `REACT_APP_SSO_TOKEN_URL`: http://api.local.bw.com/oauth2/token
