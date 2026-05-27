# 项目问题分析与整改建议

**文档版本**: v1.0  
**创建日期**: 2026-05-27  
**分析范围**: 整体项目架构、部署模式、SSO OAuth流程

---

## 📋 目录

1. [项目架构概览](#一项目架构概览)
2. [问题1：集成模式前端启动失败](#二问题1集成模式前端启动失败)
3. [问题2：本地模式APISIX_IMAGE变量未设置](#三问题2本地模式apisix_image变量未设置)
4. [问题3：本地模式网关502错误](#四问题3本地模式网关502错误)
5. [问题4：SSO OAuth授权码流程问题](#五问题4sso-oauth授权码流程问题)
6. [两种模式互斥性分析](#六两种模式互斥性分析)
7. [整改优先级建议](#七整改优先级建议)
8. [代码引用索引](#八代码引用索引)

---

## 一、项目架构概览

### 1.1 服务清单

| 服务 | 说明 | 本地端口 | 集成模式端口 |
|------|------|---------|-------------|
| **APISIX 网关** | API网关，统一入口 | 8080 | 80/443 |
| **sso-server** | SSO单点登录服务（OAuth2授权服务器） | 8081 | 8080 |
| **user-server** | 用户中心服务（资源服务器） | 8082 | 8080 |
| **sso-web** | 登录门户前端 | 3001 | 80 |
| **user-web** | 用户管理前端 | 3002 | 80 |

### 1.2 部署模式

| 模式 | 说明 | 核心特点 |
|------|------|---------|
| **本地模式** | 开发调试用 | 仅网关Docker化，其他服务本地启动 |
| **集成模式** | 生产/测试部署 | 全部服务Docker化，支持多环境配置 |

---

## 二、问题1：集成模式前端启动失败

### 2.1 问题现象

集成模式启动 `user-web` 和 `sso-web` 服务时报错：
```
exec /usr/local/bin/start.sh: no such file or directory
```
服务一直处在启动中。

### 2.2 根本原因分析

#### 原因A：脚本解释器不兼容（主要原因）

**问题描述**：
- 前端 Dockerfile 使用 `nginx:alpine` 作为基础镜像
- Alpine Linux 默认不包含 `bash`，只有 `sh`
- 但 `start.sh` 第一行是 `#!/bin/bash`
- 导致系统找不到解释器，报错 "no such file or directory"

**涉及文件**：

| 项目 | Dockerfile | start.sh |
|------|------------|----------|
| sso-web | [sso-web/Dockerfile](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/sso-web/deploy/Dockerfile#L2) | [sso-web/start.sh](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/sso-web/deploy/start.sh#L1) |
| user-web | [user-web/Dockerfile](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/deploy/Dockerfile#L2) | [user-web/start.sh](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/deploy/start.sh#L1) |

**验证方法**：
```bash
# 进入容器检查
docker run --rm -it nginx:alpine which bash   # 无输出
docker run --rm -it nginx:alpine which sh     # /bin/sh
```

#### 原因B：同样问题可能存在于APISIX网关

APISIX 使用 `apache/apisix:2.15.0-alpine`，同样是 Alpine 基础镜像：
- [apisix/Dockerfile](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/ops/docker/apisix/Dockerfile#L1)
- [apisix/start.sh](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/ops/docker/apisix/start.sh#L1)

**注意**：后端服务使用 `eclipse-temurin:17-jre`（Ubuntu/Deian 基础），包含 bash，无此问题。

### 2.3 整改建议

**方案1：修改脚本解释器（推荐）**

将所有前端 start.sh 第一行改为：
```bash
#!/bin/sh
```

**影响文件**：
- `frontend/apps/sso-web/deploy/start.sh`
- `frontend/apps/user-web/deploy/start.sh`
- `ops/docker/apisix/start.sh`

**方案2：在 Dockerfile 中安装 bash**

在前端 Dockerfile 中添加：
```dockerfile
RUN apk add --no-cache bash
```

**方案对比**：

| 方案 | 改动量 | 兼容性 | 镜像大小 |
|------|--------|--------|----------|
| 方案1（改解释器） | 小 | 好 | 无变化 |
| 方案2（装bash） | 小 | 更好 | 增加 ~1MB |

**推荐方案1**，因为 start.sh 中使用的都是 sh 兼容语法，无需额外安装 bash。

---

## 三、问题2：本地模式APISIX_IMAGE变量未设置

### 3.1 问题现象

本地模式启动时报警告：
```
WARN[0000] The "APISIX_IMAGE" variable is not set. Defaulting to a blank string.
```

### 3.2 根本原因分析

#### 原因：环境文件未传递给 docker-compose

**问题代码位置**：
[ops/scripts/local/start.sh](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/ops/scripts/local/start.sh#L126) 第126行：

```bash
# 当前代码（有问题）
docker compose -f docker-compose-local.yml up -d apisix
```

缺少 `--env-file` 参数，导致 `.env.apisix` 中的变量未被加载。

**配置文件**：
- 环境变量定义：[ops/env/local/.env.apisix](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/ops/env/local/.env.apisix#L8)
  ```bash
  APISIX_IMAGE=zqzl/apisix-gateway:latest
  ```
- Compose 引用：[docker-compose-local.yml](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/docker-compose-local.yml#L8)
  ```yaml
  image: ${APISIX_IMAGE}
  ```

### 3.3 整改建议

修改 `ops/scripts/local/start.sh` 第126行，添加 `--env-file` 参数：

```bash
# 修改前
docker compose -f docker-compose-local.yml up -d apisix

# 修改后
docker compose --env-file "$ENV_FILE" -f docker-compose-local.yml up -d apisix
```

**注意**：需要确保 `ENV_FILE` 变量在调用时已正确设置。

---

## 四、问题3：本地模式网关502错误

### 4.1 问题现象

- 访问地址：`http://sso.local.bw.com:8080`（通过网关访问）
- 网关报 502 Bad Gateway
- 网关 access.log 和 error.log 没有任何日志
- Ping 域名 `sso.local.bw.com` 是通的

### 4.2 可能原因分析（按优先级排序）

#### 原因A：`host.docker.internal` 解析问题

**问题描述**：
- Docker for Mac/Windows 应该自动支持 `host.docker.internal`
- 但 Linux 需要特殊配置
- 虽然 [docker-compose-local.yml](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/docker-compose-local.yml#L24-L25) 已设置：
  ```yaml
  extra_hosts:
    - "host.docker.internal:host-gateway"
  ```
  但可能在某些环境下失效。

**验证方法**：
```bash
# 进入APISIX容器测试连通性
docker exec -it zqzl-apisix-local curl host.docker.internal:3001
docker exec -it zqzl-apisix-local curl host.docker.internal:8081
```

#### 原因B：本地服务监听地址问题

**问题描述**：
- 后端服务默认可能只监听 `127.0.0.1`
- Docker 容器无法通过 `host.docker.internal` 访问只监听 127.0.0.1 的服务

**检查方法**：
```bash
# 检查服务监听地址
lsof -Pi :8081 -sTCP:LISTEN
lsof -Pi :3001 -sTCP:LISTEN
```

**整改建议**：确保服务监听 `0.0.0.0` 而非 `127.0.0.1`。

#### 原因C：APISIX 路由配置问题

**问题描述**：
- 路由配置中的 `host` 匹配可能不正确
- `upstream` 地址格式可能有问题

**涉及文件**：
- 路由模板：[apisix.yaml.template](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/ops/docker/apisix/apisix.yaml.template)
- 入口脚本：[docker-entrypoint.sh](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/ops/docker/apisix/docker-entrypoint.sh)

**验证方法**：
```bash
# 检查APISIX配置是否正确生成
docker exec -it zqzl-apisix-local cat /usr/local/apisix/conf/apisix.yaml

# 检查环境变量是否正确传递
docker exec -it zqzl-apisix-local env | grep HOST
docker exec -it zqzl-apisix-local env | grep UPSTREAM
```

#### 原因D：日志路径配置问题

**问题描述**：
- APISIX 默认日志路径可能不在标准输出
- 导致 `docker logs` 看不到日志

**涉及文件**：
- APISIX 主配置：[config.yaml](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/ops/docker/apisix/config.yaml)

**验证方法**：
```bash
# 查看APISIX内部日志文件
docker exec -it zqzl-apisix-local cat /usr/local/apisix/logs/error.log
docker exec -it zqzl-apisix-local cat /usr/local/apisix/logs/access.log
```

### 4.3 排查步骤建议

```bash
# 步骤1：检查APISIX容器是否正常运行
docker ps | grep apisix

# 步骤2：检查APISIX配置
docker exec -it zqzl-apisix-local cat /usr/local/apisix/conf/apisix.yaml

# 步骤3：测试host.docker.internal连通性
docker exec -it zqzl-apisix-local curl -v host.docker.internal:3001

# 步骤4：查看APISIX错误日志
docker exec -it zqzl-apisix-local cat /usr/local/apisix/logs/error.log

# 步骤5：直接访问upstream验证服务
curl http://localhost:3001    # sso-web
curl http://localhost:8081    # sso-server
```

---

## 五、问题4：SSO OAuth授权码流程问题

### 5.1 配置不一致问题

| 问题 | 影响 | 涉及文件 | 错误配置 | 正确配置 |
|------|------|---------|---------|---------|
| **回调路径不匹配** | 登录后无法正确回调 | [application-local.yml](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/resources/application-local.yml#L34) | `/oauth2/callback` | 应与前端路由 `/sso/callback` 一致 |
| **Scope不一致** | 授权请求可能失败 | [.env.local](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/.env.local#L8) | `openid profile email` | 文档中是 `openid profile read write` |
| **JWK地址错误** | Token验证失败 | [application-local.yml](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/user-server/src/main/resources/application-local.yml#L18) | `localhost:8081` | 应该是sso-server的端口 |
| **sso-web登录路由缺失** | 重定向到登录页后404 | [sso-web/App.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/sso-web/src/App.js) | 无 `/login` 路由 | 需添加Login组件路由 |

### 5.2 安全功能缺失

#### 缺失1：State参数未验证

**问题描述**：
- [SsoCallback.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/src/components/SsoCallback.js#L13) 获取了state但未验证
- state 存储在 localStorage，但回调时没有比对

**安全风险**：CSRF攻击

**整改建议**：
```javascript
// 在 SsoCallback.js 中添加 state 验证
const storedState = localStorage.getItem('oauth_state');
if (state !== storedState) {
  setError('State参数不匹配，可能是CSRF攻击');
  return;
}
localStorage.removeItem('oauth_state');
```

#### 缺失2：PKCE未实现

**问题描述**：
- 前端SPA是公共客户端（Public Client），无法安全存储 client_secret
- 应该使用 PKCE（Proof Key for Code Exchange）增强安全性

**安全风险**：授权码拦截攻击

**整改建议**：
1. 前端生成 `code_verifier` 和 `code_challenge`
2. 授权请求时携带 `code_challenge` 和 `code_challenge_method=S256`
3. 令牌交换时携带 `code_verifier`
4. 后端配置支持 PKCE

#### 缺失3：Refresh Token自动刷新未实现

**问题描述**：
- [App.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/src/App.js#L27-L33) 401拦截器直接重定向登录
- 没有利用 refresh_token 自动刷新 access_token

**用户体验影响**：token过期需重新登录

**整改建议**：
```javascript
// 在响应拦截器中添加自动刷新逻辑
if (error.response?.status === 401) {
  const refreshToken = localStorage.getItem('refresh_token');
  if (refreshToken) {
    // 调用刷新token接口
    // 成功后重试原请求
    // 失败则重定向登录
  } else {
    localStorage.clear();
    redirectToSSO();
  }
}
```

#### 缺失4：Token吊销未实现

**问题描述**：
- 登出时只清除了localStorage和SSO session
- 未调用 `/oauth2/revoke` 端点吊销 token

**安全风险**：token可能被滥用

**整改建议**：登出时先调用吊销端点，再清除本地存储。

#### 缺失5：单点登出（SLO）未实现

**问题描述**：
- A应用登出时，B应用的session仍然有效
- 没有统一的会话管理和登出通知机制

**影响**：非真正的"单点登出"

### 5.3 流程设计问题

#### 问题1：登录表单提交方式混乱

**问题描述**：
- [Login.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/sso-web/src/components/Login.js#L97) 同时处理了AJAX和表单提交
- 逻辑复杂，异常处理分支多

**整改建议**：统一使用表单提交方式，简化逻辑。

#### 问题2：gRPC双轨制设计复杂

**问题描述**：
- 同时支持HTTP和gRPC两种Token交换方式
- 增加了维护成本

**整改建议**：建议统一使用HTTP标准协议，gRPC仅用于内部服务间调用。

---

## 六、两种模式互斥性分析

### 6.1 结论

**本地模式和集成模式不互斥，可以共存**。

### 6.2 详细分析

| 维度 | 本地模式 | 集成模式 | 是否冲突 |
|------|---------|---------|---------|
| **网关端口** | 8080 | 80/443 | ❌ 不冲突 |
| **服务端口** | 8081, 8082, 3001, 3002 | 容器内端口 | ❌ 不冲突 |
| **镜像构建** | 仅需APISIX | 需要全部5个 | ❌ 不冲突 |
| **环境配置** | `ops/env/local/.env.apisix` | `ops/env/integration/.env.*` | ❌ 不冲突 |
| **Compose文件** | `docker-compose-local.yml` | `docker-compose.yml` | ❌ 不冲突 |
| **容器名称** | `*-local` 后缀 | 标准名称 | ❌ 不冲突 |

### 6.3 注意事项

1. **不要同时启动两套模式的网关**：虽然端口不冲突，但会造成资源浪费
2. **镜像共用**：两套模式使用相同的镜像，只需构建一次
3. **hosts配置**：两套模式使用不同域名，需分别配置
   - 本地模式：`*.local.bw.com`
   - 集成模式：`*.bw.com`

---

## 七、整改优先级建议

| 优先级 | 问题 | 影响 | 预估工作量 | 责任人 |
|--------|------|------|-----------|--------|
| **P0** | 集成模式前端start.sh解释器问题 | 阻塞集成模式部署 | 1小时 | |
| **P0** | 本地模式docker-compose未加载.env文件 | 阻塞本地模式启动 | 0.5小时 | |
| **P1** | OAuth回调路径配置不一致 | 导致登录流程失败 | 0.5小时 | |
| **P1** | State参数未验证 | 安全漏洞 | 1小时 | |
| **P1** | sso-web登录路由缺失 | 登录页面无法显示 | 1小时 | |
| **P2** | PKCE未实现 | 安全增强 | 2小时 | |
| **P2** | Refresh Token自动刷新 | 用户体验 | 2小时 | |
| **P2** | 本地模式网关502问题排查 | 本地开发体验 | 2-4小时 | |
| **P3** | Token吊销和单点登出 | 安全增强 | 3小时 | |
| **P3** | gRPC双轨制简化 | 可维护性 | 2小时 | |

### 7.1 建议整改顺序

1. **第一阶段（阻断性问题）**：P0级别 → 确保能启动
2. **第二阶段（功能问题）**：P1级别 → 确保登录流程正常
3. **第三阶段（体验优化）**：P2级别 → 优化开发体验和用户体验
4. **第四阶段（安全增强）**：P3级别 → 长期安全建设

---

## 八、代码引用索引

### 8.1 集成模式问题

| 文件 | 行号 | 说明 |
|------|------|------|
| [sso-web/start.sh](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/sso-web/deploy/start.sh) | 1 | 解释器问题 |
| [user-web/start.sh](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/deploy/start.sh) | 1 | 解释器问题 |
| [apisix/start.sh](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/ops/docker/apisix/start.sh) | 1 | 解释器问题 |

### 8.2 本地模式问题

| 文件 | 行号 | 说明 |
|------|------|------|
| [local/start.sh](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/ops/scripts/local/start.sh) | 126 | 缺少--env-file参数 |
| [docker-compose-local.yml](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/docker-compose-local.yml) | 8 | 引用APISIX_IMAGE |
| [.env.apisix](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/ops/env/local/.env.apisix) | 8 | 定义APISIX_IMAGE |

### 8.3 SSO OAuth问题

| 文件 | 行号 | 说明 |
|------|------|------|
| [SecurityConfig.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/config/SecurityConfig.java) | 全文 | SSO安全配置 |
| [AuthorizationServerConfig.java](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/backend/services/sso-server/src/main/java/com/sso/config/AuthorizationServerConfig.java) | 全文 | OAuth2授权服务器配置 |
| [SsoCallback.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/src/components/SsoCallback.js) | 全文 | OAuth回调处理 |
| [App.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/user-web/src/App.js) | 全文 | 前端路由和拦截器 |
| [Login.js](file:///Users/wangbo/Project/data-annotation/zqzl/zqzl-traecn/frontend/apps/sso-web/src/components/Login.js) | 全文 | 登录组件 |

---

**文档结束**
