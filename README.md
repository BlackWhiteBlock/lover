# Lover

面向情侣的双人私密空间，原生 Android 客户端与 TypeScript/Fastify 后端。

## 项目结构

- `android/`：Kotlin、Jetpack Compose、Material 3 原生客户端
- `backend/`：Node.js、Fastify、PostgreSQL API
- `UI-Figma/`：Figma Make 导出的视觉原型
- `docs/开发需求文档.md`：产品需求与验收标准

## MVP

- 手机验证码登录与可轮换会话
- 情侣邀请、绑定和双方确认解绑
- 相爱天数与近期掠影
- 照片/视频流式上传、私有访问和播放
- 年度/里程碑纪念日
- 即时信与服务端锁定的时间胶囊
- Local 开发存储与七牛私有生产存储

## 本地运行

后端需要 Node.js 20+ 和 PostgreSQL 14+：

```bash
cd backend
cp .env.example .env
npm ci
npm run db:migrate
npm run dev
```

Android 需要 JDK 21 和 Android SDK 36：

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

模拟器默认连接 `http://10.0.2.2:4000/`。发布构建通过
`LOVER_API_BASE_URL=https://api.example.com/` 配置 HTTPS API 地址。

详细配置与安全要求见 `backend/README.md` 和 `android/README.md`。