# 静态页面标题品牌一致性变更报告

## 背景

前台博客页面首屏加载时，浏览器会先展示 `index.html` 中的静态标题。原静态标题仍为 `Ruyu-blog | 不断追求完美的开源博客`，Vue 路由初始化后才切换为 `Crux-blog | 不断追求完美的开源博客`，导致用户看到短暂的旧品牌标题。

同时检查后台管理项目发现，后台 `index.html` 静态标题为 `博客后台`，而环境变量中的应用标题为 `Crux-Blog`，存在同类的静态入口标题不一致问题。

## 变更内容

- 将前台博客 `blog-frontend/kuailemao-blog/index.html` 的静态标题改为 `Crux-blog | 不断追求完美的开源博客`。
- 将后台管理 `blog-frontend/kuailemao-admin/index.html` 的静态标题改为 `Crux-Blog`。

## 影响范围

- 仅影响浏览器标签页在 Vue 应用初始化前展示的静态标题。
- 不影响路由、接口、业务逻辑、权限、构建配置和数据库。

## 验证

- 通过 `rg` 确认前台静态标题与首页路由标题一致。
- 通过 `rg` 确认后台静态标题与 `.env.development`、`.env.production` 中的 `VITE_GLOB_APP_TITLE` 一致。

## 回滚方式

如需回滚，仅恢复以下两个文件的 `<title>` 内容：

- `blog-frontend/kuailemao-blog/index.html`
- `blog-frontend/kuailemao-admin/index.html`
