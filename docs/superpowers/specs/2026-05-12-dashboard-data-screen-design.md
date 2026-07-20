# 博客运营概览仪表盘设计

## 概述

将 admin 后台的欢迎首页（`/welcome`）改造为运营概览仪表盘，以网格卡片布局展示博客核心运营数据、趋势图表、访客地域分布和系统监控信息。

## 页面布局

采用 **网格卡片布局**（Grid Card Layout），三行结构：

### 第一行：核心指标卡片（4 列）

四个渐变色统计卡片横向排列：

| 卡片 | 数据源 | 渐变色 |
|------|--------|--------|
| 文章总数 | `t_article` 已发布文章数（status=1） | 紫色渐变 `#667eea → #764ba2` |
| 总访问量 | 所有文章 visitCount 之和 | 粉红渐变 `#f093fb → #f5576c` |
| 评论数 | `t_comment` 总数 | 蓝色渐变 `#4facfe → #00f2fe` |
| 总字数 | 所有文章 wordCount 之和 | 绿色渐变 `#43e97b → #38f9d7` |

### 第二行：趋势图 + 系统状态（2:1 分栏）

- **左侧（2/3 宽）**：访问量趋势折线图
  - 支持 7 天 / 30 天切换
  - X 轴为日期，Y 轴为访问量
  - 使用 @antv/g2plot Line 绘制
- **右侧（1/3 宽）**：系统监控面板
  - CPU 使用率（百分比 + 进度条）
  - 内存使用率
  - JVM 使用率
  - 磁盘使用率
  - 复用现有 `GET /monitor/server` 接口

### 第三行：地图 + 文章趋势（1:1 分栏）

- **左侧**：访客地域分布图
  - 中国地图 + 热力点
  - 基于 IP 地址解析省份，聚合各省份访客数量
  - 使用 @antv/l7 绘制
- **右侧**：文章发布趋势图
  - 支持 7 天 / 30 天切换
  - X 轴为日期，Y 轴为发布数量
  - 使用 @antv/g2plot Column 绘制

## 后端改动

### 1. 新建数据库表：`t_visitor_log`

记录前台每次访问（不需要登录）：

```sql
CREATE TABLE t_visitor_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    ip          VARCHAR(64)  NOT NULL COMMENT '访问者IP',
    address     VARCHAR(128) DEFAULT NULL COMMENT 'IP归属地（省份）',
    browser     VARCHAR(64)  DEFAULT NULL COMMENT '浏览器',
    os          VARCHAR(64)  DEFAULT NULL COMMENT '操作系统',
    page_url    VARCHAR(512) DEFAULT NULL COMMENT '访问页面URL',
    user_agent  VARCHAR(512) DEFAULT NULL COMMENT 'User-Agent',
    create_time DATETIME     NOT NULL COMMENT '访问时间',
    is_deleted  TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_create_time (create_time),
    INDEX idx_ip (ip),
    INDEX idx_address (address)
) COMMENT '访客访问日志';
```

### 2. 新建实体、Mapper、Service

- `VisitorLog` 实体（对应 `t_visitor_log`）
- `VisitorLogMapper` 继承 BaseMapper
- `VisitorLogService` / `VisitorLogServiceImpl`
  - `saveLog(VisitorLog log)` — 保存访问记录
  - `getRegionStatistics(Integer days)` — 按省份聚合访客数量
  - `getVisitTrend(Integer days)` — 按天聚合访问量趋势

### 3. 新建访客记录采集

- 创建 `VisitorLogFilter`（OncePerRequestFilter），拦截前台 API 请求
- 仅记录前台访问路径（`/article/visit/**` 等），排除后台管理和静态资源
- 异步写入（通过线程池或 RabbitMQ），不影响请求响应时间
- 解析 IP 归属地可使用现有工具类或集成 ip2region 库

### 4. 新建仪表盘 Controller

`DashboardController` 提供以下接口：

| 接口 | 方法 | 说明 | 权限 |
|------|------|------|------|
| `/dashboard/overview` | GET | 核心指标（文章数、访问量、评论数、字数、分类数、标签数） | 需要登录 |
| `/dashboard/visitTrend` | GET | 访问量趋势（参数：days=7 或 30） | 需要登录 |
| `/dashboard/articleTrend` | GET | 文章发布趋势（参数：days=7 或 30） | 需要登录 |
| `/dashboard/visitor/region` | GET | 访客地域分布（参数：days=7 或 30） | 需要登录 |

### 5. VO 定义

**DashboardOverviewVO：**
- articleCount (Long) — 文章总数
- visitCount (Long) — 总访问量
- commentCount (Long) — 评论数
- wordCount (Long) — 总字数
- categoryCount (Long) — 分类数
- tagCount (Long) — 标签数

**TrendVO：**
- date (String) — 日期（yyyy-MM-dd）
- count (Long) — 数量

**RegionStatVO：**
- province (String) — 省份名称
- count (Long) — 访客数量

## 前端改动

### 1. 替换首页路由

将 `/welcome` 路由指向数据大屏组件，或直接修改 `welcome/index.vue` 的内容为仪表盘。

### 2. 新建 API 调用

在 `src/api/blog/` 下新建 `dashboard.ts`：

```typescript
getDashboardOverview()           // GET /dashboard/overview
getVisitTrend(days: number)      // GET /dashboard/visitTrend?days=7
getArticleTrend(days: number)    // GET /dashboard/articleTrend?days=7
getVisitorRegion(days: number)   // GET /dashboard/visitor/region?days=7
getServerMonitor()               // GET /monitor/server (已存在)
```

### 3. 页面组件结构

在 `welcome/index.vue` 中实现仪表盘，或拆分为子组件：

```
welcome/
├── index.vue              # 主页面，组合所有子组件
└── components/
    ├── StatCard.vue        # 统计卡片组件（接收标题、数值、渐变色）
    ├── VisitTrend.vue      # 访问量趋势折线图（@antv/g2plot Line）
    ├── ArticleTrend.vue    # 文章发布趋势柱状图（@antv/g2plot Column）
    ├── VisitorMap.vue      # 访客地域分布地图（@antv/l7）
    └── SystemMonitor.vue   # 系统监控面板（CPU/内存/JVM/磁盘）
```

### 4. 图表库使用

- **@antv/g2plot**（已安装 ^2.4.31）：趋势折线图和柱状图
- **@antv/l7**（已安装 ^2.19.10）：中国地图热力图

### 5. 数据刷新策略

- 页面加载时获取所有数据
- 系统监控每 30 秒自动刷新一次
- 趋势图切换 7 天/30 天时重新请求数据

## 技术依赖

- 后端：Spring Boot 3 + MyBatis-Plus（现有技术栈）
- IP 归属地解析：ip2region 或类似离线库
- 前端图表：@antv/g2plot + @antv/l7（已安装）
- UI 框架：Ant Design Vue（已安装）
- 包管理：pnpm

## 不在范围内

- 实时 WebSocket 推送（使用定时轮询即可）
- 访客详细信息列表页（仅做地域聚合统计）
- 移动端适配（仪表盘主要在 PC 端使用）
