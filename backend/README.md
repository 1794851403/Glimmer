# backend

后端代码目录（模块化单体）。设计依据：`docs/02-架构设计文档.md`、`docs/03-数据库设计文档.md`、`docs/04-接口设计文档.md`。

## 当前骨架

```
src/main/java/com/glimmer/
├── GlimmerApplication.java
├── common/
│   ├── api/            # Result / CursorPage / PageResult
│   ├── auth/           # JWT、Token 白名单、统一鉴权拦截器
│   ├── config/         # 双 Redis、Redisson、CORS、MVC
│   ├── exception/      # 错误码、业务异常、全局异常处理
│   ├── ratelimit/      # Redis Lua 滑动窗口
│   └── web/            # TraceId、UserContext
└── user/
    ├── controller/     # POST /api/v1/auth/sms-code|login|logout
    ├── dto/
    ├── entity/
    ├── mapper/
    └── service/        # AuthService 接口
        └── impl/       # AuthServiceImpl：登录 / 自动注册
```

- **Service 约定**：业务 Service 统一「接口 + `impl` 实现」，接口放 `service/`，实现放
  `service/impl/`；接口名不带 `I` 前缀。技术组件（`JwtService`、`TokenStore`、`RateLimiter`）
  不是模块业务 API，保持具体类，避免单实现的伪抽象。

- **双 Redis**：`core`（noeviction + AOF，Token / 锁 / 秒杀 / 布隆）与 `cache`（allkeys-lru，详情 / Feed / 限流）。
  Spring Boot 默认 `RedisAutoConfiguration` 已排除，连接在 `RedisConfig` 显式创建。
- **鉴权**：默认拒绝 `/api/**`；白名单按「方法 + 路径」精确匹配；JWT 之外还要校验 core 中的
  `glimmer:user:token:{userId}`，支持登出 / 封禁主动失效。
- **数据库**：Flyway `V1__init_schema.sql`（11 张表）、`V2__seed_tags.sql`。
- **限流**：验证码 5 次/小时、登录 10 次/小时，阈值在 `application.yml`。

## 运行

1. 在 `deploy/` 中准备 `.env`（见 `deploy/README.md`），至少设置 MySQL / Redis 密码与 `JWT_SECRET`（≥32 字节）。
2. 启动基础设施：`cd deploy && docker compose up -d`。
3. 运行：

   ```bash
   mvn spring-boot:run
   ```

   IDE 中运行 `com.glimmer.GlimmerApplication` 时，工作目录设为 `backend/`。

后端启动时会自动执行 Flyway；首次登录的手机号会自动注册。

## 验证登录链路

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/sms-code \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800138000"}'
# dev profile 响应里带 debugCode；服务端日志也会打印

curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800138000","code":"<debugCode>"}'

curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer <accessToken>"
```

## 测试

```bash
mvn test
```

当前只有 JWT 单元测试；接 Testcontainers 后补 MySQL / Redis / RabbitMQ 集成测试与 ArchUnit 模块边界测试。

## 待办

- OSS / DeepSeek / RabbitMQ / ES 的配置与实现（按 M1/M2）；
- 上传、发帖、Feed / 详情纵向链路；
- ArchUnit、Testcontainers、CI。
