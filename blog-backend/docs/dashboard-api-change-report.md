# Dashboard 首页接口变更报告

## 背景

后台管理首页会请求 Dashboard 概览、访问趋势、文章发布趋势和访客地域分布接口。后端需要提供与前端首页契约一致的数据结构，并提供访客日志表结构用于趋势和地域聚合。

## 变更内容

- 后端提供 `/dashboard/overview`、`/dashboard/visitTrend`、`/dashboard/articleTrend`、`/dashboard/visitor/region` 四个首页接口。
- 概览接口返回 `articleCount`、`visitCount`、`commentCount`、`categoryCount`、`tagCount`。
- 趋势接口返回 `date` 和 `count`，地域接口返回 `province` 和 `count`。
- 访客日志表结构放在 `sql/01_create_visitor_log.sql`，不使用版本号目录。

## 初始验证口径

- 不新增测试文件，不连接数据库。
- 使用 `mvn -DskipTests compile` 验证后端编译。
- 手工联调时，前端 `/api/dashboard/**` 由代理转发到后端 `/dashboard/**`。

## 2026-07-05 趋势接口 500 修复

- 问题：`/dashboard/articleTrend` 和 `/dashboard/visitTrend` 在开启 MySQL `ONLY_FULL_GROUP_BY` 时返回 500。
- 根因：趋势 SQL `SELECT DATE_FORMAT(create_time, '%Y-%m-%d')`，但 `GROUP BY DATE(create_time)`，选择列表达式与分组表达式不一致。
- 修复：两个趋势 SQL 均改为 `GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')`。
- 验证：JDBC 只读执行修复后的两条 SQL，均返回 `OK`；未新增测试文件。
- 备注：本地已有后端进程占用 `target/classes/mapper/ArticleMapper.xml`，导致 `mvn -DskipTests compile` 的 resources 复制阶段报 `AccessDeniedException`；跳过 resources 后 Java 编译链路通过。

## 风险与回滚

- 如果目标数据库未执行 `sql/01_create_visitor_log.sql`，访问趋势和地域接口会因缺表失败。
- 回滚时移除 Dashboard 接口相关后端代码，并撤销 `t_visitor_log` 表结构变更。
