# 部署指南

## 一、架构概述

### 1.1 核心设计理念

**两套环境，一套镜像，通用脚本，层次清晰**

| 环境类型 | 说明 | 网关 | 前端 | 后端 |
|---------|------|------|------|------|
| **集成环境** | 多厂商/多环境部署，全部服务 Docker 化，通过不同 `.env` 配置文件部署到不同环境 | Docker 镜像 | Docker 镜像 | Docker 镜像 |
| **本地开发环境** | 开发调试，仅网关 Docker 化，其他服务本地启动 | Docker 镜像 | 本地 `npm start` | 本地 `java -jar` |

> **核心原则**：
> - **集成环境**：通过配置文件 + 环境变量组合的方式，适用于多地部署
> - **本地环境**：直接使用各项目类型规范的本地配置，不需要变量能覆盖就覆盖
> - **通用脚本**：脚本能通用的做成通用的，用入参的方式替代差异
> - **层次清晰**：层级环境配置层次分明，易于维护
> - **Dockerfile 固化**：Dockerfile 是项目编码的一部分，固化的路径/名称不需要提炼为占位符
> - **本地启动固化**：本地模式固定使用 `local` profile，不允许其他值，作为项目开发规范

### 1.2 项目组成

本项目由三大块、5个子项目组成：

| 模块 | 子项目 | 说明 |
|------|--------|------|
| 网关层 | APISIX Gateway | API 网关，统一入口 |
| 后端服务 | SSO Server | 单点登录认证服务 |
| 后端服务 | User Server | 用户中心服务 |
| 前端应用 | SSO Web | 登录门户前端 |
| 前端应用 | User Web | 管理平台前端 |

---

## 二、目录结构规范

### 2.1 整体目录结构

```
zqzl-traecn/
├── docker-compose.yml                      # 集成环境 Compose 总配置（include 子服务）
├── docker-compose-local.yml                # 本地环境 Compose 配置（仅网关）
├── backend/
│   ├── frameworks/
│   │   └── zqzl-framework/
│   └── services/
│       ├── sso-server/
│       │   ├── deploy/
│       │   │   ├── Dockerfile              # 统一格式，支持构建参数
│       │   │   └── start.sh                # 单体启动脚本（本地和 Docker 共用）
│       │   └── src/main/resources/
│       │       ├── application.yml         # 基础配置（占位符）
│       │       └── application-local.yml   # 本地配置（硬编码，适配 Spring Boot 规范）
│       └── user-server/
│           └── deploy/                     # 同上，结构一致
├── frontend/
│   └── apps/
│       ├── sso-web/
│       │   ├── deploy/
│       │   │   ├── Dockerfile
│       │   │   ├── docker-entrypoint.sh    # 容器内环境变量替换
│       │   │   ├── start.sh                # 单体启动脚本（本地和 Docker 共用）
│       │   │   └── nginx.conf
│       │   ├── public/
│       │   │   └── env-config.js.template   # 前端配置模板
│       │   ├── .env                        # 构建时环境变量
│       │   └── .env.local                  # 本地环境变量（硬编码）
│       └── user-web/
│           └── deploy/                     # 同上，结构一致
├── ops/
│   ├── env/                                # 环境配置目录（层次清晰）
│   │   ├── integration/                    # 集成环境配置（多厂商）
│   │   │   └── .env.default                # 默认配置（可复制为其他环境）
│   │   └── local/                          # 本地环境配置
│   │       └── .env.apisix                 # 本地网关配置
│   ├── docker/
│   │   ├── apisix/
│   │   │   ├── Dockerfile
│   │   │   ├── docker-entrypoint.sh
│   │   │   ├── start.sh                    # 网关启动脚本（本地和 Docker 共用）
│   │   │   ├── config.yaml                 # APISIX 基础配置
│   │   │   └── apisix.yaml.template        # 路由配置模板
│   │   └── compose/                        # Docker Compose 服务配置目录
│   │       └── services/
│   │           ├── network.yml             # 网络配置
│   │           ├── apisix.yml              # APISIX 网关配置
│   │           ├── sso-server.yml          # SSO 服务配置
│   │           ├── user-server.yml         # User 服务配置
│   │           ├── sso-web.yml             # SSO 前端配置
│   │           └── user-web.yml            # User 前端配置
│   └── scripts/
│       ├── docker/
│       │   ├── build-all.sh                # 总构建脚本（调用 build-service.sh）
│       │   ├── build-service.sh            # 单体通用构建脚本（通过入参构建不同服务）
│       │   └── start.sh                    # Docker 启动总脚本（支持多环境多厂商）
│       └── local/
│           ├── build.sh                    # 本地编译总脚本
│           └── start.sh                    # 本地启动总脚本（调用各项目 start.sh）
└── docs/
    └── DEPLOYMENT.md                       # 本文档
```

### 2.2 设计原则

1. **集成环境配置化**：集成环境所有配置通过 `.env` 文件注入，一套镜像多环境部署
2. **本地配置规范化**：本地环境遵循各项目类型规范，使用硬编码的本地配置
3. **通用脚本参数化**：脚本通用化，通过入参方式解决差异，不写多份脚本
4. **Docker Compose 分层化**：总 Compose 文件使用 `include` 方式引用子服务配置
5. **层次结构清晰化**：环境配置按类型分层，目录结构一目了然
6. **单体启动脚本**：各项目保留自己的 start.sh，本地调试和 Docker 启动复用

---

## 三、集成环境部署（多厂商）

### 3.1 环境配置体系

集成环境通过不同的 `.env` 配置文件支持多厂商/多环境部署：

| 配置文件 | 用途 | 说明 |
|---------|------|------|
| `ops/env/integration/.env.default` | 默认集成环境 | 可复制作为其他环境的基础 |
| `ops/env/integration/.env.<name>` | 自定义环境 | 根据部署需求创建 |

> **添加新环境**：复制 `.env.default` 为 `.env.<名称>`，修改其中的配置即可

### 3.2 配置 Hosts

根据部署的环境配置对应的 hosts：

```bash
# 默认环境（示例）
echo "127.0.0.1 sso.bw.com admin.bw.com api.bw.com" | sudo tee -a /etc/hosts
```

### 3.3 一键构建所有镜像

```bash
# 构建所有服务镜像（通用脚本，通过入参构建）
bash ops/scripts/docker/build-service.sh all

# 自定义镜像标签和前缀
bash ops/scripts/docker/build-service.sh all v1.0.0 mycompany

# 仅构建单个服务
bash ops/scripts/docker/build-service.sh apisix
bash ops/scripts/docker/build-service.sh sso-server
bash ops/scripts/docker/build-service.sh user-server
bash ops/scripts/docker/build-service.sh sso-web
bash ops/scripts/docker/build-service.sh user-web

# 按类型构建
bash ops/scripts/docker/build-service.sh backend    # 所有后端
bash ops/scripts/docker/build-service.sh frontend   # 所有前端
```

### 3.4 一键启动所有服务

```bash
# 启动默认集成环境（后台）
bash ops/scripts/docker/start.sh integration default up

# 启动自定义环境（前台，日志直接输出）
bash ops/scripts/docker/start.sh integration <env-name> up --abort-on-container-exit
```

### 3.5 常用操作

```bash
# 查看服务状态
bash ops/scripts/docker/start.sh integration default status

# 查看日志
bash ops/scripts/docker/start.sh integration default logs           # 所有服务日志
bash ops/scripts/docker/start.sh integration default logs apisix    # 单个服务日志

# 停止服务
bash ops/scripts/docker/start.sh integration <env-name> down

# 重启服务
bash ops/scripts/docker/start.sh integration <env-name> restart

# 构建镜像
bash ops/scripts/docker/start.sh integration default build
```

### 3.6 访问地址

根据部署的环境配置中的域名访问：

| 服务 | 说明 |
|------|------|
| 登录门户 | 根据 .env 配置的 `SSO_WEB_HOST` 访问 |
| 管理平台 | 根据 .env 配置的 `ADMIN_WEB_HOST` 访问 |
| API 网关 | 根据 .env 配置的 `API_HOST` 访问 |

---

## 四、本地开发环境部署

### 4.1 环境配置说明

本地开发环境遵循各项目类型规范：

- **Spring Boot 后端**：使用 `--spring.profiles.active=local` 加载 `application-local.yml`（硬编码）
- **React 前端**：自动加载 `.env.local` 配置（硬编码）
- **APISIX 网关**：使用 Docker，通过 `host.docker.internal` 代理到本机服务

### 4.2 配置 Hosts

```bash
echo "127.0.0.1 sso.local.bw.com admin.local.bw.com api.local.bw.com" | sudo tee -a /etc/hosts
```

### 4.3 一键编译所有项目

```bash
# 编译所有模块（含 APISIX 镜像）
bash ops/scripts/local/build.sh

# 仅编译框架层
bash ops/scripts/local/build.sh framework

# 仅编译后端
bash ops/scripts/local/build.sh backend

# 仅编译前端
bash ops/scripts/local/build.sh frontend

# 仅编译单个服务
bash ops/scripts/local/build.sh sso-server
bash ops/scripts/local/build.sh apisix
```

### 4.4 一键启动所有服务

**默认前台启动**（推荐，日志直接输出到当前终端）：

```bash
# 前台启动所有服务（最后一个服务占据前台，Ctrl+C 停止）
bash ops/scripts/local/start.sh

# 前台启动 APISIX 网关
bash ops/scripts/local/start.sh start apisix

# 前台启动后端服务
bash ops/scripts/local/start.sh start backend

# 前台启动前端应用
bash ops/scripts/local/start.sh start frontend
```

**后台启动**（如需同时查看多个日志）：

```bash
# 后台启动所有服务
bash ops/scripts/local/start.sh start all false

# 查看日志
tail -f logs/local/user-server.log
tail -f logs/local/sso-server.log
tail -f logs/local/sso-web.log
tail -f logs/local/user-web.log
```

### 4.5 单独启动某个项目

也可以直接使用各项目自己的启动脚本：

```bash
# 启动 sso-server
bash backend/services/sso-server/deploy/start.sh local local

# 启动 user-server
bash backend/services/user-server/deploy/start.sh local local

# 启动 sso-web
bash frontend/apps/sso-web/deploy/start.sh local local

# 启动 user-web
bash frontend/apps/user-web/deploy/start.sh local local

# 启动 apisix
bash ops/docker/apisix/start.sh local local
```

### 4.6 常用操作

```bash
# 查看服务状态
bash ops/scripts/local/start.sh status

# 停止所有服务
bash ops/scripts/local/start.sh stop

# 重启所有服务
bash ops/scripts/local/start.sh restart

# 启动单个服务
bash ops/scripts/local/start.sh start user-server
bash ops/scripts/local/start.sh start sso-web
```

### 4.7 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 登录门户（网关） | http://sso.local.bw.com:8080 | 通过 APISIX 网关访问 |
| 管理平台（网关） | http://admin.local.bw.com:8080 | 通过 APISIX 网关访问 |
| API 网关 | http://api.local.bw.com:8080 | APISIX 网关入口 |
| 登录门户（直连） | http://sso.local.bw.com:3001 | 直接访问前端开发服务器 |
| 管理平台（直连） | http://admin.local.bw.com:3002 | 直接访问前端开发服务器 |
| SSO API（直连） | http://localhost:8080 | 直接访问后端服务 |
| User API（直连） | http://localhost:8081 | 直接访问后端服务 |

---

## 五、Dockerfile 规范

### 5.1 后端项目 Dockerfile

所有后端项目 Dockerfile 格式统一，使用 `ARG` 定义构建参数：

```dockerfile
ARG BUILDER_IMAGE=maven:3.9.6-eclipse-temurin-17
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre
ARG APP_NAME=sso-server
ARG APP_VERSION=1.0.0
ARG WORKDIR=/app

FROM ${BUILDER_IMAGE} AS builder
ARG APP_NAME
ARG WORKDIR
WORKDIR ${WORKDIR}

# ... 构建步骤 ...

FROM ${RUNTIME_IMAGE}
ARG APP_NAME
ARG APP_VERSION
ARG WORKDIR
WORKDIR ${WORKDIR}

COPY --from=builder ${WORKDIR}/services/${APP_NAME}/target/${APP_NAME}-${APP_VERSION}.jar app.jar
COPY backend/services/${APP_NAME}/deploy/start.sh /usr/local/bin/start.sh

RUN chmod +x /usr/local/bin/start.sh

EXPOSE 8080 9090

ENTRYPOINT ["start.sh"]
CMD ["prod", "docker"]
```

### 5.2 前端项目 Dockerfile

```dockerfile
ARG BUILDER_IMAGE=node:22-alpine
ARG RUNTIME_IMAGE=nginx:alpine

FROM ${BUILDER_IMAGE} AS builder
WORKDIR /app

COPY frontend/apps/sso-web/package*.json ./
RUN npm install

COPY frontend/apps/sso-web .
RUN npm run build

FROM ${RUNTIME_IMAGE}
WORKDIR /usr/share/nginx/html

COPY --from=builder /app/build .
COPY frontend/apps/sso-web/deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY frontend/apps/sso-web/deploy/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY frontend/apps/sso-web/deploy/start.sh /usr/local/bin/start.sh

RUN chmod +x /usr/local/bin/docker-entrypoint.sh \
    && chmod +x /usr/local/bin/start.sh

EXPOSE 80

ENTRYPOINT ["start.sh"]
CMD ["docker"]
```

### 5.3 构建参数说明

| 参数 | 说明 |
|------|------|
| `BUILDER_IMAGE` | 构建阶段基础镜像 |
| `RUNTIME_IMAGE` | 运行阶段基础镜像 |
| `APP_NAME` | 应用名称 |
| `APP_VERSION` | 应用版本 |
| `WORKDIR` | 工作目录 |

---

## 六、配置文件规范

### 6.1 后端配置

| 文件 | 用途 | 配置方式 |
|------|------|---------|
| `application.yml` | 基础配置 | 占位符 `${VAR}`，集成环境通过环境变量注入 |
| `application-local.yml` | 本地开发配置 | 硬编码，遵循 Spring Boot 规范 |

**示例（application-local.yml）**：
```yaml
server:
  port: 8080

spring:
  jpa:
    show-sql: true
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://sso.local.bw.com:8080
          jwk-set-uri: http://localhost:8080/oauth2/jwks

grpc:
  server:
    port: 9090
  client:
    user-server:
      address: static://localhost:9091

logging:
  level:
    com.sso: DEBUG
```

### 6.2 前端配置

| 文件 | 用途 | 配置方式 |
|------|------|---------|
| `.env` | 构建时环境变量 | 占位符，集成环境通过环境变量注入 |
| `.env.local` | 本地开发环境变量 | 硬编码，遵循 React 规范 |
| `env-config.js.template` | 运行时配置模板 | 容器启动时替换占位符 |

**示例（.env.local）**：
```
REACT_APP_SSO_SERVER_URL=http://sso.local.bw.com:8080
REACT_APP_API_SERVER_URL=http://api.local.bw.com:8080
REACT_APP_USER_WEB_URL=http://admin.local.bw.com:3002
PORT=3001
```

### 6.3 APISIX 配置

| 文件 | 用途 | 配置方式 |
|------|------|---------|
| `config.yaml` | APISIX 基础配置 | 固定配置 |
| `apisix.yaml.template` | 路由配置模板 | 占位符，启动时通过 `docker-entrypoint.sh` 替换 |
| `ops/env/local/.env.apisix` | 本地网关环境 | 网关配置，使用 `host.docker.internal` 代理到本机 |

**本地网关配置示例（.env.apisix）**：
```
APISIX_HTTP_PORT=8080
APISIX_HTTPS_PORT=8443

API_HOST=api.local.bw.com
SSO_WEB_HOST=sso.local.bw.com
ADMIN_WEB_HOST=admin.local.bw.com

# 使用 host.docker.internal 代理到本机
SSO_SERVER_UPSTREAM=host.docker.internal:8080
USER_SERVER_UPSTREAM=host.docker.internal:8081
SSO_WEB_UPSTREAM=host.docker.internal:3001
USER_WEB_UPSTREAM=host.docker.internal:3002
```

---

## 七、脚本总览

### 7.1 各项目单体启动脚本

每个项目都有自己的 `start.sh`，支持本地和 Docker 两种模式：

| 项目 | 脚本路径 | 说明 |
|------|---------|------|
| sso-server | `backend/services/sso-server/deploy/start.sh` | 后端服务启动 |
| user-server | `backend/services/user-server/deploy/start.sh` | 后端服务启动 |
| sso-web | `frontend/apps/sso-web/deploy/start.sh` | 前端应用启动 |
| user-web | `frontend/apps/user-web/deploy/start.sh` | 前端应用启动 |
| apisix | `ops/docker/apisix/start.sh` | 网关启动 |

**用法**：
```bash
# 本地启动（固定使用 local profile）
bash <script-path> local

# Docker 启动（容器内，默认使用 SPRING_PROFILES_ACTIVE 环境变量）
start.sh docker
```

### 7.2 Docker 脚本

| 脚本 | 用途 | 示例 |
|------|------|------|
| `ops/scripts/docker/build-all.sh` | 构建所有镜像（入口） | `build-all.sh [tag] [prefix]` |
| `ops/scripts/docker/build-service.sh` | 通用单体构建脚本（通过入参构建不同服务） | `build-service.sh <service> [tag] [prefix]` |
| `ops/scripts/docker/start.sh` | Docker 服务管理（支持多环境多厂商） | `start.sh <env-type> <env-config> [action] [options]` |

**build-service.sh 服务参数**：
```
all          - 构建所有服务
apisix       - 构建 APISIX 网关
backend      - 构建所有后端服务
frontend     - 构建所有前端服务
sso-server   - 构建 SSO 后端服务
user-server  - 构建 User 后端服务
sso-web      - 构建 SSO 前端应用
user-web     - 构建 User 前端应用
```

### 7.3 本地脚本

| 脚本 | 用途 | 示例 |
|------|------|------|
| `ops/scripts/local/build.sh` | 本地编译 | `build.sh [module]` |
| `ops/scripts/local/start.sh` | 本地服务管理（调用各项目 start.sh） | `start.sh [action] [module] [foreground]` |

---

## 八、Docker Compose 分层设计

### 8.1 目录结构

```
ops/docker/compose/
└── services/
    ├── network.yml          # 网络配置（公用）
    ├── apisix.yml           # APISIX 网关配置
    ├── sso-server.yml       # SSO 服务配置
    ├── user-server.yml      # User 服务配置
    ├── sso-web.yml          # SSO 前端配置
    └── user-web.yml         # User 前端配置
```

### 8.2 根目录 Compose 文件

**集成环境（docker-compose.yml）**：
```yaml
include:
  - path: ./ops/docker/compose/services/network.yml
  - path: ./ops/docker/compose/services/user-server.yml
  - path: ./ops/docker/compose/services/sso-server.yml
  - path: ./ops/docker/compose/services/sso-web.yml
  - path: ./ops/docker/compose/services/user-web.yml
  - path: ./ops/docker/compose/services/apisix.yml
```

**本地环境（docker-compose-local.yml）**：
```yaml
include:
  - path: ./ops/docker/compose/services/network.yml

services:
  apisix:
    extends:
      file: ./ops/docker/compose/services/apisix.yml
      service: apisix
    container_name: ${PROJECT_NAME}-apisix-local
    depends_on: []
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

### 8.3 子服务 Compose 配置

每个子服务的 compose 文件只包含该服务的配置，全部使用占位符：

```yaml
services:
  sso-server:
    image: ${SSO_SERVER_IMAGE}
    container_name: ${PROJECT_NAME}-sso-server
    environment:
      - SPRING_PROFILES_ACTIVE=${SSO_SERVER_PROFILE}
      - SERVER_PORT=${SSO_SERVER_PORT}
      # ... 其他环境变量
    networks:
      - ${NETWORK_NAME}
    restart: unless-stopped
```

---

## 九、快速参考

### 9.1 常用命令速查

| 操作 | 集成环境 | 本地环境 |
|------|---------|---------|
| 构建镜像 | `bash ops/scripts/docker/build-service.sh all` | `bash ops/scripts/local/build.sh` |
| 启动服务 | `bash ops/scripts/docker/start.sh integration default up` | `bash ops/scripts/local/start.sh` |
| 停止服务 | `bash ops/scripts/docker/start.sh integration default down` | `bash ops/scripts/local/start.sh stop` |
| 查看状态 | `bash ops/scripts/docker/start.sh integration default status` | `bash ops/scripts/local/start.sh status` |
| 查看日志 | `bash ops/scripts/docker/start.sh integration default logs` | `tail -f logs/local/*.log` |

### 9.2 多环境部署示例

```bash
# 添加新环境（示例：生产环境）
cp ops/env/integration/.env.default ops/env/integration/.env.prod
# 修改 .env.prod 中的域名、端口等配置
bash ops/scripts/docker/start.sh integration prod up
```

### 9.3 默认账号

```
用户名: admin
密码: admin123
```

---

## 十、版本历史

| 版本 | 日期 | 更新内容 |
|------|------|----------|
| v9.2 | 2026-05-26 | Dockerfile 固化路径不提取占位符；start.sh local 模式固定使用 local profile；修复本地网关网络配置问题 |
| v9.1 | 2026-05-26 | 调整：各项目保留 start.sh 便于本地调试，Docker 内启动也复用；修复前端编译问题；清理多余示例文件 |
| v9.0 | 2026-05-26 | 架构重构：两套环境概念明确，通用脚本参数化，Docker Compose 分层设计，本地配置规范化 |
| v8.0 | 2026-05-26 | 多环境部署架构重构：统一 Dockerfile、统一启动脚本、环境变量全量注入 |
| v7.2 | 2026-05-24 | APISIX 环境变量注入：一套配置模板+entrypoint 变量替换 |
| v7.1 | 2026-05-24 | APISIX 纯净镜像重构：配置完全不打包，统一挂载 |
| v7.0 | 2026-05-24 | 环境命名统一：Docker 环境→集成环境；本地域名统一使用 *.local.bw.com |
| v6.0 | 2026-05-24 | 环境统一升级：JDK 17 + Node 22，脚本目录重构 |
