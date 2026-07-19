# 阿里云号码认证 Harmony SDK

请从 [号码认证控制台 → API & SDK](https://dypns.console.aliyun.com/) 下载 **鸿蒙** SDK，将解压后的 `.har` **复制并重命名**为：

`numberauth_standard.har`

放入本目录后，在 `harmony/entry` 执行：

```bash
ohpm install
```

`oh-package.json5` 已声明：`"numberauth_standard": "file:./libs/numberauth_standard.har"`。

未放入 har 时 DevEco 无法解析该依赖；可临时注释依赖与 `PnvsAuthHelper` / AuthPage 中的一键登录引用，仅保留短信登录。
