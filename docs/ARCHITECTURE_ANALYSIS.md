# 技术架构分析与缺失功能

## 一、当前架构概述

当前项目是一个基于 **APISIX 网关 + gRPC 微服务** 的轻量化架构，包含：
- 2 个后端微服务（sso-server、user-server）
- 2 个前端应用（sso-web、user-web）
- APISIX 网关（standalone 模式，YAML 本地配置）
- H2 内存数据库

---

## 二、已实现的功能

### ✅ 认证授权
- 用户登录/登出
- SSO 单点登录（票据机制）
- 用户注册
- 忘记密码（两步验证）
- 图形验证码
- 手机验证码（模拟）

### ✅ 用户管理
- 用户 CRUD 操作
- 用户分页查询、搜索
- 用户状态管理（启用/禁用）
- 密码重置（管理员）
- JWT Token 生成

### ✅ 技术架构
- APISIX 网关路由转发
- gRPC 服务间通信
- Docker 容器化部署
- Maven 多模块构建
- 前后端分离

---

## 三、缺失功能分析

### 🔴 高优先级缺失

#### 1. **认证拦截器 / JWT 校验**
**问题**：当前所有接口都没有 JWT Token 校验，任意未登录用户都可以访问所有 API
- 缺少 Spring Security 认证拦截器
- 缺少 Token 解析和用户上下文注入
- 缺少接口级别的权限控制

**建议实现**：
```java
// 添加 JwtAuthenticationFilter
// 校验 Authorization: Bearer <token>
// 将用户信息存入 SecurityContext
```

#### 2. **权限控制 / RBAC**
**问题**：虽然有角色字段（ADMIN/USER），但没有实际的权限校验
- 缺少 @PreAuthorize 或自定义权限注解
- 缺少角色-权限映射
- 普通用户也可以删除用户、修改用户信息

#### 3. **数据库持久化**
**问题**：当前使用 H2 内存数据库，容器重启后数据全部丢失
- 生产环境需要替换为 MySQL / PostgreSQL
- 缺少数据库迁移工具（Flyway / Liquibase）
- 缺少数据备份机制

#### 4. **配置中心**
**问题**：配置硬编码在 application.yml 中
- 缺少分布式配置中心（Nacos / Apollo / Spring Cloud Config）
- 配置变更需要重启服务
- 缺少配置版本管理

#### 5. **服务注册与发现**
**问题**：当前使用静态地址，扩展性差
- 服务实例扩容需要修改配置
- 缺少服务健康检查和自动剔除
- 建议：Nacos / Consul

---

### 🟡 中优先级缺失

#### 6. **分布式链路追踪**
**问题**：服务间调用问题难以排查
- 缺少 TraceID 传递
- 缺少调用链可视化
- 建议：SkyWalking / Jaeger

#### 7. **日志聚合**
**问题**：日志分散在各个容器中
- 缺少统一的日志收集（ELK / Loki）
- 缺少日志查询平台
- 缺少错误日志告警

#### 8. **监控告警**
**问题**：服务状态无法感知
- 缺少服务健康监控
- 缺少关键指标监控（QPS、响应时间、错误率）
- 缺少告警通知（钉钉/邮件/短信）
- 建议：Prometheus + Grafana + AlertManager

#### 9. **缓存层**
**问题**：频繁查询数据库，性能差
- 缺少热点数据缓存
- 缺少验证码、票据的分布式缓存
- 当前使用 ConcurrentHashMap，多实例部署会有问题
- 建议：Redis

#### 10. **消息队列**
**问题**：缺少异步解耦能力
- 手机验证码发送是同步的
- 缺少日志审计、事件通知的异步处理
- 建议：RabbitMQ / Kafka

---

### 🟢 低优先级缺失

#### 11. **限流熔断**
**问题**：缺少服务保护机制
- 缺少 API 限流
- 缺少服务熔断（Hystrix / Sentinel）
- 缺少降级策略

#### 12. **分布式事务**
**问题**：跨服务操作没有事务保证
- 注册用户时 sso-server 和 user-server 的操作没有事务
- 建议：Seata / TCC

#### 13. **国际化 / i18n**
**问题**：错误消息硬编码中文
- 缺少多语言支持
- 前后端消息统一管理

#### 14. **文件存储**
**问题**：头像等文件没有存储方案
- 缺少对象存储集成（MinIO / OSS）
- 缺少文件上传、下载接口

#### 15. **单元测试 / 集成测试**
**问题**：项目中没有测试代码
- 缺少核心业务逻辑的单元测试
- 缺少 API 集成测试
- 缺少 CI/CD 流水线

#### 16. **操作审计**
**问题**：缺少敏感操作的审计日志
- 用户登录、登出记录
- 管理员操作记录（删除用户、修改权限）
- 日志防篡改

---

## 四、安全风险分析

### 🔴 高危风险

1. **SQL 注入**：虽然使用 JPA，但动态查询需要注意
2. **密码强度**：当前只要求 6 位，建议增加复杂度校验
3. **验证码暴力破解**：缺少发送频率限制
4. **CORS 配置过宽**：当前允许所有来源（`*`）
5. **HTTPS**：生产环境缺少 SSL/TLS 配置

### 🟡 中危风险

1. **敏感信息泄露**：错误消息可能暴露系统内部信息
2. **并发会话**：缺少同一账号最大登录数限制
3. **密码加密**：BCrypt 强度可以提升

---

## 五、架构演进建议

### 短期（1-2周）
1. ✅ 修复当前编译错误（已完成）
2. ✅ 修复 APISIX 网关启动问题（已完成）
3. ✅ 修复管理平台 /user 路径 404 问题（已完成）
4. ✅ 移除 etcd 依赖，改用 standalone 模式（已完成）
5. 🔧 实现 JWT 认证拦截器
6. 🔧 实现基于角色的权限控制
7. 🔧 替换 H2 为 MySQL

### 中期（1-2月）
6. 引入 Redis 分布式缓存
7. 引入配置中心（Nacos）
8. 引入服务注册发现（Nacos）
9. 引入链路追踪（SkyWalking）
10. 实现操作审计日志

### 长期（3月+）
11. 引入监控告警体系
12. 引入限流熔断
13. 引入消息队列
14. 实现 CI/CD 流水线
15. 完善单元测试覆盖率

---

## 六、当前问题修复总结

### ✅ 已修复问题

1. **APISIX 网关启动问题**：
   - 原问题 1：`sh: /usr/local/apisix/bin/apisix: not found` - CMD 路径错误
   - 原问题 2：`ERROR: Admin API can only be used with etcd config_center` - 配置冲突
   - 修复：使用 standalone 模式，更新 CMD 路径为 `/usr/bin/apisix`
   - 文件：[Dockerfile](../ops/docker/apisix/Dockerfile)、[config.yaml](../ops/docker/apisix/config.yaml)

2. **管理平台 /user 路径 404**：
   - 原问题：APISIX 路由 `/user/*` 不匹配 `/user`（无子路径）
   - 修复：添加 `/user` 和 `/user/` 的精确匹配路由
   - 文件：[apisix.yaml](../ops/docker/apisix/apisix.yaml)

3. **SecurityConfig 路径不匹配**：
   - 原问题：`/api/auth/**` 与实际路径 `/v1/auth/**` 不匹配
   - 修复：修正为 `/v1/auth/**`
   - 文件：[SecurityConfig.java](../backend/services/sso-server/src/main/java/com/sso/config/SecurityConfig.java#L26-L28)

4. **后端编译**：
   - sso-server 编译成功 ✅
   - user-server 编译成功 ✅

5. **文档梳理与 etcd 移除**：
   - 原问题：文档内容冗余，层次混乱，且包含 etcd 相关描述
   - 修复：重新整理文档，移除 etcd 服务，改用 APISIX standalone 模式
   - 变更文件：
     - [docker-compose.yml](../docker-compose.yml) - 移除 etcd 服务
     - [ARCHITECTURE.md](ARCHITECTURE.md) - 技术架构更新
     - [DEPLOYMENT.md](DEPLOYMENT.md) - 部署指南更新
     - [ARCHITECTURE_ANALYSIS.md](ARCHITECTURE_ANALYSIS.md) - 架构分析更新
