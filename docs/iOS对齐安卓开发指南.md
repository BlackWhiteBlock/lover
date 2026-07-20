# Lover iOS 对齐安卓 — Cursor 分步开发指南

> 目标：用 Cursor **完整开发 iOS App**，界面与交互尽可能对齐安卓端。  
> 约束：**不使用 Multitask**；采用 **Plan 定方案 → 写规则 → Agent 按切片落地**。  
> 参考产品面：安卓 Compose（`android/…`），鸿蒙为已对齐的第二端，可作辅助对照。

---

## 0. 使用前必读

### 0.1 三种模式在本项目中的角色

| 模式 | 是否使用 | 用途 |
|------|----------|------|
| **Plan** | ✅ 必须 | 架构、里程碑、技术选型、对齐策略、大功能开工前拆解 |
| **Agent** | ✅ 主力 | 按「垂直切片」写代码、改 UI、接 API |
| **Multitask** | ❌ 不用 | 易并行踩同一导航/会话层，合并成本高；本指南一律串行 |

### 0.2 总原则（写进脑子里）

1. **安卓是唯一 UI/交互标准答案**，不是「随便做一个好看的 iOS」。
2. **只复用后端 API**（`backend/`），不幻想直接复用 Compose 代码。
3. **一次只做一个垂直切片**，做完验收再开下一块。
4. **每个切片的提示词里必须写清安卓参考路径**。
5. 平台限制导致无法 1:1 时，必须在切片结束时写下 **divergence（差异说明）**。

### 0.3 推荐技术栈（Plan 阶段可微调，但建议默认如下）

| 层 | 建议 |
|----|------|
| UI | SwiftUI |
| 异步 | async/await |
| 网络 | URLSession（自建薄封装，对齐安卓/鸿蒙的 token 刷新语义） |
| 本地会话 | Keychain + UserDefaults / AppStorage（token 放 Keychain） |
| 导航 | 根状态机（Splash / Auth / Onboarding / Main）+ TabView |
| 图片 | 与安卓一样处理「签名 URL」；注意缓存键不要只认完整带 token 的 URL |
| 工程位置 | 建议 monorepo 下新建 `ios/`（与 `android/`、`harmony/`、`backend/` 并列） |

---

## 1. 总体流程总览

按顺序执行，不要跳步：

```text
阶段 A  Plan：对齐策略 + 里程碑 + 目录约定
    ↓
阶段 B  写 Cursor Rule：强制「以安卓为准」
    ↓
阶段 C  Agent：切片 0 — 工程骨架
    ↓
阶段 D  Agent：切片 1…N — 按里程碑串行实现
    ↓
阶段 E  每切片验收（对照安卓同一操作路径）
    ↓
阶段 F  大功能前短 Plan，再 Agent
    ↓
阶段 G  收尾：差异清单 + 回归清单
```

下面从阶段 A 开始，逐步照做即可。

---

## 2. 阶段 A — 第一次 Plan（开工前，约 1 次长对话）

### 2.1 你怎么开

1. 在 Cursor 切到 **Plan** 模式。  
2. 附上本文档路径：`docs/iOS对齐安卓开发指南.md`。  
3. 使用下面提示词（可原样粘贴）：

```text
请阅读 docs/iOS对齐安卓开发指南.md 与 android/app/src/main/java/com/lover/app/ 下的 feature/、core/。
我要在 monorepo 新建 ios/，用 SwiftUI 全量开发 Lover iOS，
界面与交互对齐安卓（Harmony 仅作辅助对照）。
不使用 Multitask。

请输出一份可执行的 Plan，必须包含：
1. 推荐 iOS 目录结构（对照安卓 core/feature）
2. 根导航状态机（Splash/Auth/Onboarding/Main）
3. 按优先级的里程碑列表（每项可独立用 Agent 完成）
4. 每个里程碑对应的安卓参考文件路径
5. 明确「本阶段不做」的范围
6. 第一刀 Agent 任务的完整提示词草稿
不要直接写业务代码。
```

### 2.2 Plan 验收标准（你来勾选）

- [ ] 有清晰的 `ios/` 目录树  
- [ ] Tab 与安卓一致：空间 / 时光 / 纪念 / 信封 / 我们（`MainTab`）  
- [ ] 里程碑可独立验收、可串行  
- [ ] 每个里程碑都挂了安卓参考路径  
- [ ] 你认可技术栈与「不做清单」  

Plan 满意后再进入阶段 B。若 Plan 有争议，继续在 Plan 模式里改，直到你点头。

---

## 3. 阶段 B — 写 Cursor 规则（防止 Agent 跑偏）

### 3.1 你怎么开

切到 **Agent** 模式，粘贴：

```text
请根据 docs/iOS对齐安卓开发指南.md，给你自己加上rules

规则要求：
1. iOS UI/交互以 android/ 为唯一参考；
2. 实现 iOS 功能时先定位安卓参考文件，再镜像行为；
3. 与安卓/鸿蒙共用同一 backend API；
4. 禁止 iOS 自创主流程；平台限制必须写 divergence；
5. 本阶段只改 ios/（除非明确要改 API 合同）；
6. 不使用 Multitask；一次任务只做一个垂直切片。

写完后用几句话总结规则要点。
```

### 3.2 建议同时更新全栈规则（可选但推荐）

现有 `.cursor/rules/fullstack-dual-app.mdc` 只写了 Android + Harmony。  
在合适时机让 Agent **扩展为三端**：Android 为参考，Harmony + iOS 对齐。  
这可在切片 0 之前或之后做一次，单独开一个小 Agent 任务即可。

### 3.3 规则验收

- [ ] `ios-align-android` 规则已存在  
- [ ] 新开 Agent 时，模型会主动找安卓参考路径  

---

## 4. 阶段 C — 切片地图（照此顺序做，不要并行）

以下为推荐顺序。每一行 = **一次独立的 Agent 对话**（做完验收再开下一个）。

| 切片 | 名称 | 安卓主要参考 | 验收（用户可见） |
|------|------|--------------|------------------|
| **0** | 工程骨架 + 主题 | `core/design/*`、`MainActivity.kt` | 能编译运行；颜色/字体气质接近安卓 |
| **1** | 网络层 + Token 静默刷新 | `core/network/*`、`TokenStore.kt`、`AppRepository.call` | access 过期后自动 refresh 重试；失败才回登录 |
| **2** | Splash + 会话恢复 | `feature/splash/*`、`MainActivity` 根路由 | 有 token 进主页/引导，无 token 进登录 |
| **3** | 短信登录 | `feature/auth/*` | 发码、登录、错误提示对齐 |
| **4** | 本机号一键登录（可后置） | `core/auth/PnvsLoginHelper.kt`、`docs/阿里云本机号一键登录.md` | iOS 号码认证能登录（依赖阿里云 iOS 配置） |
| **5** | Onboarding 建档 | `feature/onboarding/*` | 昵称/性别/生日/空间名提交后进主页 |
| **6** | Main Tab 壳 | `MainScreen.kt`、`MainTab` | 五 Tab + 文案（solo/linked）对齐 |
| **7** | 空间首页 | `HomeSections.kt` | 相爱天数、日签、掠影入口对齐 |
| **8** | 时光列表 + 未读 | `TimelineGallery.kt`、未读 API | 列表、年筛选、未读红点/列表对齐 |
| **9** | 时光详情/编辑/发布 | `MediaDetailScreen.kt`、`ComposerScreens.kt` | 浏览、编辑、多图上传对齐 |
| **10** | 纪念日 | `MainScreen` 纪念 Tab、`AnniversaryCompose` | 列表 + 新增/编辑对齐 |
| **11** | 信封列表 + 详情拆信 | `LetterDetailScreen.kt`、信封未读 | 未拆红点、拆信动效、已寄/已阅对齐 |
| **12** | 我们 / 绑定 / 解绑 | `MainScreen` Profile、`CoupleScreen.kt` | 卡片、绑定、解绑、退出对齐 |
| **13** | 个人头像 / 空间编辑 | Profile 头像编辑、Space 编辑相关 | 点自己头像可改头像；绑定后合照逻辑对齐 |
| **14** | 打磨与回归 | 全文 | 对照下方「全量回归清单」 |

> 说明：切片 4（一键登录）可放到 3 之后立刻做，也可等主路径通了再做；**不要插队打乱 0→3→5→6**。

---

## 5. 阶段 D — 每个切片怎么用 Agent（标准作业程序）

### 5.1 开新对话的习惯

- **一个切片 = 一个新 Agent 对话**（或明确「继续切片 N」）。  
- 不要在同一对话里说「顺便把时光和信封也做了」。  
- 不使用 Multitask / 并行多 Agent 写业务。

### 5.2 通用 Agent 提示词模板（必用）

把 `{…}` 换成当前切片内容：

```text
【任务】Lover iOS 垂直切片：{切片编号 + 名称}
【模式】仅实现本切片；不要做下一切片；不使用 Multitask。
【对齐标准】以安卓为唯一参考，用户可见行为一致。

【安卓参考】
- {路径1}
- {路径2}

【鸿蒙辅助（可选）】
- {harmony 对应路径，若有}

【API】
- {相关 endpoint，或写「对照 ApiService / 鸿蒙 ApiService」}

【实现要求】
1. 先读安卓参考，再写 ios/ 代码；
2. 文案、信息架构、主交互对齐；
3. 平台差异单独列出 divergence；
4. 不要改 backend，除非 API 真缺能力（缺则先说明再改）。

【完成定义】
- {本切片验收条目，从第 4 节表格抄}

做完后用中文简短总结：改了哪些文件、如何对照安卓验收、有无 divergence。
```

### 5.3 切片 0 示例提示词（可直接用）

```text
【任务】Lover iOS 垂直切片：0 工程骨架 + 主题
【模式】仅本切片；不使用 Multitask。
【对齐标准】视觉气质对齐安卓 Couple/Soleil mood。

【安卓参考】
- android/app/src/main/java/com/lover/app/core/design/LoverTheme.kt
- android/app/src/main/java/com/lover/app/core/design/LoverBrand.kt
- android/app/src/main/java/com/lover/app/MainActivity.kt

【实现要求】
1. 在 monorepo 创建 ios/ Xcode 工程（SwiftUI，最低部署版本你选合理值并说明）；
2. 目录贴近：App / Core(Design, Network, Data, Model) / Feature(…空壳即可)；
3. 主题色、字体策略对齐安卓；先能跑一个占位 RootView；
4. 根路由枚举先搭好：splash / auth / onboarding / main（可先全是占位页）。

【完成定义】
- Xcode 能编译运行到模拟器；
- 有 README 说明如何打开工程；
- 列出与安卓 design token 的对应关系。
```

### 5.4 切片 1 特别注意（网络）

安卓/鸿蒙已实现：**401 → 静默 refresh → 重试；refresh 失败才清会话回登录**。

iOS 必须同样实现，并吸取鸿蒙教训：

- 更新类接口优先确认 HTTP 方法在 iOS 上真实发出（鸿蒙曾把 PATCH 退化成 GET）。  
- 建议：`URLSession` 明确使用 `PATCH`；若遇环境问题，可与后端已支持的 **POST 别名**对齐（`/api/me` 等）。  
- 并行多个 401 时要 **单飞 refresh**（同一时刻只刷新一次），避免 refresh 旋转把会话打挂。

---

## 6. 阶段 E — 每切片验收清单（对照安卓点一遍）

每个切片结束后，你在 **安卓真机/模拟器** 与 **iOS 模拟器** 上走同一路径：

### 6.1 通用验收

- [ ] 主路径能走通，无崩溃  
- [ ] 文案与安卓一致（或仅有平台必要差异并已记录）  
- [ ] 错误提示可读（网络失败、校验失败）  
- [ ] 未引入下一切片的半成品入口（或入口禁用并注明）  

### 6.2 记录模板（建议建 `docs/ios-alignment-log.md`，每切片追加）

```markdown
## 切片 N — 标题 — YYYY-MM-DD
- 安卓参考：
- iOS 主要文件：
- 验收结果：通过 / 未通过
- divergence：
- 下一切片准备：
```

---

## 7. 阶段 F — 大功能前的「短 Plan」

以下切片开工前，建议先 **Plan 10～20 分钟**，再 Agent：

- 切片 8–9（时光上传/多图/签名 URL）  
- 切片 11（信封拆信动效与已读）  
- 切片 12（绑定/解绑状态机）  
- 切片 4（一键登录，涉及阿里云 iOS SDK）  

短 Plan 提示词：

```text
即将做 iOS 切片 {N}。请只读安卓参考 {路径}，输出：
1) 状态机/数据流草图 2) 文件清单 3) 风险点 4) Agent 实现提示词。
不要写代码。
```

---

## 8. 安卓模块速查（写提示词时用）

### 8.1 核心层

| 职责 | 安卓路径 |
|------|----------|
| 主题 | `android/.../core/design/LoverTheme.kt` |
| 品牌 | `android/.../core/design/LoverBrand.kt` |
| 模型 | `android/.../core/model/Models.kt` |
| 仓库 | `android/.../core/data/AppRepository.kt` |
| Token | `android/.../core/data/TokenStore.kt` |
| API | `android/.../core/network/ApiService.kt` |
| 401 刷新 | `android/.../core/network/TokenAuthenticator.kt` |
| 选图 | `android/.../core/media/GalleryPickContracts.kt` |
| 签名图 | `android/.../core/media/SignedMediaImage.kt` |

### 8.2 功能层

| 功能 | 安卓路径 |
|------|----------|
| 根入口 | `MainActivity.kt` |
| 启动 | `feature/splash/SplashScreen.kt` |
| 登录 | `feature/auth/AuthScreen.kt` |
| 建档 | `feature/onboarding/OnboardingScreen.kt` |
| 主壳/多 Tab | `feature/main/MainScreen.kt` |
| Tab 枚举 | `feature/main/MainViewModel.kt` → `MainTab` |
| 首页区块 | `feature/main/HomeSections.kt` |
| 时光 | `feature/main/TimelineGallery.kt` |
| 媒体详情 | `feature/main/MediaDetailScreen.kt` |
| 信封详情 | `feature/main/LetterDetailScreen.kt` |
| 发布 | `feature/main/ComposerScreens.kt` |
| 绑定 | `feature/couple/CoupleScreen.kt` |

### 8.3 五 Tab 对齐（必须一致）

安卓 `MainTab`：`HOME, TIMELINE, ANNIVERSARY, LETTERS, PROFILE`  
展示名（示意）：空间 / 时光 / 纪念 / 信封 / 我|我们（随是否绑定变化）

---

## 9. 建议的 iOS 目录（Plan 可微调）

```text
ios/
  README.md
  Lover/
    App/
      LoverApp.swift
      RootView.swift          # splash/auth/onboarding/main 切换
    Core/
      Design/                # Theme, Fonts, SoftInputs…
      Model/                 # 对齐 Models.kt
      Network/               # API + Auth refresh（单飞）
      Data/                  # AppRepository, TokenStore
      Media/                 # Picker, signed image
    Feature/
      Splash/
      Auth/
      Onboarding/
      Main/                  # Tab 壳
      Home/
      Timeline/
      Anniversary/
      Letters/
      Profile/
      Couple/
```

命名不必与安卓文件一一对应，但 **Feature 边界应对齐**。

---

## 10. 全量回归清单（切片 14 / 上架前）

在安卓与 iOS 上各走一遍：

### 账号与会话

- [ ] 短信登录  
- [ ]（若已做）本机号一键登录  
- [ ] 杀进程重启仍保持登录（refresh 可用）  
- [ ] 退出登录回到登录页  
- [ ] 另一端重新登录后，本端是否符合「单端登录」产品预期（被踢时最好有明确提示）  

### 主路径

- [ ] Onboarding  
- [ ] 五 Tab 切换、文案随绑定状态变化  
- [ ] 空间：相爱天数 / 日签 / 掠影  
- [ ] 时光：浏览、未读、年筛选、发布、详情  
- [ ] 纪念：增删改  
- [ ] 信封：未读红点、拆信、已寄/已阅  
- [ ] 我们：绑定、解绑流程、个人头像、情侣合照（若有）  

### 体验

- [ ] 加载/空态/错误态不劣于安卓  
- [ ] 关键动效（拆信、主题切换等）有等价实现  

---

## 11. 常见坑（从安卓/鸿蒙经验迁移）

| 坑 | 建议 |
|----|------|
| 一次让 Agent「做完整 iOS」 | 禁止；按切片 |
| iOS 自创导航结构 | 禁止；对齐 MainTab |
| Token 刷新多请求并发 | 必须单飞 |
| 签名 URL 缓存 | 缓存键去掉易变 query（参考安卓 `SignedMediaImage`） |
| 更新资料用错 HTTP 方法 | 确认真实发出 PATCH/POST；可走后端 POST 别名 |
| 改完 UI 状态不刷新 | 状态要用可观察对象；返回前台要同步 |
| 只对齐「长得像」不对齐状态机 | 绑定中/已绑定/解绑申请等状态必须对齐 |

---

## 12. 每日工作节奏（建议）

1. 打开本文档，确认今天只做 **一个切片**。  
2. 大切片先 **短 Plan**，小切片直接 **Agent + 模板提示词**。  
3. 跑安卓 + iOS 对照验收。  
4. 写 5 行对齐日志。  
5. 提交 git（按你的节奏，建议每切片一提交）。  
6. **停止**，不要「顺手再做一点下一切片」。

---

## 13. 你现在立刻做的第一步

1. Cursor 切换到 **Plan**。  
2. 粘贴 **§2.1** 的 Plan 提示词。  
3. 审完 Plan → 按 **§3** 写规则。  
4. 新开 **Agent**，粘贴 **§5.3** 做切片 0。  

完成切片 0 后，再开新 Agent，用 **§5.2 模板** 做切片 1。

---

## 14. 文档维护

- 里程碑若有调整：改第 4 节表格，并在 Plan 中确认。  
- 三端规则变更：同步 `.cursor/rules/`。  
- 本文是流程真源；实现细节以安卓代码与后端 API 为准。
