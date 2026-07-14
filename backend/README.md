# Lover MVP backend

Node 20、TypeScript、Fastify 5 与 PostgreSQL 构成的模块化单体。领域模型只有对称的情侣成员，不包含 dad/mom/child。

## 本地启动

要求 Node 20 和 PostgreSQL 14+。

```bash
cp .env.example .env
createdb lover
npm install
npm run db:migrate
npm run dev
```

当前进程直接读取环境变量；可用 shell、Docker Compose 或进程管理器加载 `.env`。API 文档位于 `http://localhost:4000/docs`，原始文档为 `/openapi.json`。

开发默认短信验证码 `123456`，`POST /api/auth/sms/send` 也会返回 `devCode`。生产环境使用开发短信、默认 JWT 密钥或 local 存储会在启动时 fail-fast。阿里云短信按需动态加载。

## API 概览

- `GET /health`、`GET /ready`
- `POST /api/auth/sms/send|login`、`POST /api/auth/refresh|logout`
- `GET|PATCH /api/couple-space`，邀请创建/接受/取消
- `GET /api/bootstrap`：相爱天数（含在一起当天）与最近 6 条媒体
- `/api/media-assets/token|complete|:assetId/sign`：私有资产三阶段流程
- `/api/media`：媒体 CRUD
- `/api/anniversaries`：年度/里程碑纪念日 CRUD 与服务端倒计时
- `/api/letters`：即时信、时间胶囊 CRUD；未解锁响应不含 `content`/`summary`
- `/api/couple-space/unbinding`：申请、伴侣确认、取消

除健康、文档、短信登录、刷新令牌和带签名的私有媒体读取外，使用：

```http
Authorization: Bearer <accessToken>
```

所有输入使用严格 Zod object 校验（额外字段会被拒绝），错误格式统一：

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "请求参数无效", "details": [] } }
```

## 本地媒体上传

1. `POST /api/media-assets/token`，提交 `fileName`、`mimeType`、精确 `sizeBytes`。
2. 使用返回的 `uploadToken` 作为 Bearer token，向 `uploadUrl` 发送单文件 multipart。
3. `POST /api/media-assets/complete` 提交 `assetId`。
4. 在媒体业务记录中引用 ready 的 asset ID。
5. 阅读前调用 `POST /api/media-assets/:assetId/sign` 获取短期私有 URL。

对象 key 只由服务端生成，解析时拒绝绝对路径、反斜线、空段和 `.`/`..`；下载签名仍会查询当前 active membership，解绑后旧签名立即失效。

## 数据一致性与隐私

- PostgreSQL partial unique index 保证一个用户最多一个 active 空间。
- 行锁触发器保证一个空间最多两名 active 成员。
- 所有业务查询同时限定当前 active `space_id`。
- refresh token 仅以 SHA-256 摘要保存，每次刷新轮换；注销撤销 session。
- 解绑必须由另一名成员确认；确认后双方 membership 失活，历史数据保留但不可访问。

## 验证

```bash
npm run typecheck
npm test
npm run build
```

迁移命令按文件名顺序执行 `db/migrations/*.sql`，并记录于 `schema_migrations`。`db/schema.sql` 是供 `psql` 使用的完整 schema 入口。
