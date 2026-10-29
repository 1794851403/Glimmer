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
│   ├── storage/        # OssStorageService：后端中转 OSS 上传
│   └── web/            # TraceId、UserContext
├── content/
│   ├── controller/     # files/images 图片上传；posts 发帖
│   ├── dto/            # 入参：CreatePostDTO
│   ├── vo/             # 出参：PostCreateVO
│   ├── entity/         # PostDO / PostImageDO / PostTagDO / TagDO
│   ├── mapper/         # 四个 MyBatis-Plus Mapper
│   └── service/        # FileService、PostService 接口
│       └── impl/       # FileServiceImpl：最小校验 + 服务端生成对象名
│                       # PostServiceImpl：最小校验 + 帖子/图片/标签同事务
└── user/
    ├── controller/     # auth/sms-code|login|logout；users/me GET / PATCH
    ├── dto/            # 入参：LoginRequest、SmsCodeRequest、UpdateProfileDTO
    ├── vo/             # 出参：SelfProfileVO 等
    ├── entity/
    ├── mapper/
    └── service/        # AuthService、UserService 接口
        └── impl/       # AuthServiceImpl：登录 / 自动注册；UserServiceImpl：本人资料
```

- **Service 约定**：业务 Service 统一「接口 + `impl` 实现」，接口放 `service/`，实现放
  `service/impl/`；接口名不带 `I` 前缀。技术组件（`JwtService`、`TokenStore`、`RateLimiter`）
  不是模块业务 API，保持具体类，避免单实现的伪抽象。

- **接口注释约定**：Controller 的每个接口方法、Service 接口方法都要有 Javadoc，至少写明
  「用途、鉴权 / 限流、请求参数、返回内容」；路径只写在 `@RequestMapping` / `@PostMapping`
  注解里，注释不重复路径，避免改动时两处漂移。

- **双 Redis**：`core`（noeviction + AOF，Token / 锁 / 秒杀 / 布隆）与 `cache`（allkeys-lru，详情 / Feed / 限流）。
  Spring Boot 默认 `RedisAutoConfiguration` 已排除，连接在 `RedisConfig` 显式创建。
- **鉴权**：默认拒绝 `/api/**`；白名单按「方法 + 路径」精确匹配；JWT 之外还要校验 core 中的
  `glimmer:user:token:{userId}`，支持登出 / 封禁主动失效。
- **数据库**：Flyway `V1__init_schema.sql`（11 张表）、`V2__seed_tags.sql`。
- **限流**：验证码默认 5 次/小时（当前联调期配置为 100，见 `application.yml`）、登录 10 次/小时。
- **图片存储**：开发和发布都走后端中转 OSS 上传，不再提供本地实现；OSS 桶保持私有，
  上传时仅将本次对象设为 `public-read`。

## 运行

1. 在 `deploy/` 中准备 `.env`（见 `deploy/README.md`），至少设置 MySQL / Redis 密码、`JWT_SECRET`（≥32 字节）与 `OSS_*` 配置。
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

## 验证本人资料

```bash
# 编辑页预填：获取当前资料
curl -s http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <accessToken>"

# 局部更新；未传字段不变，bio 传 "" 表示清空
curl -s -X PATCH http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <accessToken>" \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"小光","bio":"记录生活里的微光"}'
```

## 验证图片上传

```bash
curl -s -X POST http://localhost:8080/api/v1/files/images \
  -H "Authorization: Bearer <accessToken>" \
  -F "file=@photo.jpg"
```

- 成功返回 `{ url, size, contentType }`；URL 形如
  `https://{bucket}.{endpoint}/images/{userId}/yyyy/MM/dd/{uuid}.jpg`；
- 只接受 jpg / jpeg / png / webp，单文件 ≤5MB；不做 magic bytes 校验与图片审核；
- 在 `deploy/.env` 设置 `OSS_ENDPOINT` / `OSS_BUCKET` / `OSS_ACCESS_KEY_ID` /
  `OSS_ACCESS_KEY_SECRET`；桶保持私有但需关闭「阻止公共访问」，后端会在上传时通过对象 ACL
  把单个对象设为 `public-read`。

## 验证发帖

```bash
# 先上传图片拿到 URL，再发帖；tagIds 可省略（暂不打标签）
curl -s -X POST http://localhost:8080/api/v1/posts \
  -H "Authorization: Bearer <accessToken>" \
  -H 'Content-Type: application/json' \
  -d '{"title":"周末去山里露营","content":"第一次在秋天露营，夜里能看到银河。","imageUrls":["<imageUrl>"],"tagIds":["1"]}'
```

- 成功返回 HTTP 201，`data` 含 `id / status / statusText / publishedAt / message`；
- 普通用户 `status=0`（已发布待审核），管理员 `status=1`（已发布）；
- 校验只保留最小必要项：标题 1~100 字符、正文 1~20,000 字符、图片 1~9 张且每张 ≤512 字符、
  标签 0~5 个且必须存在；不校验图片 URL 协议与归属；
- 当前只落 MySQL（帖子 / 图片 / 标签同一事务）；Feed 写扩散、ES 索引与 AI 打标随后续模块接入。

## 测试

```bash
mvn test
```

当前 31 个单元测试：JWT、`UserServiceImpl`、`FileServiceImpl`、`PostServiceImpl`；
接 Testcontainers 后补 MySQL / Redis / RabbitMQ / OSS 集成测试与 ArchUnit 模块边界测试。

## 待办

- Feed / 帖子详情 / 编辑删除纵向链路；
- OSS 直传（预签名 URL）、magic bytes 校验、图片内容审核（演进）；
- DeepSeek / ES 的配置与实现（按 M1/M2）；
- ArchUnit、Testcontainers、CI。
