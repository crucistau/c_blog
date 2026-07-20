# 博客运营概览仪表盘 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 admin 后台首页改造为运营概览仪表盘，包含核心指标卡片、趋势图、系统监控、访客地域分布

**Architecture:** 后端新建 `t_visitor_log` 表记录访客数据 + `DashboardController` 提供聚合接口；前端将 `welcome/index.vue` 改造为 G2Plot 图表演示页面，采用网格卡片布局

**Tech Stack:** Spring Boot 3 + MyBatis-Plus + G2Plot + Ant Design Vue

---

## 文件结构一览

```
blog-backend/src/main/java/xyz/kuailemao/
├── domain/
│   ├── entity/VisitorLog.java          # 新建 — 访客记录实体
│   ├── vo/
│   │   ├── DashboardOverviewVO.java    # 新建 — 概览指标 VO
│   │   ├── TrendVO.java                # 新建 — 趋势 VO
│   │   └── RegionStatVO.java           # 新建 — 地域统计 VO
│   └── response/ResponseResult.java    # 已有
├── mapper/VisitorLogMapper.java        # 新建 — 访客记录 Mapper
├── service/
│   ├── VisitorLogService.java          # 新建 — 访客记录 Service 接口
│   └── impl/VisitorLogServiceImpl.java # 新建 — 访客记录 Service 实现
├── service/DashboardService.java       # 新建 — 仪表盘 Service 接口
├── service/impl/DashboardServiceImpl.java # 新建 — 仪表盘 Service 实现
├── controller/DashboardController.java # 新建 — 仪表盘 Controller
├── filter/VisitorLogFilter.java        # 新建 — 访客记录采集过滤器
├── constants/SecurityConst.java        # 修改 — 添加 dashboard 路径

blog-backend/src/main/resources/
└── mapper/VisitorLogMapper.xml         # 新建 — 访客记录 XML

sql/
└── v1.7.0/01_create_visitor_log.sql    # 新建 — SQL 建表

blog-frontend/kuailemao-admin/src/
├── api/blog/dashboard/index.ts         # 新建 — 仪表盘 API
├── pages/welcome/index.vue            # 修改 — 改造为仪表盘页面
├── pages/welcome/components/
│   ├── StatCard.vue                    # 新建 — 统计卡片
│   ├── VisitTrend.vue                  # 新建 — 访问量趋势图
│   ├── ArticleTrend.vue               # 新建 — 文章发布趋势图
│   ├── VisitorMap.vue                  # 新建 — 访客地域分布图
│   └── SystemMonitor.vue              # 新建 — 系统监控面板
```

---

### Task 1: 创建 SQL 建表文件

**Files:**
- Create: `sql/v1.7.0/01_create_visitor_log.sql`

- [ ] **Step 1: 创建 SQL 文件**

```sql
-- v1.7.0 访客访问日志表
CREATE TABLE IF NOT EXISTS `t_visitor_log` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `ip`          VARCHAR(64)  NOT NULL COMMENT '访问者IP',
    `address`     VARCHAR(128) DEFAULT NULL COMMENT 'IP归属地',
    `browser`     VARCHAR(64)  DEFAULT NULL COMMENT '浏览器',
    `os`          VARCHAR(64)  DEFAULT NULL COMMENT '操作系统',
    `page_url`    VARCHAR(512) DEFAULT NULL COMMENT '访问页面URL',
    `user_agent`  TEXT         DEFAULT NULL COMMENT 'User-Agent',
    `create_time` DATETIME     NOT NULL COMMENT '访问时间',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    `is_deleted`  TINYINT      DEFAULT 0 COMMENT '逻辑删除(0:未删除,1:已删除)',
    INDEX `idx_create_time` (`create_time`),
    INDEX `idx_ip` (`ip`),
    INDEX `idx_address` (`address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访客访问日志';
```

---

### Task 2: 创建 VisitorLog 实体

**Files:**
- Create: `blog-backend/src/main/java/xyz/kuailemao/domain/entity/VisitorLog.java`

- [ ] **Step 1: 创建实体类**

```java
package xyz.kuailemao.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import xyz.kuailemao.domain.BaseData;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_visitor_log")
public class VisitorLog implements Serializable, BaseData {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String ip;
    private String address;
    private String browser;
    private String os;
    private String pageUrl;
    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    private Integer isDeleted;
}
```

---

### Task 3: 创建 VisitorLogMapper

**Files:**
- Create: `blog-backend/src/main/java/xyz/kuailemao/mapper/VisitorLogMapper.java`
- Create: `blog-backend/src/main/resources/mapper/VisitorLogMapper.xml`

- [ ] **Step 1: 创建 Mapper 接口**

```java
package xyz.kuailemao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import xyz.kuailemao.domain.entity.VisitorLog;

public interface VisitorLogMapper extends BaseMapper<VisitorLog> {
}
```

- [ ] **Step 2: 创建 XML 映射文件**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="xyz.kuailemao.mapper.VisitorLogMapper">

    <resultMap id="BaseResultMap" type="xyz.kuailemao.domain.entity.VisitorLog">
        <id column="id" property="id"/>
        <result column="ip" property="ip"/>
        <result column="address" property="address"/>
        <result column="browser" property="browser"/>
        <result column="os" property="os"/>
        <result column="page_url" property="pageUrl"/>
        <result column="user_agent" property="userAgent"/>
        <result column="create_time" property="createTime"/>
        <result column="update_time" property="updateTime"/>
        <result column="is_deleted" property="isDeleted"/>
    </resultMap>

    <!-- 按省份统计访客数量 -->
    <select id="selectRegionStatistics" resultType="xyz.kuailemao.domain.vo.RegionStatVO">
        SELECT
            SUBSTRING_INDEX(address, ' ', 1) AS province,
            COUNT(*) AS count
        FROM t_visitor_log
        WHERE is_deleted = 0
          AND address IS NOT NULL
          AND address != ''
          AND address != '内网IP'
          AND address != '未知'
          <if test="days != null">
              AND create_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
          </if>
        GROUP BY province
        ORDER BY count DESC
    </select>

    <!-- 按天统计访问量趋势 -->
    <select id="selectVisitTrend" resultType="xyz.kuailemao.domain.vo.TrendVO">
        SELECT
            DATE_FORMAT(create_time, '%Y-%m-%d') AS date,
            COUNT(*) AS count
        FROM t_visitor_log
        WHERE is_deleted = 0
          AND create_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
        GROUP BY DATE(create_time)
        ORDER BY date ASC
    </select>

</mapper>
```

- [ ] **Step 3: 在 Mapper 接口中添加自定义查询方法**

更新 `VisitorLogMapper.java`:

```java
package xyz.kuailemao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import xyz.kuailemao.domain.entity.VisitorLog;
import xyz.kuailemao.domain.vo.RegionStatVO;
import xyz.kuailemao.domain.vo.TrendVO;

import java.util.List;

public interface VisitorLogMapper extends BaseMapper<VisitorLog> {

    List<RegionStatVO> selectRegionStatistics(@Param("days") Integer days);

    List<TrendVO> selectVisitTrend(@Param("days") Integer days);
}
```

---

### Task 4: 创建 VO 类

**Files:**
- Create: `blog-backend/src/main/java/xyz/kuailemao/domain/vo/DashboardOverviewVO.java`
- Create: `blog-backend/src/main/java/xyz/kuailemao/domain/vo/TrendVO.java`
- Create: `blog-backend/src/main/java/xyz/kuailemao/domain/vo/RegionStatVO.java`

- [ ] **Step 1: 创建 DashboardOverviewVO**

```java
package xyz.kuailemao.domain.vo;

import lombok.Data;

@Data
public class DashboardOverviewVO {
    private Long articleCount;
    private Long visitCount;
    private Long commentCount;
    private Long wordCount;
    private Long categoryCount;
    private Long tagCount;
}
```

- [ ] **Step 2: 创建 TrendVO**

```java
package xyz.kuailemao.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendVO {
    private String date;
    private Long count;
}
```

- [ ] **Step 3: 创建 RegionStatVO**

```java
package xyz.kuailemao.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegionStatVO {
    private String province;
    private Long count;
}
```

---

### Task 5: 创建 VisitorLogService

**Files:**
- Create: `blog-backend/src/main/java/xyz/kuailemao/service/VisitorLogService.java`
- Create: `blog-backend/src/main/java/xyz/kuailemao/service/impl/VisitorLogServiceImpl.java`

- [ ] **Step 1: 创建 Service 接口**

```java
package xyz.kuailemao.service;

import com.baomidou.mybatisplus.extension.service.IService;
import xyz.kuailemao.domain.entity.VisitorLog;
import xyz.kuailemao.domain.vo.RegionStatVO;
import xyz.kuailemao.domain.vo.TrendVO;

import java.util.List;

public interface VisitorLogService extends IService<VisitorLog> {
    List<RegionStatVO> getRegionStatistics(Integer days);
    List<TrendVO> getVisitTrend(Integer days);
    void recordVisit(VisitorLog log);
}
```

- [ ] **Step 2: 创建 Service 实现**

```java
package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xyz.kuailemao.domain.entity.VisitorLog;
import xyz.kuailemao.domain.vo.RegionStatVO;
import xyz.kuailemao.domain.vo.TrendVO;
import xyz.kuailemao.mapper.VisitorLogMapper;
import xyz.kuailemao.service.VisitorLogService;

import java.util.List;

@Slf4j
@Service
public class VisitorLogServiceImpl extends ServiceImpl<VisitorLogMapper, VisitorLog> implements VisitorLogService {

    @Resource
    private VisitorLogMapper visitorLogMapper;

    @Override
    public List<RegionStatVO> getRegionStatistics(Integer days) {
        return visitorLogMapper.selectRegionStatistics(days);
    }

    @Override
    public List<TrendVO> getVisitTrend(Integer days) {
        return visitorLogMapper.selectVisitTrend(days);
    }

    @Async
    @Override
    public void recordVisit(VisitorLog log) {
        try {
            this.save(log);
        } catch (Exception e) {
            log.error("保存访客记录失败: {}", e.getMessage());
        }
    }
}
```

---

### Task 6: 创建 VisitorLogFilter（访客采集过滤器）

**Files:**
- Create: `blog-backend/src/main/java/xyz/kuailemao/filter/VisitorLogFilter.java`

- [ ] **Step 1: 创建过滤器**

```java
package xyz.kuailemao.filter;

import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.kuailemao.domain.entity.VisitorLog;
import xyz.kuailemao.service.VisitorLogService;
import xyz.kuailemao.utils.AddressUtils;
import xyz.kuailemao.utils.BrowserUtil;
import xyz.kuailemao.utils.IpUtils;

import java.io.IOException;

/**
 * 访客记录采集过滤器
 * 拦截前台博客页面访问请求，记录访客信息
 */
@Slf4j
@Component
public class VisitorLogFilter extends OncePerRequestFilter {

    @Resource
    private VisitorLogService visitorLogService;

    // 需要记录的前台访问路径前缀
    private static final String[] FRONTEND_PATHS = {
            "/article/",
            "/comment/",
            "/category/",
            "/tag/",
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestURI = request.getRequestURI();

        // 只记录前台访问路径
        if (shouldRecord(requestURI)) {
            try {
                String ip = IpUtils.getIpAddr(request);
                VisitorLog log = VisitorLog.builder()
                        .ip(ip)
                        .address(AddressUtils.getRealAddressByIP(ip))
                        .browser(BrowserUtil.browserName(request))
                        .os(BrowserUtil.osName(request))
                        .pageUrl(requestURI)
                        .userAgent(request.getHeader("User-Agent"))
                        .build();
                visitorLogService.recordVisit(log);
            } catch (Exception e) {
                log.warn("记录访客信息异常: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldRecord(String uri) {
        // 跳过静态资源和后台管理
        if (uri.contains("/auth/") || uri.contains("/back/") || uri.startsWith("/menu")
                || uri.startsWith("/role") || uri.startsWith("/permission")
                || uri.startsWith("/monitor") || uri.startsWith("/dashboard")
                || uri.contains("swagger") || uri.contains("doc.html")) {
            return false;
        }
        for (String prefix : FRONTEND_PATHS) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
```

---

### Task 7: 创建 DashboardService + Controller

**Files:**
- Create: `blog-backend/src/main/java/xyz/kuailemao/service/DashboardService.java`
- Create: `blog-backend/src/main/java/xyz/kuailemao/service/impl/DashboardServiceImpl.java`
- Create: `blog-backend/src/main/java/xyz/kuailemao/controller/DashboardController.java`

- [ ] **Step 1: 创建 DashboardService 接口**

```java
package xyz.kuailemao.service;

import xyz.kuailemao.domain.vo.DashboardOverviewVO;
import xyz.kuailemao.domain.vo.RegionStatVO;
import xyz.kuailemao.domain.vo.TrendVO;

import java.util.List;

public interface DashboardService {
    DashboardOverviewVO getOverview();
    List<TrendVO> getVisitTrend(Integer days);
    List<TrendVO> getArticleTrend(Integer days);
    List<RegionStatVO> getRegionStatistics(Integer days);
}
```

- [ ] **Step 2: 创建 DashboardServiceImpl**

```java
package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.kuailemao.domain.entity.Article;
import xyz.kuailemao.domain.entity.Category;
import xyz.kuailemao.domain.entity.Comment;
import xyz.kuailemao.domain.entity.Tag;
import xyz.kuailemao.domain.vo.DashboardOverviewVO;
import xyz.kuailemao.domain.vo.RegionStatVO;
import xyz.kuailemao.domain.vo.TrendVO;
import xyz.kuailemao.mapper.ArticleMapper;
import xyz.kuailemao.mapper.CategoryMapper;
import xyz.kuailemao.mapper.CommentMapper;
import xyz.kuailemao.mapper.TagMapper;
import xyz.kuailemao.mapper.VisitorLogMapper;
import xyz.kuailemao.service.DashboardService;

import java.util.List;

@Service
public class DashboardServiceImpl implements DashboardService {

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private TagMapper tagMapper;

    @Resource
    private VisitorLogMapper visitorLogMapper;

    @Override
    public DashboardOverviewVO getOverview() {
        // 文章总数（已发布）
        Long articleCount = articleMapper.selectCount(
                new LambdaQueryWrapper<Article>().eq(Article::getStatus, 1));

        // 总访问量
        List<Article> articles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>().select(Article::getVisitCount));
        Long visitCount = articles.stream()
                .mapToLong(a -> a.getVisitCount() != null ? a.getVisitCount() : 0L)
                .sum();

        // 评论总数
        Long commentCount = commentMapper.selectCount(null);

        // 总字数
        Long wordCount = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getStatus, 1)
                        .select(Article::getWordCount))
                .stream()
                .mapToLong(a -> a.getWordCount() != null ? a.getWordCount() : 0L)
                .sum();

        // 分类数
        Long categoryCount = categoryMapper.selectCount(null);

        // 标签数
        Long tagCount = tagMapper.selectCount(null);

        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setArticleCount(articleCount);
        vo.setVisitCount(visitCount);
        vo.setCommentCount(commentCount);
        vo.setWordCount(wordCount);
        vo.setCategoryCount(categoryCount);
        vo.setTagCount(tagCount);
        return vo;
    }

    @Override
    public List<TrendVO> getVisitTrend(Integer days) {
        return visitorLogMapper.selectVisitTrend(days);
    }

    @Override
    public List<TrendVO> getArticleTrend(Integer days) {
        return articleMapper.selectArticleTrend(days);
    }

    @Override
    public List<RegionStatVO> getRegionStatistics(Integer days) {
        return visitorLogMapper.selectRegionStatistics(days);
    }
}
```

- [ ] **Step 3: 在 ArticleMapper 中添加趋势查询**

更新 `ArticleMapper.java`:

```java
package xyz.kuailemao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import xyz.kuailemao.domain.entity.Article;
import xyz.kuailemao.domain.vo.TrendVO;

import java.util.List;

public interface ArticleMapper extends BaseMapper<Article> {
    List<TrendVO> selectArticleTrend(@Param("days") Integer days);
}
```

- [ ] **Step 4: 在 ArticleMapper.xml 中添加趋势查询**

添加 SQL 到 `blog-backend/src/main/resources/mapper/ArticleMapper.xml`:

```xml
    <!-- 按天统计文章发布趋势 -->
    <select id="selectArticleTrend" resultType="xyz.kuailemao.domain.vo.TrendVO">
        SELECT
            DATE_FORMAT(create_time, '%Y-%m-%d') AS date,
            COUNT(*) AS count
        FROM t_article
        WHERE is_deleted = 0
          AND status = 1
          AND create_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
        GROUP BY DATE(create_time)
        ORDER BY date ASC
    </select>
```

确保 XML header 包含 `@Param` 的 import。如果 `ArticleMapper.xml` 中没有 namespace 级别引入，在参数中使用 `#{days}` 即可（MyBatis-Plus 默认支持）。

- [ ] **Step 5: 创建 DashboardController**

```java
package xyz.kuailemao.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.kuailemao.domain.ResponseResult;
import xyz.kuailemao.domain.vo.DashboardOverviewVO;
import xyz.kuailemao.domain.vo.RegionStatVO;
import xyz.kuailemao.domain.vo.TrendVO;
import xyz.kuailemao.service.DashboardService;

import java.util.List;

@Tag(name = "仪表盘")
@RestController
@RequestMapping("dashboard")
public class DashboardController {

    @Resource
    private DashboardService dashboardService;

    @Operation(summary = "获取概览指标")
    @GetMapping("/overview")
    public ResponseResult<DashboardOverviewVO> getOverview() {
        return ResponseResult.success(dashboardService.getOverview());
    }

    @Operation(summary = "获取访问量趋势")
    @GetMapping("/visitTrend")
    public ResponseResult<List<TrendVO>> getVisitTrend(@RequestParam(value = "days", defaultValue = "7") Integer days) {
        return ResponseResult.success(dashboardService.getVisitTrend(days));
    }

    @Operation(summary = "获取文章发布趋势")
    @GetMapping("/articleTrend")
    public ResponseResult<List<TrendVO>> getArticleTrend(@RequestParam(value = "days", defaultValue = "7") Integer days) {
        return ResponseResult.success(dashboardService.getArticleTrend(days));
    }

    @Operation(summary = "获取访客地域分布")
    @GetMapping("/visitor/region")
    public ResponseResult<List<RegionStatVO>> getRegionStatistics(@RequestParam(value = "days", defaultValue = "30") Integer days) {
        return ResponseResult.success(dashboardService.getRegionStatistics(days));
    }
}
```

---

### Task 8: 配置 Spring 异步支持 + Security 路径

**Files:**
- Modify: `blog-backend/src/main/java/xyz/kuailemao/config/SecurityConfiguration.java`
- Modify: `blog-backend/src/main/resources/application.yml`

- [ ] **Step 1: 启用 Spring 异步支持**

在应用主类或新建配置类中添加 `@EnableAsync`：

创建 `blog-backend/src/main/java/xyz/kuailemao/config/AsyncConfig.java`：

```java
package xyz.kuailemao.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
}
```

- [ ] **Step 2: 确认 Security 配置不需要改动**

`/dashboard/**` 路径不在 `SecurityConst.AUTH_CHECK_ARRAY` 中，但由于 Controller 方法上有 `@PreAuthorize` 权限注解，Spring Security 会自动拦截。为了让 `/dashboard/**` 受登录保护，需要在 SecurityConst 中添加：

在 `SecurityConst.java` 中添加：

```java
public static final String DASHBOARD_CHECK = "/dashboard/**";
```

并在 `AUTH_CHECK_ARRAY` 中添加 `DASHBOARD_CHECK`。

- [ ] **Step 3: 配置异步线程池（可选）**

在 `application.yml` 中添加：

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 2
        max-size: 5
        queue-capacity: 100
```

---

### Task 9: 创建前端 Dashboard API 模块

**Files:**
- Create: `blog-frontend/kuailemao-admin/src/api/blog/dashboard/index.ts`

- [ ] **Step 1: 创建 API 文件**

```typescript
// 仪表盘数据接口
import { message } from 'ant-design-vue'

export interface DashboardOverview {
  articleCount: number
  visitCount: number
  commentCount: number
  wordCount: number
  categoryCount: number
  tagCount: number
}

export interface TrendItem {
  date: string
  count: number
}

export interface RegionStat {
  province: string
  count: number
}

// 获取概览指标
export async function getDashboardOverview() {
  return useGet<DashboardOverview>('/dashboard/overview').catch(msg => message.warn(msg))
}

// 获取访问量趋势
export async function getVisitTrend(days: number = 7) {
  return useGet<TrendItem[]>('/dashboard/visitTrend', null, {
    params: { days },
  }).catch(msg => message.warn(msg))
}

// 获取文章发布趋势
export async function getArticleTrend(days: number = 7) {
  return useGet<TrendItem[]>('/dashboard/articleTrend', null, {
    params: { days },
  }).catch(msg => message.warn(msg))
}

// 获取访客地域分布
export async function getVisitorRegion(days: number = 30) {
  return useGet<RegionStat[]>('/dashboard/visitor/region', null, {
    params: { days },
  }).catch(msg => message.warn(msg))
}
```

---

### Task 10: 创建 StatCard 组件

**Files:**
- Create: `blog-frontend/kuailemao-admin/src/pages/welcome/components/StatCard.vue`

- [ ] **Step 1: 创建统计卡片组件**

```vue
<script setup lang="ts">
defineProps<{
  title: string
  value: number | string
  gradient: string
  icon: string
}>()
</script>

<template>
  <div
    class="stat-card"
    :style="{ background: `linear-gradient(135deg, ${gradient})` }"
  >
    <div class="stat-icon">
      <component :is="icon" />
    </div>
    <div class="stat-info">
      <div class="stat-title">{{ title }}</div>
      <div class="stat-value">{{ value.toLocaleString() }}</div>
    </div>
  </div>
</template>

<style scoped lang="less">
.stat-card {
  border-radius: 8px;
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  color: #fff;
  transition: transform 0.3s, box-shadow 0.3s;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  }
}

.stat-icon {
  font-size: 32px;
  opacity: 0.9;
}

.stat-info {
  flex: 1;
}

.stat-title {
  font-size: 14px;
  opacity: 0.85;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}
</style>
```

---

### Task 11: 创建 VisitTrend 组件（G2Plot 折线图）

**Files:**
- Create: `blog-frontend/kuailemao-admin/src/pages/welcome/components/VisitTrend.vue`

- [ ] **Step 1: 创建组件**

```vue
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Line } from '@antv/g2plot'
import type { TrendItem } from '~/api/blog/dashboard'

const props = defineProps<{
  data: TrendItem[]
  days: number
}>()

const emit = defineEmits(['update:days'])
const containerRef = ref<HTMLDivElement>()

let plot: Line | null = null

function renderChart() {
  if (!containerRef.value || !props.data.length) return

  plot?.destroy()

  plot = new Line(containerRef.value, {
    data: props.data,
    xField: 'date',
    yField: 'count',
    smooth: true,
    color: '#1677ff',
    lineStyle: { lineWidth: 2 },
    point: { size: 3, shape: 'circle' },
    xAxis: {
      label: {
        formatter: (text: string) => text.slice(5),
      },
    },
    yAxis: {
      min: 0,
    },
    tooltip: {
      formatter: (datum: any) => ({
        name: '访问量',
        value: datum.count,
      }),
    },
    animation: { appear: { animation: 'wave-in' } },
  })

  plot.render()
}

onMounted(renderChart)
watch(() => props.data, renderChart, { deep: true })
</script>

<template>
  <div class="trend-card">
    <div class="trend-header">
      <div class="trend-title">
        <LineChartOutlined /> 访问量趋势
      </div>
      <div class="trend-tabs">
        <a-button
          :type="days === 7 ? 'primary' : 'default'"
          size="small"
          @click="emit('update:days', 7)"
        >7天</a-button>
        <a-button
          :type="days === 30 ? 'primary' : 'default'"
          size="small"
          @click="emit('update:days', 30)"
        >30天</a-button>
      </div>
    </div>
    <div ref="containerRef" class="chart-container" />
  </div>
</template>

<style scoped lang="less">
.trend-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  padding: 16px;
}

.trend-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.trend-title {
  font-size: 16px;
  font-weight: 600;
}

.trend-tabs {
  display: flex;
  gap: 8px;
}

.chart-container {
  height: 280px;
}
</style>
```

---

### Task 12: 创建 ArticleTrend 组件（G2Plot 柱状图）

**Files:**
- Create: `blog-frontend/kuailemao-admin/src/pages/welcome/components/ArticleTrend.vue`

- [ ] **Step 1: 创建组件**

```vue
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Column } from '@antv/g2plot'
import type { TrendItem } from '~/api/blog/dashboard'

const props = defineProps<{
  data: TrendItem[]
  days: number
}>()

const emit = defineEmits(['update:days'])
const containerRef = ref<HTMLDivElement>()

let plot: Column | null = null

function renderChart() {
  if (!containerRef.value || !props.data.length) return

  plot?.destroy()

  plot = new Column(containerRef.value, {
    data: props.data,
    xField: 'date',
    yField: 'count',
    color: '#52c41a',
    columnStyle: { radius: [4, 4, 0, 0] },
    xAxis: {
      label: {
        formatter: (text: string) => text.slice(5),
      },
    },
    yAxis: { min: 0 },
    tooltip: {
      formatter: (datum: any) => ({
        name: '发布数',
        value: datum.count,
      }),
    },
    animation: { appear: { animation: 'scale-in-y' } },
  })

  plot.render()
}

onMounted(renderChart)
watch(() => props.data, renderChart, { deep: true })
</script>

<template>
  <div class="trend-card">
    <div class="trend-header">
      <div class="trend-title">
        <BarChartOutlined /> 文章发布趋势
      </div>
      <div class="trend-tabs">
        <a-button
          :type="days === 7 ? 'primary' : 'default'"
          size="small"
          @click="emit('update:days', 7)"
        >7天</a-button>
        <a-button
          :type="days === 30 ? 'primary' : 'default'"
          size="small"
          @click="emit('update:days', 30)"
        >30天</a-button>
      </div>
    </div>
    <div ref="containerRef" class="chart-container" />
  </div>
</template>

<style scoped lang="less">
.trend-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  padding: 16px;
}

.trend-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.trend-title {
  font-size: 16px;
  font-weight: 600;
}

.trend-tabs {
  display: flex;
  gap: 8px;
}

.chart-container {
  height: 280px;
}
</style>
```

---

### Task 13: 创建 VisitorMap 组件（G2Plot 柱状图展示地域排名）

**Files:**
- Create: `blog-frontend/kuailemao-admin/src/pages/welcome/components/VisitorMap.vue`

- [ ] **Step 1: 创建组件**

使用横向条形图展示各省份访客数量排名，按数量降序排列（直观展示地域分布，不需要地图 API Key）：

```vue
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Bar } from '@antv/g2plot'
import type { RegionStat } from '~/api/blog/dashboard'

const props = defineProps<{
  data: RegionStat[]
  days: number
}>()

const emit = defineEmits(['update:days'])
const containerRef = ref<HTMLDivElement>()

let plot: Bar | null = null

function renderChart() {
  if (!containerRef.value || !props.data.length) return

  plot?.destroy()

  // 取 top 15
  const chartData = props.data.slice(0, 15).reverse()

  plot = new Bar(containerRef.value, {
    data: chartData,
    xField: 'count',
    yField: 'province',
    seriesField: 'province',
    color: '#1677ff',
    barStyle: { radius: [0, 4, 4, 0] },
    xAxis: { min: 0 },
    label: {
      position: 'right',
      formatter: (datum: any) => `${datum.count}`,
    },
    tooltip: {
      formatter: (datum: any) => ({
        name: '访客数',
        value: datum.count,
      }),
    },
    animation: { appear: { animation: 'fade-in' } },
  })

  plot.render()
}

onMounted(renderChart)
watch(() => props.data, renderChart, { deep: true })
</script>

<template>
  <div class="map-card">
    <div class="map-header">
      <div class="map-title">
        <EnvironmentOutlined /> 访客地域分布
      </div>
      <div class="map-tabs">
        <a-button
          :type="days === 7 ? 'primary' : 'default'"
          size="small"
          @click="emit('update:days', 7)"
        >7天</a-button>
        <a-button
          :type="days === 30 ? 'primary' : 'default'"
          size="small"
          @click="emit('update:days', 30)"
        >30天</a-button>
      </div>
    </div>
    <div ref="containerRef" class="chart-container" />
  </div>
</template>

<style scoped lang="less">
.map-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  padding: 16px;
}

.map-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.map-title {
  font-size: 16px;
  font-weight: 600;
}

.map-tabs {
  display: flex;
  gap: 8px;
}

.chart-container {
  height: 280px;
}
</style>
```

---

### Task 14: 创建 SystemMonitor 组件

**Files:**
- Create: `blog-frontend/kuailemao-admin/src/pages/welcome/components/SystemMonitor.vue`

- [ ] **Step 1: 创建系统监控面板**

```vue
<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { getServiceMonitorData } from '~/api/server'

interface SysInfo {
  cpu: { cpuNum: number; used: number; sys: number; free: number }
  mem: { total: number; used: number; free: number }
  jvm: { total: number; free: number }
  sys: { computerName: string; computerIp: string }
  sysFiles: { usage: number }[]
}

const serverInfo = ref<SysInfo>()
let timer: ReturnType<typeof setInterval> | undefined

async function fetchData() {
  const { data } = await getServiceMonitorData()
  serverInfo.value = data
}

onMounted(() => {
  fetchData()
  timer = setInterval(fetchData, 30000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

function memUsage(): string {
  if (!serverInfo.value) return '0'
  const m = serverInfo.value.mem
  return ((m.used / m.total) * 100).toFixed(1)
}

function jvmUsage(): string {
  if (!serverInfo.value) return '0'
  const j = serverInfo.value.jvm
  return (((j.total - j.free) / j.total) * 100).toFixed(1)
}

function diskUsage(): string {
  if (!serverInfo.value?.sysFiles?.length) return '0'
  const max = Math.max(...serverInfo.value.sysFiles.map(f => f.usage))
  return max.toFixed(1)
}
</script>

<template>
  <div class="monitor-card">
    <div class="monitor-title">
      <DashboardOutlined /> 系统状态
    </div>
    <div class="monitor-body">
      <template v-if="serverInfo">
        <div class="monitor-item">
          <div class="item-label">
            <span>CPU</span>
            <span class="item-value">{{ serverInfo.cpu.used }}%</span>
          </div>
          <a-progress :percent="serverInfo.cpu.used" :show-info="false" size="small" stroke-color="#52c41a" />
        </div>
        <div class="monitor-item">
          <div class="item-label">
            <span>内存</span>
            <span class="item-value">{{ memUsage() }}%</span>
          </div>
          <a-progress :percent="Number(memUsage())" :show-info="false" size="small" stroke-color="#faad14" />
        </div>
        <div class="monitor-item">
          <div class="item-label">
            <span>JVM</span>
            <span class="item-value">{{ jvmUsage() }}%</span>
          </div>
          <a-progress :percent="Number(jvmUsage())" :show-info="false" size="small" stroke-color="#1677ff" />
        </div>
        <div class="monitor-item">
          <div class="item-label">
            <span>磁盘</span>
            <span class="item-value">{{ diskUsage() }}%</span>
          </div>
          <a-progress :percent="Number(diskUsage())" :show-info="false" size="small" stroke-color="#ff4d4f" />
        </div>
        <div class="monitor-footer">
          <div class="server-info">服务器: {{ serverInfo.sys.computerName }}</div>
          <div class="server-info">IP: {{ serverInfo.sys.computerIp }}</div>
        </div>
      </template>
      <a-skeleton v-else :paragraph="{ rows: 4 }" />
    </div>
  </div>
</template>

<style scoped lang="less">
.monitor-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  padding: 16px;
}

.monitor-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 20px;
}

.monitor-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.monitor-item {
  .item-label {
    display: flex;
    justify-content: space-between;
    margin-bottom: 6px;
    font-size: 13px;
    color: #333;
  }
  .item-value {
    font-weight: 600;
    color: #666;
  }
}

.monitor-footer {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;

  .server-info {
    font-size: 12px;
    color: #999;
    line-height: 1.8;
  }
}
</style>
```

---

### Task 15: 改造首页 welcome/index.vue

**Files:**
- Modify: `blog-frontend/kuailemao-admin/src/pages/welcome/index.vue`

- [ ] **Step 1: 替换为仪表盘页面**

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import StatCard from './components/StatCard.vue'
import VisitTrend from './components/VisitTrend.vue'
import ArticleTrend from './components/ArticleTrend.vue'
import VisitorMap from './components/VisitorMap.vue'
import SystemMonitor from './components/SystemMonitor.vue'
import {
  getDashboardOverview,
  getVisitTrend,
  getArticleTrend,
  getVisitorRegion,
} from '~/api/blog/dashboard'
import { getServiceMonitorData } from '~/api/server'
import type { DashboardOverview, TrendItem, RegionStat } from '~/api/blog/dashboard'

const overview = ref<DashboardOverview>()
const visitTrend = ref<TrendItem[]>([])
const articleTrend = ref<TrendItem[]>([])
const regionData = ref<RegionStat[]>([])
const loading = ref(true)

const visitDays = ref(7)
const articleDays = ref(7)
const regionDays = ref(30)

async function loadOverview() {
  const { data } = await getDashboardOverview()
  overview.value = data
}

async function loadVisitTrend(days: number) {
  visitDays.value = days
  const { data } = await getVisitTrend(days)
  visitTrend.value = data ?? []
}

async function loadArticleTrend(days: number) {
  articleDays.value = days
  const { data } = await getArticleTrend(days)
  articleTrend.value = data ?? []
}

async function loadRegion(days: number) {
  regionDays.value = days
  const { data } = await getVisitorRegion(days)
  regionData.value = data ?? []
}

onMounted(async () => {
  await Promise.all([
    loadOverview(),
    loadVisitTrend(7),
    loadArticleTrend(7),
    loadRegion(30),
  ])
  loading.value = false
})
</script>

<template>
  <div class="dashboard">
    <!-- 第一行：核心指标卡片 -->
    <div class="row stat-row">
      <StatCard
        title="文章总数"
        :value="overview?.articleCount ?? 0"
        gradient="#667eea, #764ba2"
        icon="FileTextOutlined"
      />
      <StatCard
        title="总访问量"
        :value="overview?.visitCount ?? 0"
        gradient="#f093fb, #f5576c"
        icon="EyeOutlined"
      />
      <StatCard
        title="评论数"
        :value="overview?.commentCount ?? 0"
        gradient="#4facfe, #00f2fe"
        icon="MessageOutlined"
      />
      <StatCard
        title="总字数"
        :value="overview?.wordCount ?? 0"
        gradient="#43e97b, #38f9d7"
        icon="FileTextOutlined"
      />
    </div>

    <!-- 第二行：趋势图 + 系统监控 (2:1) -->
    <div class="row chart-row">
      <div class="chart-main">
        <VisitTrend
          :data="visitTrend"
          :days="visitDays"
          @update:days="loadVisitTrend"
        />
      </div>
      <div class="chart-side">
        <SystemMonitor />
      </div>
    </div>

    <!-- 第三行：地域分布 + 文章趋势 (1:1) -->
    <div class="row chart-row">
      <div class="chart-half">
        <VisitorMap
          :data="regionData"
          :days="regionDays"
          @update:days="loadRegion"
        />
      </div>
      <div class="chart-half">
        <ArticleTrend
          :data="articleTrend"
          :days="articleDays"
          @update:days="loadArticleTrend"
        />
      </div>
    </div>
  </div>
</template>

<style scoped lang="less">
.dashboard {
  padding: 0;
}

.row {
  margin-bottom: 20px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.chart-row {
  display: flex;
  gap: 16px;
}

.chart-main {
  flex: 2;
}

.chart-side {
  flex: 1;
  min-width: 280px;
}

.chart-half {
  flex: 1;
}
</style>
```

---

### Task 16: verify backend compilation + frontend build

- [ ] **Step 1: 后端编译检查**

```bash
cd blog-backend
mvn compile -q
```
Expected: BUILD SUCCESS (无错误)

- [ ] **Step 2: 前端类型检查**

```bash
cd blog-frontend/kuailemao-admin
pnpm typecheck
```
Expected: 无类型错误

- [ ] **Step 3: 前端构建**

```bash
pnpm build
```
Expected: 构建成功
