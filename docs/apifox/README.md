# Apifox 导入说明

`glimmer-openapi.json` 是按 [04-接口设计文档.md](../04-接口设计文档.md) v1.15 对齐的 OpenAPI 3.0 契约（`info.version = 1.8.5`），覆盖接口总览中的 **63 个接口**（54 条路径，部分路径同时有 POST / DELETE）。1.8.5 已包含关注 / 取关、粉丝 / 关注列表、帖子编辑 / 删除 / 重提、管理端审核状态流转、用户公开主页、用户帖子 / 我的帖子 / 全站最新等接口；第 55 轮无 schema 变化，只同步 04 的 MVP 说明：关系与计数在 MySQL 事务内维护、游标坏值按首页、`size` 越界夹取、`sort=hot` 在热榜接入前返回 `40001`，缓存 / ES / Feed 写扩散随后续模块接入。

## 导入步骤

1. 打开 Apifox 项目 → **项目设置 → 导入数据 → OpenAPI / Swagger**。
2. 选择 `glimmer-openapi.json`，导入模式建议：
   - 首次导入：**新建接口**；
   - 后续契约更新：**智能合并**，保留已编写的手工测试用例。
3. 在 **环境管理** 中配置服务地址：
   - 本地（后端在宿主机）：`http://localhost:8080`
   - 后端在 CentOS 7 VM：`http://192.168.157.129:8080`
   - 生产示例：`https://api.glimmer.example`（占位域名）
4. 认证：登录接口返回 `accessToken` 后，把它写入环境变量（如 `token`），在项目根目录的 Auth 中选择 **Bearer Token**，值填 `{{token}}`。

## 建议联调顺序

1. `POST /api/v1/auth/sms-code` → 从服务端日志或 `debugCode` 获取验证码（`local` / `dev` profile）。
2. `POST /api/v1/auth/login` → 将 `accessToken` 写入环境变量。
3. 上传图片 → 发帖 → 编辑 / 重提 → 关注 / 取关 → 关注流 → 管理端审核（approve / reject / takedown / restore）→ Feed / 搜索 / 互动。
4. 秒杀：管理端创建活动 → 发布 → 抢购 → `GET /seckill/activities/{activityId}/my-order` 轮询 → 模拟支付。

## 契约约定

- **鉴权**：全局 `bearerAuth`；白名单接口 `security: []`；可选登录接口同时声明 `bearerAuth` 与匿名。
- **管理端**：`/api/v1/admin/**` 额外要求 `role=ADMIN`，OpenAPI 无法表达角色，导入后请确保使用管理员 Token。
- **分页**：用户端为 `CursorPage<T>`（`cursor` / `size`），管理端为 `PageResult<T>`（`page` / `size`）。
- **数据格式**：BIGINT ID 为字符串；时间为 epoch 毫秒整数；金额为字符串十进制；布尔为 `true` / `false`。
- **响应信封**：`{ code, message, data, traceId }`；HTTP 状态与业务 `code` 同时表达语义。
- **错误示例**：同一 HTTP 状态下的多个业务错误码以 `examples` 列出，Apifox 可在响应示例中切换查看。

## 暂不能完整测试的接口

- **WebSocket** `GET /ws/danmaku`：契约只描述握手与帧协议（`x-websocket-frames`），建议用 `wscat` 或浏览器控制台测试。
- **SSE** `POST /api/v1/ai/chat/stream`：契约标注了 `text/event-stream` 与事件示例；流式交互建议用 `curl -N` 或前端 `fetch + ReadableStream` 验证，不要用原生 `EventSource`。
