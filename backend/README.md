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

进程通过 dotenv 和系统环境变量读取配置。API 文档位于 `http://localhost:4000/docs`，原始文档为 `/openapi.json`。

开发默认短信验证码 `123456`，`POST /api/auth/sms/send` 也会返回 `devCode`。生产环境必须使用阿里云短信、非默认 JWT 密钥、`STORAGE_PROVIDER=qiniu` 以及完整的七牛配置，否则启动立即失败。

## 生产存储（七牛 Kodo）

设置 `QINIU_ACCESS_KEY`、`QINIU_SECRET_KEY`、`QINIU_BUCKET`、HTTPS `QINIU_UPLOAD_URL`、HTTPS `QINIU_DOWNLOAD_DOMAIN`。Bucket 必须为私有空间。

上传 token 将 `scope` 锁定到精确的 `bucket:objectKey`，启用 `insertOnly`、`forceSaveKey`，并精确限制大小和 MIME。客户端直传后，`complete` 先以数据库中的 asset ID、active space、owner、provider 和 bucket 精确定位记录，再使用该记录的 objectKey 调用七牛管理 API `stat` 核对实际大小和 MIME；任何不一致都不会进入 ready 状态。

七牛 SDK 的 `BucketManager.stat` 不保证返回 PutPolicy 的 `endUser`，因此完成判定不依赖该字段，也不接受客户端回传的 objectKey、大小或 MIME 作为事实来源。

下载由服务端生成私有签名 URL：

```json
{ "variant": "original" }
```

或图片缩略图 `{ "variant": "thumb" }`。缩略图处理参数由 `QINIU_IMAGE_THUMB_FOP` 配置，并纳入七牛私有 URL 签名；视频请求 thumb 时安全退化为原文件。

## API 概览

- `GET /health`、`GET /ready`（均执行 PostgreSQL 探测，失败返回 503）
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

## 媒体上传

1. `POST /api/media-assets/token`，提交 `fileName`、`mimeType`、精确 `sizeBytes`。
2. local：使用 `uploadToken` 作为 Bearer token，向 `uploadUrl` 发送单文件 multipart。
3. qiniu：按七牛表单上传协议将 `uploadToken` 作为 `token`，并提交响应中的 `uploadFields`（含服务端 key）。
4. `POST /api/media-assets/complete` 提交 `assetId`；服务端从实际 provider 验证对象。
5. 在媒体业务记录中引用 ready 的 asset ID。
6. 阅读前调用 `POST /api/media-assets/:assetId/sign` 获取短期私有 URL。

对象 key 只由服务端生成，解析时拒绝绝对路径、反斜线、查询串、片段、空段和 `.`/`..`。local 下载每次仍查询当前 active membership，解绑后旧签名立即失效；七牛 URL 有短 TTL，签发前严格校验 active space。

## 限流

API 基于来源 IP 限流，并返回统一 `RATE_LIMITED` 错误：短信发送 5 次/10 分钟、登录 10 次/10 分钟、邀请创建 10 次/小时、接受邀请 20 次/小时、上传 token 30 次/分钟。生产部署在可信反向代理后时设置 `TRUST_PROXY=true`。默认计数器在单进程内存中；多副本部署应按 `@fastify/rate-limit` 文档接入共享 Redis store。

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

迁移命令只接受三位数字前缀的 SQL，并按文件名顺序执行；当前依次为 `001_initial.sql`、`002_qiniu_storage.sql`，结果记录于 `schema_migrations`。`db/schema.sql` 也按相同顺序引用两者。
