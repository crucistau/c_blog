# Elasticsearch 搜索迁移实施规格文档

> **项目**: C-Blog 后端搜索系统升级
> **状态**: 待实施

## 修订历史

| 版本 | 日期 | 变更摘要 |
|------|------|---------|
| **v2** | **2026-07-20** | 多实体综合搜索；Java 客户端改用官方 `elasticsearch-java`；ES 版本升至 8.18.0；连接改为 HTTPS + 账号密码（信任自签 CA）；查询源开关改为「总开关 + 按实体子开关 + 策略模式」；新增运行时自动回退；同步消息统一为多实体 `EsSyncMessage`；移除本地 docker-compose（服务器已有 ES） |
| v1 | 2026-06-01 | 仅文章搜索；Spring Data Elasticsearch 5.1.x；ES 8.12；HTTP 无认证；单一全局开关 + Controller 三元 |

---

## 0. v1 → v2 关键变更（速览）

| 维度 | v1 | v2 | 变更类型 |
|------|----|----|---------|
| 搜索范围 | 仅文章 | 文章 / 留言 / 标签 / 分类（独立多索引） | 扩展 |
| ES 版本 | 8.12.x | **8.18.0**（与服务器一致） | 升级 |
| Java 客户端 | Spring Data Elasticsearch 5.1.x | **官方 `elasticsearch-java` 8.18.0 + `elasticsearch-rest-client`** | 替换 |
| 连接/认证 | HTTP 无认证（`xpack.security.enabled=false`） | **HTTPS + 账号密码 + 信任自签 CA 证书** | 变更 |
| 查询源开关 | 单一全局开关，Controller 内三元 `?:` | **总开关 + 按实体子开关，策略模式集中路由** | 变更 |
| 运行时降级 | 无（仅开关级） | **新增 ES 异常自动回退 MySQL** | 新增 |
| 同步消息 | `ArticleSyncMessage`（仅文章） | **统一 `EsSyncMessage`（带 `entityType`）** | 变更 |
| ES 部署 | 本地 docker-compose 自建 | **服务器现成 8.18.0，compose 删除** | 变更 |
| IK 分词器 | 8.12.x | **8.18.0** | 升级 |
| RabbitMQ 同步 | 复用 | 复用 | 不变 |

---

## 1. 概述

### 1.1 背景

当前 C-Blog 后端博客搜索基于 MySQL `LIKE` 模糊查询实现，存在性能瓶颈、不支持中文分词、缺乏相关性排序与高亮。本次引入 Elasticsearch 8.18.0 作为搜索引擎，覆盖**文章、留言、标签、分类**四类实体，提供站内综合搜索能力。服务器已部署 8.18.0 且开启安全认证（HTTPS + 账号密码）。

### 1.2 目标

- 支持中文分词全文搜索（IK 分词器）
- 支持**多实体综合搜索**（一个入口检索文章/留言/标签/分类）
- 支持搜索结果高亮显示
- 支持标题搜索建议（自动补全）
- 支持多条件组合搜索
- 保持与现有接口的入参/返回格式兼容
- 通过 RabbitMQ 实现多实体增量同步
- **配置驱动**：通过 `application.yml` 开关在 ES 与 MySQL 之间切换，且支持三级降级

### 1.3 约束

- 不改变 MySQL 作为主数据存储的地位，ES 仅作为搜索引擎
- 保持现有接口路径、参数、返回值格式不变
- 渐进式迁移，保留随时降级能力
- **客户端版本严格对齐服务器 8.18.0**，不使用 Spring Boot BOM 管理的旧版本

---

## 2. 技术选型

| 组件 | 版本 | 说明 |
|------|------|------|
| Elasticsearch | **8.18.0**（服务器现有） | 搜索引擎，HTTPS + 账号密码 |
| `co.elastic.clients:elasticsearch-java` | **8.18.0** | 官方 Java 客户端，提供类型安全的 `ElasticsearchClient` |
| `org.elasticsearch.client:elasticsearch-rest-client` | **8.18.0** | 底层传输（处理 HTTPS/SSL/认证） |
| IK Analysis 插件 | **8.18.0** | 中文分词，需在服务器 ES 安装 |
| RabbitMQ | 已有 | 多实体增量同步 |
| Jackson | 已有（spring-boot-starter-web 传递） | ES 客户端 JSON 映射 |

> **为何不用 Spring Data Elasticsearch？** Spring Boot 3.1.4 BOM 管理的 ES 客户端是 8.11.x，与服务器 8.18.0 存在版本漂移。直接使用官方 `elasticsearch-java` 8.18.0 可严格对齐，且多索引 `bool` 综合查询的表达力更强，与项目现有 MyBatis-Plus 显式查询风格一致。

---

## 3. 整体架构

### 3.1 组件关系

```
┌─────────────┐   ┌──────────────────┐   ┌─────────────────────┐
│ Controller  │──▶│   SearchRouter   │──▶│ ArticleSearch(ES/DB) │
│ (不感知来源) │   │ (总开关+子开关)   │   │ LeaveWordSearch(...) │
└─────────────┘   └──────────────────┘   │ TagSearch(...)       │
                                          │ CategorySearch(...)  │
                  ┌──────────────────┐   └─────────────────────┘
                  │  EsSyncListener  │   ▲  ES 实现内 try-catch
                  │  (统一 MQ 入口)   │   │  异常自动回退 DB 实现
                  └────────┬─────────┘   │
                           │             │  写入路径
   Service 写 MySQL 成功后 ─┼─发 EsSyncMessage──▶ RabbitMQ
                           ▼
                  ┌──────────────────┐
                  │ DocumentBuilder  │ (每实体一个，MySQL→ES 文档)
                  │ + ElasticsearchClient │
                  └──────────────────┘
```

### 3.2 读数据流（搜索）

`Controller → SearchRouter.resolve(entityType) → 命中的 ArticleSearch(ES 或 DB 实现) → 结果 VO`

路由规则：`search.enabled=true` 且 `search.entities.<entity>=true` 且 ES 实现可用 → 走 ES；否则走 DB 实现。ES 实现内部捕获异常，按 `search.fallback-on-error` 决定是否回退 DB。

### 3.3 写数据流（同步）

`Service 写 MySQL 成功 → 发 EsSyncMessage 到 RabbitMQ → EsSyncListener 消费 → 按 entityType 分发到对应 DocumentBuilder → ElasticsearchClient 写入/删除索引`

---

## 4. 索引设计（独立多索引）

### 4.1 索引清单

| 索引名 | 实体 | MySQL 表 | 默认启用 |
|--------|------|---------|---------|
| `blog_article` | 文章 | `t_article` | ✅ |
| `blog_leave_word` | 留言 | `t_leave_word` | ✅ |
| `blog_tag` | 标签 | `t_tag` | ❌（数据量小，默认关，可按需开） |
| `blog_category` | 分类 | `t_category` | ❌（同上） |

> **设计取舍**：各实体字段差异大，独立索引避免字段稀疏、分词策略可各自最优。综合搜索通过 `ElasticsearchClient` 的 **multi-index 查询**（一次请求搜多个索引）+ 应用层按实体类型归并实现。标签/分类数据量小，LIKE 足够，默认不索引；纳入综合搜索时可通过子开关按需启用。

### 4.2 `blog_article` mapping（沿用 v1，IK 分词）

```json
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "ik_smart_analyzer": { "type": "custom", "tokenizer": "ik_smart" },
        "ik_max_word_analyzer": { "type": "custom", "tokenizer": "ik_max_word" }
      }
    }
  },
  "mappings": { "properties": {
    "id":            { "type": "long" },
    "userId":        { "type": "long" },
    "username":      { "type": "keyword" },
    "categoryId":    { "type": "long" },
    "categoryName":  { "type": "keyword", "fields": { "text": { "type": "text", "analyzer": "ik_max_word" } } },
    "articleCover":  { "type": "keyword", "index": false },
    "articleTitle":  { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart", "fields": { "keyword": { "type": "keyword" } } },
    "articleContent":{ "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
    "articleType":   { "type": "integer" },
    "isTop":         { "type": "integer" },
    "status":        { "type": "integer" },
    "visitCount":    { "type": "long" },
    "tags":          { "type": "keyword", "fields": { "text": { "type": "text", "analyzer": "ik_max_word" } } },
    "createTime":    { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },
    "updateTime":    { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" }
  }}
}
```

> `username` / `categoryName` / `tags` 需在 `DocumentBuilder` 中关联查询 `t_user` / `t_category` / `t_tag`（经 `t_article_tag`）后冗余写入索引，避免搜索时二次关联。

### 4.3 `blog_leave_word` mapping

```json
{
  "settings": { "number_of_shards": 1, "number_of_replicas": 0,
    "analysis": { "analyzer": { "ik_max_word_analyzer": { "type": "custom", "tokenizer": "ik_max_word" } } }
  },
  "mappings": { "properties": {
    "id":         { "type": "long" },
    "userId":     { "type": "long" },
    "username":   { "type": "keyword" },
    "content":    { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
    "isCheck":    { "type": "integer" },
    "createTime": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },
    "updateTime": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" }
  }}
}
```

### 4.4 `blog_tag` mapping

```json
{
  "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
  "mappings": { "properties": {
    "id":         { "type": "long" },
    "tagName":    { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart", "fields": { "keyword": { "type": "keyword" } } },
    "createTime": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },
    "updateTime": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" }
  }}
}
```

### 4.5 `blog_category` mapping

```json
{
  "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
  "mappings": { "properties": {
    "id":           { "type": "long" },
    "categoryName": { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart", "fields": { "keyword": { "type": "keyword" } } },
    "createTime":   { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },
    "updateTime":   { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" }
  }}
}
```

### 4.6 IK 分词器

- **必须在服务器 ES 8.18.0 安装 IK 插件 8.18.0**，否则中文按单字拆分，搜索效果极差。
- 索引分词用 `ik_max_word`（细粒度，召回高），搜索分词用 `ik_smart`（粗粒度，精确）。
- mapping JSON 文件统一存放 `src/main/resources/es/`，全量重建时由代码读取并 `CreateIndex`。

---

## 5. 依赖与配置

### 5.1 `pom.xml`

在 `<dependencies>` 新增（不引入 `spring-boot-starter-data-elasticsearch`）：

```xml
<!-- Elasticsearch 官方客户端（与服务器 8.18.0 严格对齐） -->
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.18.0</version>
</dependency>
<dependency>
    <groupId>org.elasticsearch.client</groupId>
    <artifactId>elasticsearch-rest-client</artifactId>
    <version>8.18.0</version>
</dependency>
<!-- jackson-databind 已由 spring-boot-starter-web 传递引入，无需重复声明 -->
```

> 因不引入 spring-data-elasticsearch，Spring Boot BOM 不会强加 ES 客户端版本，可直接锁定 8.18.0。

### 5.2 `application.yml`

新增独立命名空间 `elasticsearch:` 与 `search:`（不使用 `spring.elasticsearch.*`，避免触发 Spring Data ES 自动配置）：

```yaml
elasticsearch:
  uris: https://141.98.198.67:9200
  username: elastic
  password: ${ES_PASSWORD:changeme}          # 通过环境变量注入，禁止明文入库
  ca-cert-path: classpath:es/http_ca.crt      # 服务器 ES 自签 CA 证书（见 §10 获取方式）
  connect-timeout: 5000
  socket-timeout: 30000

search:
  enabled: true                    # 总开关：false 则全部走 MySQL，且不装配 ES 客户端
  fallback-on-error: true          # ES 查询异常时是否自动回退 MySQL
  entities:                        # 按实体子开关（仅当 search.enabled=true 时生效）
    article: true
    leave-word: true
    tag: false                     # 数据量小，默认不索引
    category: false

spring:
  rabbitmq:
    # ... 现有 email/log 配置保持不变 ...
    queue:
      email: email_queue
      log-login: log_login_queue
      log-system: log_system_queue
      es-sync: es_sync_queue         # 新增：ES 统一同步队列
    exchange:
      email: email_exchange
      log: log_exchange
      es: es_exchange                # 新增：ES 同步交换机
    routingKey:
      email: email_routing_key
      log-login: log_routing_key_login
      log-system: log_routing_key_system
      es-sync: es_sync_routing_key   # 新增：ES 同步路由键
```

### 5.3 `ElasticsearchConfig.java`（官方客户端 + HTTPS + 自签证书）

**路径**: `src/main/java/xyz/kuailemao/config/ElasticsearchConfig.java`

```java
@Configuration
@ConditionalOnProperty(name = "search.enabled", havingValue = "true", matchIfMissing = true)
@ConfigurationProperties(prefix = "elasticsearch")
@Data
public class ElasticsearchConfig {

    private String uris;
    private String username;
    private String password;
    private String caCertPath;       // classpath:es/http_ca.crt
    private int connectTimeout = 5000;
    private int socketTimeout = 30000;

    @Bean
    public RestClient restClient() throws Exception {
        // 1. 信任服务器自签 CA 证书（纯 JDK API，不依赖具体 httpclient 版本）
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Resource caRes = new DefaultResourceLoader().getResource(caCertPath);
        try (InputStream is = caRes.getInputStream()) {
            trustStore.setCertificateEntry("es-ca", cf.generateCertificate(is));
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        // 2. basic auth（elasticsearch-rest-client 8.x 基于 Apache HttpClient 5）
        BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(username, password.toCharArray()));

        // 3. 构建 RestClient
        return RestClient.builder(HttpHost.create(uris))
                .setHttpClientConfigCallback(hc -> hc
                        .setSSLContext(sslContext)
                        .setDefaultCredentialsProvider(creds))
                .setRequestConfigCallback(rc -> rc
                        .setConnectTimeout(connectTimeout)
                        .setSocketTimeout(socketTimeout))
                .build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        ElasticsearchTransport transport =
                new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
```

**要点**：
- `@ConditionalOnProperty(search.enabled=true)`：总开关关闭时**整个 ES 客户端不装配**，应用可在无 ES 环境正常启动。
- 自签证书采用 **truststore 模式**（信任 CA），生产推荐；纯 JDK API 构建 `SSLContext`，避免 httpclient 4/5 版本差异。
- 账号密码走环境变量 `ES_PASSWORD`，禁止明文入库（遵守安全规范）。

---

## 6. 查询源开关（策略模式 + 总/子开关）

### 6.1 配置项语义

| 配置项 | 含义 |
|--------|------|
| `search.enabled` | 总开关。`false`：全部走 MySQL，且不装配 ES 客户端 |
| `search.entities.<entity>` | 实体子开关。仅当总开关为 `true` 时生效；`false` 则该实体走 MySQL |
| `search.fallback-on-error` | 运行时开关。`true`：ES 查询异常自动回退 MySQL |

### 6.2 策略接口与路由

**搜索策略接口**（按实体维度，ES 与 DB 实现共用契约）：

```java
public interface ArticleSearch {
    List<SearchArticleByContentVO> searchByContent(String keyword);
    List<InitSearchTitleVO> initSearchByTitle();
    List<ArticleListVO> searchArticles(SearchArticleDTO dto);
}
// LeaveWordSearch / TagSearch / CategorySearch 同理定义各自方法
```

**ES 实现**（条件装配）：

```java
@Component
@ConditionalOnProperty(name = "search.enabled", havingValue = "true", matchIfMissing = true)
public class ArticleEsSearch implements ArticleSearch {
    @Resource private ElasticsearchClient esClient;
    @Resource private ObjectProvider<ArticleSearch> dbImplProvider;  // 用于回退
    @Value("${search.fallback-on-error:true}") private boolean fallbackOnError;
    // 用 esClient.search(...) 实现，方法体 try-catch，失败时按 fallbackOnError 回退
}
```

**DB 实现**（委托现有 `ArticleServiceImpl` 的 LIKE 方法，零改动）：

```java
@Component
public class ArticleDbSearch implements ArticleSearch {
    @Resource private ArticleService articleService;   // 复用现有实现
    // 各方法委托给 articleService.searchArticleByContent / initSearchByTitle / searchArticle
}
```

**集中路由**（开关逻辑收敛于此，Controller 不感知来源）：

```java
@Component
public class SearchRouter {
    @Value("${search.enabled:true}") private boolean enabled;
    @Value("${search.entities.article:true}") private boolean articleEnabled;
    @Value("${search.entities.leave-word:true}") private boolean leaveWordEnabled;
    @Value("${search.entities.tag:false}") private boolean tagEnabled;
    @Value("${search.entities.category:false}") private boolean categoryEnabled;

    @Resource(name = "articleEsSearch") private ObjectProvider<ArticleSearch> articleEs;
    @Resource private ArticleDbSearch articleDb;
    // 其余实体同理...

    public ArticleSearch article() {
        return (enabled && articleEnabled) ? articleEs.getIfAvailable(articleDb) : articleDb;
    }
    // leaveWord() / tag() / category() 同理
}
```

> ES 实现用 `ObjectProvider` 可选注入：总开关关闭时 ES bean 不存在，`getIfAvailable(fallback)` 自动取 DB 实现，避免启动期注入失败。

### 6.3 运行时自动回退

ES 实现的每个方法体包 `try { esClient.search(...) } catch (Exception e) { if (fallbackOnError) return dbImpl.x(); else throw e; }`。`fallback-on-error=false` 时异常上抛，便于排错。

---

## 7. 数据同步（统一 MQ）

### 7.1 `EsSyncMessage.java`

**路径**: `src/main/java/xyz/kuailemao/domain/dto/EsSyncMessage.java`

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EsSyncMessage implements Serializable {
    private EntityType entityType;   // ARTICLE / LEAVE_WORD / TAG / CATEGORY
    private Long id;
    private SyncType syncType;       // SAVE / UPDATE / DELETE

    public enum EntityType { ARTICLE, LEAVE_WORD, TAG, CATEGORY }
    public enum SyncType { SAVE, UPDATE, DELETE }
}
```

### 7.2 `EsSyncListener.java`（统一入口）

**路径**: `src/main/java/xyz/kuailemao/listener/EsSyncListener.java`

```java
@Component @Slf4j
public class EsSyncListener {
    @Resource private ElasticsearchClient esClient;
    private final Map<EntityType, DocumentBuilder> builders;

    @Autowired  // Spring 注入所有 DocumentBuilder bean，组装为按 EntityType 索引的 Map
    public EsSyncListener(List<DocumentBuilder> list) {
        this.builders = list.stream()
                .collect(Collectors.toMap(DocumentBuilder::type, b -> b));
    }

    @RabbitListener(queues = RabbitConst.ES_SYNC_QUEUE,
                    errorHandler = "rabbitListenerErrorHandler")
    public void handle(EsSyncMessage msg) {
        log.info("ES 同步消息: entity={}, id={}, type={}", msg.getEntityType(), msg.getId(), msg.getSyncType());
        DocumentBuilder builder = builders.get(msg.getEntityType());
        switch (msg.getSyncType()) {
            case SAVE, UPDATE -> builder.upsert(msg.getId());
            case DELETE       -> builder.delete(msg.getId());
        }
    }
}
```

> 复用现有 `rabbitListenerErrorHandler` + `application.yml` 的重试策略（max-attempts: 3，间隔递增），失败入死信。

### 7.3 `DocumentBuilder<?>`（每实体一个）

**路径**: `src/main/java/xyz/kuailemao/search/builder/{Article,LeaveWord,Tag,Category}DocumentBuilder.java`

每个 builder 实现：

```java
public interface DocumentBuilder {
    EntityType type();
    String indexName();
    void upsert(Long id);   // MySQL 查关联数据 → 构建 Map → esClient.index(...)
    void delete(Long id);   // esClient.delete(...)
}
```

`ArticleDocumentBuilder.upsert` 示例（关联查询 username/categoryName/tags，冗余写入索引）：

```java
public void upsert(Long id) {
    Article a = articleMapper.selectById(id);
    if (a == null || a.getIsDeleted() == 1) { esClient.delete(d -> d.index(indexName()).id(String.valueOf(id))); return; }
    String username   = userMapper.selectById(a.getUserId()).getUsername();
    String categoryName = categoryMapper.selectById(a.getCategoryId()).getCategoryName();
    List<String> tags = /* 经 t_article_tag 关联 t_tag 查询 tagName 列表 */;
    Map<String, Object> doc = new HashMap<>();
    doc.put("id", a.getId()); doc.put("userId", a.getUserId()); doc.put("username", username);
    doc.put("categoryId", a.getCategoryId()); doc.put("categoryName", categoryName);
    doc.put("articleCover", a.getArticleCover()); doc.put("articleTitle", a.getArticleTitle());
    doc.put("articleContent", a.getArticleContent()); doc.put("articleType", a.getArticleType());
    doc.put("isTop", a.getIsTop()); doc.put("status", a.getStatus()); doc.put("visitCount", a.getVisitCount());
    doc.put("tags", tags);
    doc.put("createTime", TimeUtils.format(a.getCreateTime(), DateUtils.YYYY_MM_DD_HH_MM_SS));
    doc.put("updateTime", TimeUtils.format(a.getUpdateTime(), DateUtils.YYYY_MM_DD_HH_MM_SS));
    esClient.index(i -> i.index(indexName()).id(String.valueOf(id)).document(doc));
}
```

### 7.4 各 Service 发送点（在 MySQL 写成功后）

| Service 方法 | 文件:行 | 发送消息 |
|---|---|---|
| `ArticleServiceImpl.publish` | `ArticleServiceImpl.java:293` | `SAVE` |
| `ArticleServiceImpl.updateStatus` | `:379` | `UPDATE` |
| `ArticleServiceImpl.updateIsTop` | `:387` | `UPDATE` |
| `ArticleServiceImpl.deleteArticle` | `:408`（对每个 id） | `DELETE` |
| `LeaveWordServiceImpl.userLeaveWord` | `:83` | `SAVE` |
| `LeaveWordServiceImpl.isCheckLeaveWord` | `:131` | `UPDATE` |
| `LeaveWordServiceImpl.deleteLeaveWord` | `:139`（对每个 id） | `DELETE` |
| `TagServiceImpl.addTag` / `addOrUpdateTag` | `:43` / `:71` | `SAVE` / `UPDATE` |
| `TagServiceImpl.deleteTagByIds` | `:78`（对每个 id） | `DELETE` |
| `CategoryServiceImpl.addCategory` / `addOrUpdateCategory` | `:47` / `:76` | `SAVE` / `UPDATE` |
| `CategoryServiceImpl.deleteCategoryByIds` | `:83`（对每个 id） | `DELETE` |

发送模板（注入 `RabbitTemplate` + 交换机/路由键）：

```java
rabbitTemplate.convertAndSend(ES_EXCHANGE, ES_SYNC_ROUTING_KEY,
    EsSyncMessage.builder().entityType(EntityType.ARTICLE).id(id).syncType(SyncType.SAVE).build());
```

### 7.5 全量重建

新增 `EsFullSyncService.fullSync(EntityType type)`：分批（每批 500）从 MySQL 查 `isDeleted=0` 数据 → 各 DocumentBuilder 批量 `esClient.bulk(...)` 写入 → 返回数量。提供管理接口 `POST /back/search/es/sync?type=ARTICLE`（`@PreAuthorize` + `@AccessLimit` + `@LogAnnotation`），作为冷启动与纠偏兜底。

---

## 8. 搜索服务（多实体综合搜索）

### 8.1 综合搜索入口

新增 `GET /search/all?keyword=xxx`：用 `ElasticsearchClient` 的 **multi-index** 查询（`.index("blog_article","blog_leave_word","blog_tag","blog_category")`，按已启用子开关动态拼接），`bool.multi_match` 跨各实体的文本字段，结果按 `_index` 分组归并为统一 `SearchResultVO { entityType, id, title, snippet, score }`。高亮用 `<em class="highlight">`。

### 8.2 各实体单搜索（沿用 v1 接口契约）

- `GET /search/by/content` → `ArticleSearch.searchByContent`（ES 内 multi_match articleTitle/articleContent/categoryName.text/tags.text，filter status=1，高亮 + 摘要 stripMarkdown）
- `GET /search/init/title` → `ArticleSearch.initSearchByTitle`（sourceFilter 仅取 id/title/categoryName/visitCount）
- `POST /back/search` → `ArticleSearch.searchArticles`（动态 bool 条件）
- 留言/标签/分类搜索按需新增，契约不变

---

## 9. 现有文件变更

| 文件 | 变更 |
|------|------|
| `pom.xml` | 新增官方 client 两个依赖（§5.1） |
| `application.yml` | 新增 `elasticsearch:` / `search:` / MQ 的 es-* 配置（§5.2） |
| `controller/ArticleController.java` | 搜索接口改为调用 `SearchRouter.article()`（替代原 `articleService` 直调与 v1 的三元）；新增全量同步接口 |
| `service/impl/ArticleServiceImpl.java` | 写方法（§7.4）成功后发 `EsSyncMessage`；原 3 个搜索方法保留（供 `ArticleDbSearch` 委托），不再标 `@Deprecated` |
| `service/impl/{LeaveWord,Tag,Category}ServiceImpl.java` | 写方法成功后发对应 `EsSyncMessage` |
| `constants/RabbitConst.java` | 新增 `ES_SYNC_QUEUE` / `ES_EXCHANGE` / `ES_SYNC_ROUTING_KEY` |
| `config/rabbit/EsSyncRabbitConfig.java` | 新增：参照 `EmailRabbitConfig` 声明 exchange/queue/binding |

---

## 10. 服务器 ES 连接说明（替代 v1 的 docker-compose）

服务器已有 8.18.0 + HTTPS + 账号密码，**无需本地 docker-compose**。

**获取自签 CA 证书**（用于 `classpath:es/http_ca.crt`）：

```bash
# 在服务器 ES 部署目录执行（docker 部署则进容器）
# ES 8.x 默认生成的 CA 证书文件名为 http_ca.crt
docker cp blog-elasticsearch:/usr/share/elasticsearch/config/certs/http_ca.crt ./es/http_ca.crt
# 或从服务器 scp 到本地 src/main/resources/es/http_ca.crt
```

**安装 IK 插件 8.18.0**（服务器侧）：

```bash
docker exec -it blog-elasticsearch \
  ./bin/elasticsearch-plugin install \
  https://github.com/infinilabs/analysis-ik/releases/download/v8.18.0/elasticsearch-analysis-ik-8.18.0.zip
docker restart blog-elasticsearch
```

**首次建索引**：全量重建接口会读取 `resources/es/*.json` 自动 `CreateIndex`；也可用 Kibana Dev Tools 手工 `PUT blog_article {...}`。

---

## 11. 实施步骤（执行顺序）

### 阶段 1：基础设施
1. `pom.xml` 加官方 client 依赖
2. `application.yml` 加 `elasticsearch` / `search` / MQ es-* 配置
3. 获取 `http_ca.crt` 放 `resources/es/`，编写 4 份 mapping JSON
4. 创建 `ElasticsearchConfig`（含 SSL/认证）
5. 创建 `constants` 中的 ES 索引名、`RabbitConst` 新增项、`EsSyncRabbitConfig`

### 阶段 2：数据同步
6. 创建 `EsSyncMessage`、`EntityType` 枚举
7. 创建 4 个 `DocumentBuilder` + `DocumentBuilder` 接口
8. 创建统一 `EsSyncListener`
9. 修改 4 个 ServiceImpl，在写方法成功后发 MQ 消息
10. 创建 `EsFullSyncService` + 全量重建管理接口

### 阶段 3：搜索与开关
11. 定义 `ArticleSearch` 等策略接口 + ES 实现 + DB 实现
12. 创建 `SearchRouter`（总/子开关 + ObjectProvider 可选注入）
13. 实现 multi-index 综合搜索 + 各实体单搜索 + 高亮
14. 修改 `ArticleController` 改调 `SearchRouter`

### 阶段 4：验证
15. 编译 `mvn clean package -DskipTests`
16. 启动应用 → 全量重建 → Kibana 验证 mapping 与文档数
17. 逐项过验证清单（§12）

---

## 12. 验证清单

### 功能
- [ ] 4 个索引创建成功，mapping 含 IK 分词器
- [ ] 全量重建后，ES 各索引文档数与 MySQL `isDeleted=0` 记录数一致
- [ ] 中文分词搜索正常（如「Spring Boot」匹配相关文章）
- [ ] 搜索结果高亮正确（`<em class="highlight">`）
- [ ] 前台综合搜索跨多实体返回结果，按 entityType 归并
- [ ] 前台搜索不返回私密/草稿文章（filter status=1）
- [ ] 后台文章搜索支持标题/分类/状态/置顶组合
- [ ] 文章/留言/标签/分类的增删改后，ES 索引增量同步正确
- [ ] 软删除的记录从 ES 移除

### 开关与降级（三级）
- [ ] `search.enabled=false`：应用正常启动（ES 客户端不装配），所有搜索走 MySQL
- [ ] `search.entities.article=false`：仅文章走 MySQL，其余仍走 ES
- [ ] `search.fallback-on-error=true`：模拟 ES 异常，搜索自动回退 MySQL，用户无感
- [ ] `search.fallback-on-error=false`：ES 异常时接口报错（便于排错）

### 非功能
- [ ] RabbitMQ 重试正常（ES 不可用时消息重试 3 次后入死信）
- [ ] `@AccessLimit` / `@PreAuthorize` / `@LogAnnotation` 不受影响
- [ ] `ResponseResult` 返回格式不变
- [ ] 编译无错误：`mvn clean package -DskipTests`
- [ ] 单测：对 `ElasticsearchClient` 调用层用接口隔离 + mock，不连真实 ES

---

## 13. 降级策略（三级 + 兜底）

1. **总开关降级**：`search.enabled=false` → 全部走 MySQL，ES 客户端不装配（适合 ES 长期不可用）
2. **子开关降级**：`search.entities.<entity>=false` → 单实体走 MySQL，其余仍 ES（灰度/局部回退）
3. **运行时降级**：`search.fallback-on-error=true` → ES 查询异常自动 try-catch 回退 MySQL（瞬时抖动无感）
4. **同步兜底**：MQ 同步失败 → 重试 3 次入死信；数据偏差用全量重建接口（§7.5）纠正

---

## 14. 注意事项

1. **IK 插件版本必须 8.18.0**，与 ES 严格一致，否则 ES 启动拒绝加载。
2. **mapping 变更需 reindex**：ES 不支持直接修改已存在字段 mapping。
3. **密码禁止明文入库**：`ES_PASSWORD` 走环境变量；`application.yml` 仅留占位。
4. **自签证书**：生产用 truststore 信任 CA（本方案）；仅内网调试时可临时全信任 `SSLContext`（须注释说明，**禁止带入生产**）。
5. **Markdown 内容**：ES 存原始 Markdown 供检索，高亮摘要返回前需 `stripMarkdown()`。
6. **隐私保护**：`status=2/3`（私密/草稿）文章仍索引（后台搜索需要），前台搜索通过 `filter status=1` 排除；留言同理按 `isCheck` 过滤。
7. **批量性能**：全量重建分批 500 条，避免 OOM。
8. **一致性窗口**：RabbitMQ 异步同步有亚秒级延迟，博客场景可接受。
9. **版本对齐**：客户端 `elasticsearch-java` / `elasticsearch-rest-client` / IK 插件三者均为 8.18.0，与服务器一致。
10. **总开关与 ES 客户端装配绑定**：`search.enabled=false` 时 ES 客户端 bean 不创建，ES 实现用 `ObjectProvider` 可选注入，确保应用在无 ES 环境可启动。