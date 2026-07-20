# 前台博客前端警告清理变更报告

## 背景

运行 `kuailemao-blog` 前端项目时，控制台持续输出 Sass `legacy-js-api` 弃用提示和 Browserslist 数据过期提示。构建验证中还存在 `.env.production` 里 `NODE_ENV=production` 不受 Vite 支持，以及 Rollup `manualChunks` 配置位置弃用提示。

## 变更内容

- 移除 `.env.development`、`.env.production` 中的 `NODE_ENV` 配置，避免 Vite 对 `.env` 中非开发值的警告；业务代码已使用 Vite 自带的 `env.MODE` 判断环境。
- 在 `vite.config.ts` 的 `scss` 预处理配置中增加 `silenceDeprecations: ['legacy-js-api']`，避免 Vite 4 调用 Sass 旧 JS API 时刷屏。
- 将 `manualChunks` 移入 `build.rollupOptions.output` 下，符合 Rollup 当前配置位置。

## 已验证

- 执行 `node_modules\\.bin\\vite.cmd build` 成功，退出码为 0。
- 构建输出中已不再出现 `legacy-js-api`、`NODE_ENV=production is not supported`、`manualChunks option is deprecated`。

## 剩余提示

`Browserslist: browsers data (caniuse-lite) is 17 months old` 仍存在。它需要更新 `caniuse-lite` 数据。尝试执行 `pnpm exec update-browserslist-db@latest` 时，当前 pnpm 版本要求批准第三方依赖安装脚本；自动批准所有脚本存在安全白名单风险，本轮未保留该配置。

后续可由开发者在确认允许后执行：

```bash
pnpm exec update-browserslist-db@latest
```

若 pnpm 提示需要批准构建脚本，应只批准本项目确需的依赖脚本，并重新运行更新命令。

## 后续建议

当前 `legacy-js-api` 是通过 Sass 官方提供的 `silenceDeprecations` 暂时消警。长期根治方案是升级到 Vite 5.4+ 并切换 Sass modern API，或升级到默认使用 modern API 的 Vite 6，并完成前端回归验证。
