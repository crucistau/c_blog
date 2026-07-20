# Elasticsearch 多实体搜索接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 C-Blog 后端接入 Elasticsearch 8.18.0，覆盖文章/留言/标签/分类四类实体的全文搜索与综合搜索，通过 RabbitMQ 增量同步，并以「总开关 + 子开关 + 策略模式」实现 ES/MySQL 可切换。

**Architecture:** 官方 `elasticsearch-java` 8.18.0 客户端（HTTPS + 账密 + 自签 CA truststore），独立多索引；写 MySQL 成功后经统一 `EsSyncMessage` 异步入 ES；读路径由 `SearchRouter` 按开关在 ES 策略与 DB 策略间路由，ES 异常自动回退 DB。

**Tech Stack:** Spring Boot 3.1.4 / Java 17 / MyBatis-Plus 3.5.3.1 / Elasticsearch 8.18.0（`elasticsearch-java` + `elasticsearch-rest-client`）/ RabbitMQ / IK Analysis 8.18.0。

## Global Constraints

> 每个 task 隐含遵守以下约束（逐字来自 v2 spec `docs/elasticsearch-migration-spec.md`）：

- **版本严格对齐 8.18.0**：`elasticsearch-java`、`elasticsearch-rest-client`、IK 插件均为 8.18.0。
- **不引入** `spring-boot-starter-data-elasticsearch`（避免 BOM 强加 8.11 旧版本）。
- **连接**：HTTPS + 账号密码 + 信任自签 CA（truststore 模式），密码走环境变量 `ES_PASSWORD`，禁止明文入库。
- **开关命名空间**：`search.enabled`（总）/ `search.entities.<entity>`（子）/ `search.fallback-on-error`（运行时回退）；ES 配置用独立 `elasticsearch:` 命名空间，不用 `spring.elasticsearch.*`。
- **总开关与 ES 客户端装配绑定**：`search.enabled=false` 时 ES 客户端 bean 不创建；ES 策略实现用 `ObjectProvider` 可选注入，确保无 ES 环境可启动。
- **现有接口契约不变**：搜索接口路径、入参、返回 VO 不变。
- **测试策略**：项目无测试基础设施（仅 `BlogBackendApplicationTests.contextLoads`），遵循 `docs/dashboard-api-change-report.md` 既定约定「不新增测试文件，用编译验证」。每个 task 以 `mvn clean compile`（或 `mvn clean package -DskipTests`）+ 集成验证为验证手段；Task 12 对 `SearchRouter` 纯路由逻辑标注可选 JUnit 单测。**依据**：项目既有约定（AGENTS.md/docs）+ ES/MQ/SSL 集成代码单测价值低 + minimum-change 原则。
- **详细代码清单**：本 plan 给出每个 task 的关键代码；完整 mapping JSON 与长代码片段见 v2 spec 对应章节（spec 为稳定外部参考，非 task 内引用）。

---

## File Structure

### 新建文件

| 文件 | 职责 |
|------|------|
| `config/ElasticsearchConfig.java` | ES 客户端装配（RestClient→ElasticsearchClient），SSL + 账密，`@ConditionalOnProperty(search.enabled)` |
| `config/rabbit/EsSyncRabbitConfig.java` | ES 同步 exchange/queue/binding，参照 `EmailRabbitConfig` |
| `constants/EsIndexConst.java` | 4 个索引名常量 |
| `constants/EntityType.java` | 枚举 ARTICLE/LEAVE_WORD/TAG/CATEGORY（也可作为 `EsSyncMessage` 内嵌枚举） |
| `domain/dto/EsSyncMessage.java` | 统一同步消息体（entityType+id+syncType） |
| `search/builder/DocumentBuilder.java` | 文档构建接口（type/indexName/upsert/delete） |
| `search/builder/{Article,LeaveWord,Tag,Category}DocumentBuilder.java` | 各实体 MySQL→ES 文档构建 |
| `listener/EsSyncListener.java` | 统一消费 `es_sync_queue`，按 entityType 分发 |
| `search/EsFullSyncService.java`(+impl) | 全量重建（分批 bulk） |
| `search/{Article,LeaveWord,Tag,Category}Search.java` | 各实体搜索策略接口 |
| `search/impl/{Article,LeaveWord,Tag,Category}DbSearch.java` | DB 策略实现（委托现有 Service 的 LIKE 方法） |
| `search/impl/{Article,LeaveWord,Tag,Category}EsSearch.java` | ES 策略实现（`ElasticsearchClient` 查询 + 高亮 + 回退） |
| `search/SearchRouter.java` | 总/子开关路由 |
| `resources/es/{blog_article,blog_leave_word,blog_tag,blog_category}_mapping.json` | 4 份 mapping（v2 §4.2-4.5） |
| `resources/es/http_ca.crt` | 服务器 ES 自签 CA 证书（运维提供，见 v2 §10） |

### 修改文件

| 文件 | 变更 |
|------|------|
| `pom.xml` | 加 2 个 ES 依赖（v2 §5.1） |
| `application.yml` | 加 `elasticsearch:` / `search:` / MQ `es-*`（v2 §5.2） |
| `constants/RabbitConst.java` | 加 `ES_SYNC_QUEUE` / `ES_EXCHANGE` / `ES_SYNC_ROUTING_KEY` |
| `service/impl/{Article,LeaveWord,Tag,Category}ServiceImpl.java` | 写方法成功后发 `EsSyncMessage`（v2 §7.4） |
| `controller/ArticleController.java` | 3 个搜索接口改调 `SearchRouter.article()`；新增综合搜索 + 全量重建接口 |

---

## Task 1: pom 依赖与 application.yml 配置

**Files:**
- Modify: `pom.xml`（`<dependencies>` 末尾）
- Modify: `application.yml`（顶层 + `spring.rabbitmq`）

**Interfaces:** Produces 配置项 `elasticsearch.*` / `search.*` / `spring.rabbitmq.{queue.exchange.routingKey}.es*`，供后续 task 注入。

- [ ] **Step 1: pom.xml 加依赖**（v2 §5.1）

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
```

- [ ] **Step 2: application.yml 加配置**（v2 §5.2，顶层新增 `elasticsearch:` 与 `search:`；`spring.rabbitmq` 下新增 es-* 三项）

```yaml
elasticsearch:
  uris: https://141.98.198.67:9200
  username: elastic
  password: ${ES_PASSWORD:changeme}
  ca-cert-path: classpath:es/http_ca.crt
  connect-timeout: 5000
  socket-timeout: 30000

search:
  enabled: true
  fallback-on-error: true
  entities:
    article: true
    leave-word: true
    tag: false
    category: false
```

在现有 `spring.rabbitmq.queue / exchange / routingKey` 下分别加：`es-sync: es_sync_queue` / `es: es_exchange` / `es-sync: es_sync_routing_key`。

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile`
Expected: BUILD SUCCESS（依赖可解析）。

- [ ] **Step 4: Commit**

```bash
git add pom.xml src/main/resources/application.yml
git commit -m "feat(es): add elasticsearch 8.18.0 deps and search config"
```

---

## Task 2: ES 客户端配置类 + mapping JSON + CA 证书占位

**Files:**
- Create: `src/main/java/xyz/kuailemao/config/ElasticsearchConfig.java`
- Create: `src/main/resources/es/blog_article_mapping.json`（v2 §4.2）
- Create: `src/main/resources/es/blog_leave_word_mapping.json`（v2 §4.3）
- Create: `src/main/resources/es/blog_tag_mapping.json`（v2 §4.4）
- Create: `src/main/resources/es/blog_category_mapping.json`（v2 §4.5）
- Create: `src/main/resources/es/http_ca.crt`（运维提供；开发期可临时放服务器证书，生产禁止用全信任 SSLContext）

**Interfaces:**
- Produces: `ElasticsearchClient` bean（仅 `search.enabled=true` 时存在）；`RestClient` bean。

- [ ] **Step 1: 创建 4 份 mapping JSON**

逐字复制 v2 §4.2-4.5 的 JSON 到对应文件。

- [ ] **Step 2: 创建 ElasticsearchConfig.java**（v2 §5.3 完整代码）

```java
package xyz.kuailemao.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import jakarta.annotation.Resource;
import org.apache.hc.client5.http.auth.*;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import lombok.Data;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

@Configuration
@ConditionalOnProperty(name = "search.enabled", havingValue = "true", matchIfMissing = true)
@ConfigurationProperties(prefix = "elasticsearch")
@Data
public class ElasticsearchConfig {
    private String uris;
    private String username;
    private String password;
    private String caCertPath;
    private int connectTimeout = 5000;
    private int socketTimeout = 30000;

    @Bean
    public RestClient restClient() throws Exception {
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

        BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(username, password.toCharArray()));

        return RestClient.builder(org.apache.hc.core5.http.HttpHost.create(uris))
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
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
```

> 说明：`HttpHost.create` 用 `org.apache.hc.core5.http.HttpHost`（httpclient5）。若 `setRequestConfigCallback` 的类型在 rest-client 8.18 下与 httpclient5 的 `RequestConfig.Builder` 方法名不完全一致，执行时以编译器提示为准微调（`setConnectTimeout`/`setSocketTimeout` 在 httpclient5 `RequestConfig.Builder` 上存在）。

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add src/main/java/xyz/kuailemao/config/ElasticsearchConfig.java src/main/resources/es/
git commit -m "feat(es): add elasticsearch client config with ssl/basic-auth"
```

---

## Task 3: 常量、枚举与 RabbitMQ 配置

**Files:**
- Modify: `src/main/java/xyz/kuailemao/constants/RabbitConst.java`
- Create: `src/main/java/xyz/kuailemao/constants/EsIndexConst.java`
- Create: `src/main/java/xyz/kuailemao/config/rabbit/EsSyncRabbitConfig.java`

**Interfaces:**
- Produces: `RabbitConst.ES_SYNC_QUEUE/ES_EXCHANGE/ES_SYNC_ROUTING_KEY`；`EsIndexConst.ARTICLE/LEAVE_WORD/TAG/CATEGORY_INDEX`；RabbitMQ 的 es exchange/queue/binding bean。

- [ ] **Step 1: RabbitConst 加 3 行**

```java
/** ES 同步队列 */
public static final String ES_SYNC_QUEUE = "es_sync_queue";
public static final String ES_EXCHANGE = "es_exchange";
public static final String ES_SYNC_ROUTING_KEY = "es_sync_routing_key";
```

- [ ] **Step 2: EsIndexConst.java**

```java
package xyz.kuailemao.constants;

public class EsIndexConst {
    public static final String ARTICLE_INDEX = "blog_article";
    public static final String LEAVE_WORD_INDEX = "blog_leave_word";
    public static final String TAG_INDEX = "blog_tag";
    public static final String CATEGORY_INDEX = "blog_category";
    public static final String HIGHLIGHT_PRE_TAG = "<em class=\"highlight\">";
    public static final String HIGHLIGHT_POST_TAG = "</em>";
    public static final int CONTENT_SNIPPET_LENGTH = 200;
}
```

- [ ] **Step 3: EsSyncRabbitConfig.java**（参照 EmailRabbitConfig 模式）

```java
package xyz.kuailemao.config.rabbit;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EsSyncRabbitConfig {
    @Value("${spring.rabbitmq.queue.es-sync}") public String ES_SYNC_QUEUE;
    @Value("${spring.rabbitmq.exchange.es}") public String ES_EXCHANGE;
    @Value("${spring.rabbitmq.routingKey.es-sync}") public String ES_SYNC_ROUTING_KEY;

    @Bean public DirectExchange esExchange() { return ExchangeBuilder.directExchange(ES_EXCHANGE).durable(true).build(); }
    @Bean public Queue esSyncQueue() { return QueueBuilder.durable(ES_SYNC_QUEUE).build(); }
    @Bean public Binding esSyncBinding(DirectExchange esExchange, Queue esSyncQueue) {
        return BindingBuilder.bind(esSyncQueue).to(esExchange).with(ES_SYNC_ROUTING_KEY);
    }
}
```

- [ ] **Step 4: 编译验证** — `mvn clean compile` → BUILD SUCCESS
- [ ] **Step 5: Commit** — `git add -A` → `git commit -m "feat(es): add es constants and rabbit config"`

---

## Task 4: EsSyncMessage 与 DocumentBuilder 接口

**Files:**
- Create: `src/main/java/xyz/kuailemao/domain/dto/EsSyncMessage.java`
- Create: `src/main/java/xyz/kuailemao/search/builder/DocumentBuilder.java`

**Interfaces:**
- Produces: `EsSyncMessage{EntityType,Long,SyncType}`；`DocumentBuilder{type(),indexName(),upsert(Long),delete(Long)}`。

- [ ] **Step 1: EsSyncMessage.java**（v2 §7.1）

```java
package xyz.kuailemao.domain.dto;

import lombok.*;
import java.io.Serializable;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EsSyncMessage implements Serializable {
    private EntityType entityType;
    private Long id;
    private SyncType syncType;

    public enum EntityType { ARTICLE, LEAVE_WORD, TAG, CATEGORY }
    public enum SyncType { SAVE, UPDATE, DELETE }
}
```

- [ ] **Step 2: DocumentBuilder.java**（v2 §7.3）

```java
package xyz.kuailemao.search.builder;

import xyz.kuailemao.domain.dto.EsSyncMessage.EntityType;

public interface DocumentBuilder {
    EntityType type();
    String indexName();
    void upsert(Long id);   // MySQL 查关联数据 → esClient.index(...)
    void delete(Long id);   // esClient.delete(...)
}
```

- [ ] **Step 3: 编译验证** — `mvn clean compile` → BUILD SUCCESS
- [ ] **Step 4: Commit** — `git add -A` → `git commit -m "feat(es): add sync message dto and document builder interface"`

---

## Task 5: ArticleDocumentBuilder（含关联查询）

**Files:**
- Create: `src/main/java/xyz/kuailemao/search/builder/ArticleDocumentBuilder.java`

**Interfaces:**
- Consumes: `ElasticsearchClient`、`ArticleMapper`/`UserMapper`/`CategoryMapper`/`ArticleTagMapper`/`TagMapper`
- Produces: 一个 `DocumentBuilder` bean（`type()=ARTICLE`）

- [ ] **Step 1: 实现 ArticleDocumentBuilder**（v2 §7.3 完整逻辑）

```java
package xyz.kuailemao.search.builder;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.kuailemao.constants.EsIndexConst;
import xyz.kuailemao.domain.dto.EsSyncMessage.EntityType;
import xyz.kuailemao.domain.entity.*;
import xyz.kuailemao.mapper.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ArticleDocumentBuilder implements DocumentBuilder {
    @Resource private ElasticsearchClient esClient;
    @Resource private ArticleMapper articleMapper;
    @Resource private UserMapper userMapper;
    @Resource private CategoryMapper categoryMapper;
    @Resource private ArticleTagMapper articleTagMapper;
    @Resource private TagMapper tagMapper;

    @Override public EntityType type() { return EntityType.ARTICLE; }
    @Override public String indexName() { return EsIndexConst.ARTICLE_INDEX; }

    @Override
    public void upsert(Long id) {
        try {
            Article a = articleMapper.selectById(id);
            if (a == null || a.getIsDeleted() == 1) { delete(id); return; }
            String username = Optional.ofNullable(userMapper.selectById(a.getUserId())).map(User::getUsername).orElse("");
            String categoryName = Optional.ofNullable(categoryMapper.selectById(a.getCategoryId())).map(Category::getCategoryName).orElse("");
            List<Long> tagIds = articleTagMapper.selectList(
                    new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, id))
                    .stream().map(ArticleTag::getTagId).toList();
            List<String> tags = tagIds.isEmpty() ? List.of()
                    : tagMapper.selectBatchIds(tagIds).stream().map(Tag::getTagName).toList();

            Map<String, Object> doc = new HashMap<>();
            doc.put("id", a.getId()); doc.put("userId", a.getUserId()); doc.put("username", username);
            doc.put("categoryId", a.getCategoryId()); doc.put("categoryName", categoryName);
            doc.put("articleCover", a.getArticleCover()); doc.put("articleTitle", a.getArticleTitle());
            doc.put("articleContent", a.getArticleContent()); doc.put("articleType", a.getArticleType());
            doc.put("isTop", a.getIsTop()); doc.put("status", a.getStatus()); doc.put("visitCount", a.getVisitCount());
            doc.put("tags", tags);
            doc.put("createTime", cn.hutool.core.date.DateUtil.format(a.getCreateTime(), "yyyy-MM-dd HH:mm:ss"));
            doc.put("updateTime", cn.hutool.core.date.DateUtil.format(a.getUpdateTime(), "yyyy-MM-dd HH:mm:ss"));

            esClient.index(i -> i.index(indexName()).id(String.valueOf(id)).document(doc));
        } catch (Exception e) {
            log.error("文章同步ES失败 id={}", id, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(Long id) {
        try {
            esClient.delete(d -> d.index(indexName()).id(String.valueOf(id)));
        } catch (Exception e) {
            log.error("文章从ES删除失败 id={}", id, e);
            throw new RuntimeException(e);
        }
    }
}
```

> 说明：日期格式化用项目已有的 hutool `DateUtil`（v2 用 `TimeUtils`，若该工具类存在则替换；执行时 grep 确认 `xyz.kuailemao.utils.TimeUtils` 是否存在，存在则用之，否则用 hutool）。

- [ ] **Step 2: 编译验证** — `mvn clean compile` → BUILD SUCCESS
- [ ] **Step 3: Commit** — `git commit -m "feat(es): add article document builder"`

---

## Task 6: 其余 3 个 DocumentBuilder

**Files:**
- Create: `LeaveWordDocumentBuilder.java` / `TagDocumentBuilder.java` / `CategoryDocumentBuilder.java`（均在 `search/builder/`）

**Interfaces:** 各产出 `DocumentBuilder` bean（type 分别为 LEAVE_WORD/TAG/CATEGORY）。

- [ ] **Step 1: LeaveWordDocumentBuilder**

字段：`id, userId, username, content, isCheck, createTime, updateTime`（关联 `UserMapper` 查 username）。`upsert` 软删（isDeleted=1）时 delete。索引名 `EsIndexConst.LEAVE_WORD_INDEX`。

- [ ] **Step 2: TagDocumentBuilder**

字段：`id, tagName, createTime, updateTime`。索引名 `EsIndexConst.TAG_INDEX`。无关联查询。

- [ ] **Step 3: CategoryDocumentBuilder**

字段：`id, categoryName, createTime, updateTime`。索引名 `EsIndexConst.CATEGORY_INDEX`。无关联查询。

> 结构与 ArticleDocumentBuilder 一致（实现 `DocumentBuilder`，`upsert` 查 MySQL→`Map`→`esClient.index`，`delete`→`esClient.delete`，异常抛 RuntimeException 触发 MQ 重试）。

- [ ] **Step 4: 编译验证** — `mvn clean compile` → BUILD SUCCESS
- [ ] **Step 5: Commit** — `git commit -m "feat(es): add leave-word/tag/category document builders"`

---

## Task 7: EsSyncListener（统一消费分发）

**Files:**
- Create: `src/main/java/xyz/kuailemao/listener/EsSyncListener.java`

**Interfaces:**
- Consumes: 所有 `DocumentBuilder` bean、`ElasticsearchClient`
- 消费 `RabbitConst.ES_SYNC_QUEUE`，按 `entityType` 分发。

- [ ] **Step 1: 实现 EsSyncListener**（v2 §7.2）

```java
package xyz.kuailemao.listener;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import xyz.kuailemao.constants.RabbitConst;
import xyz.kuailemao.domain.dto.EsSyncMessage;
import xyz.kuailemao.handler.RabbitListenerErrorHandler;
import xyz.kuailemao.search.builder.DocumentBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EsSyncListener {
    @Resource private ElasticsearchClient esClient;
    private final Map<EsSyncMessage.EntityType, DocumentBuilder> builders;

    @Autowired
    public EsSyncListener(List<DocumentBuilder> list) {
        this.builders = list.stream()
                .collect(Collectors.toMap(DocumentBuilder::type, b -> b));
    }

    @RabbitListener(queues = RabbitConst.ES_SYNC_QUEUE, errorHandler = "rabbitListenerErrorHandler")
    public void handle(EsSyncMessage msg) {
        log.info("ES 同步消息: entity={}, id={}, type={}", msg.getEntityType(), msg.getId(), msg.getSyncType());
        DocumentBuilder builder = builders.get(msg.getEntityType());
        if (builder == null) { log.warn("无对应 DocumentBuilder: {}", msg.getEntityType()); return; }
        switch (msg.getSyncType()) {
            case SAVE, UPDATE -> builder.upsert(msg.getId());
            case DELETE       -> builder.delete(msg.getId());
        }
    }
}
```

> `errorHandler = "rabbitListenerErrorHandler"` 复用现有 `xyz.kuailemao.handler.RabbitListenerErrorHandler`；消息处理抛异常时由 `application.yml` 重试策略（max-attempts: 3）重试，最终入死信。

- [ ] **Step 2: 编译验证** — `mvn clean compile` → BUILD SUCCESS
- [ ] **Step 3: Commit** — `git commit -m "feat(es): add unified es sync listener"`

---

## Task 8: 各 Service 写操作发送 MQ 消息

**Files:**
- Modify: `service/impl/ArticleServiceImpl.java`（publish:293 / updateStatus:379 / updateIsTop:387 / deleteArticle:408）
- Modify: `service/impl/LeaveWordServiceImpl.java`（userLeaveWord:83 / isCheckLeaveWord:131 / deleteLeaveWord:139）
- Modify: `service/impl/TagServiceImpl.java`（addTag:43 / addOrUpdateTag:71 / deleteTagByIds:78）
- Modify: `service/impl/CategoryServiceImpl.java`（addCategory:47 / addOrUpdateCategory:76 / deleteCategoryByIds:83）

**Interfaces:** Consumes `RabbitTemplate` + es exchange/routingKey。

- [ ] **Step 1: 每个 ServiceImpl 注入 RabbitTemplate + es exchange/routingKey**

```java
@Resource private RabbitTemplate rabbitTemplate;
@Value("${spring.rabbitmq.exchange.es}") private String ES_EXCHANGE;
@Value("${spring.rabbitmq.routingKey.es-sync}") private String ES_SYNC_ROUTING_KEY;
```

- [ ] **Step 2: 在 MySQL 写成功后发送**（v2 §7.4 行号表）

发送模板：
```java
rabbitTemplate.convertAndSend(ES_EXCHANGE, ES_SYNC_ROUTING_KEY,
    EsSyncMessage.builder().entityType(EntityType.ARTICLE).id(id).syncType(SyncType.SAVE).build());
```

逐方法对应（entityType / syncType）：
- `ArticleServiceImpl`：publish→ARTICLE/SAVE；updateStatus→ARTICLE/UPDATE；updateIsTop→ARTICLE/UPDATE；deleteArticle→对每个 id 发 ARTICLE/DELETE。
- `LeaveWordServiceImpl`：userLeaveWord→LEAVE_WORD/SAVE；isCheckLeaveWord→LEAVE_WORD/UPDATE；deleteLeaveWord→对每个 id 发 LEAVE_WORD/DELETE。
- `TagServiceImpl`：addTag→TAG/SAVE；addOrUpdateTag→TAG/UPDATE；deleteTagByIds→对每个 id 发 TAG/DELETE。
- `CategoryServiceImpl`：addCategory→CATEGORY/SAVE；addOrUpdateCategory→CATEGORY/UPDATE；deleteCategoryByIds→对每个 id 发 CATEGORY/DELETE。

> 注意：`id` 的获取——`publish` 在 `saveOrUpdate(article)` 后用 `article.getId()`；批量删除遍历入参 `ids`。

- [ ] **Step 3: 编译验证** — `mvn clean compile` → BUILD SUCCESS
- [ ] **Step 4: Commit** — `git commit -m "feat(es): publish sync messages on entity writes"`

---

## Task 9: 全量重建服务 + 管理接口

**Files:**
- Create: `search/EsFullSyncService.java`（接口）+ `search/impl/EsFullSyncServiceImpl.java`
- Modify: `controller/ArticleController.java`（新增全量重建接口）

**Interfaces:** Produces `long fullSync(EntityType type)`。

- [ ] **Step 1: EsFullSyncService 接口**

```java
public interface EsFullSyncService {
    long fullSync(EsSyncMessage.EntityType type);
}
```

- [ ] **Step 2: EsFullSyncServiceImpl**

逻辑（v2 §7.5）：
1. 若索引不存在，读 `resources/es/<index>_mapping.json` → `esClient.indices().create(c -> c.index(name).settings(...).mappings(...))`（或用 JSON 字符串 withJson）。
2. 查 MySQL `isDeleted=0` 全量数据，按 500 分批 → 各 `DocumentBuilder.upsert(id)`（或直接 `esClient.bulk`）。
3. 返回处理数量。
> 各实体的"查全量 id 列表"复用对应 Mapper（`articleMapper.selectList(isDeleted=0)` 等）。

- [ ] **Step 3: ArticleController 加全量重建接口**

```java
@PreAuthorize("hasAnyAuthority('blog:article:search')")
@Operation(summary = "全量同步到ES")
@LogAnnotation(module = "文章管理", operation = "全量同步ES")
@AccessLimit(seconds = 60, maxCount = 1)
@PostMapping("/back/es/sync")
public ResponseResult<Long> fullSyncToEs(@RequestParam("type") EsSyncMessage.EntityType type) {
    return ControllerUtils.messageHandler(() -> esFullSyncService.fullSync(type));
}
```
注入 `EsFullSyncService esFullSyncService`。

- [ ] **Step 4: 编译验证** — `mvn clean compile` → BUILD SUCCESS
- [ ] **Step 5: Commit** — `git commit -m "feat(es): add full-sync service and admin endpoint"`

---

## Task 10: 搜索策略接口 + DB 实现

**Files:**
- Create: `search/ArticleSearch.java`（+ LeaveWordSearch/TagSearch/CategorySearch 若需单实体搜索）
- Create: `search/impl/ArticleDbSearch.java` 等

**Interfaces:**
- Produces: `ArticleSearch{searchByContent, initSearchByTitle, searchArticles}`；DB 实现委托现有 `ArticleService`。

- [ ] **Step 1: ArticleSearch 接口**（v2 §6.2）

```java
public interface ArticleSearch {
    List<SearchArticleByContentVO> searchByContent(String keyword);
    List<InitSearchTitleVO> initSearchByTitle();
    List<ArticleListVO> searchArticles(SearchArticleDTO dto);
}
```

- [ ] **Step 2: ArticleDbSearch**（委托现有 `ArticleServiceImpl` 的 LIKE 方法，零改动）

```java
@Component
public class ArticleDbSearch implements ArticleSearch {
    @Resource private ArticleService articleService;
    @Override public List<SearchArticleByContentVO> searchByContent(String k) { return articleService.searchArticleByContent(k); }
    @Override public List<InitSearchTitleVO> initSearchByTitle() { return articleService.initSearchByTitle(); }
    @Override public List<ArticleListVO> searchArticles(SearchArticleDTO dto) { return articleService.searchArticle(dto); }
}
```

> 留言/标签/分类的 DB 策略同理（如有单实体搜索需求）；若无独立搜索接口，可仅做 Article。

- [ ] **Step 3: 编译验证** — `mvn clean compile` → BUILD SUCCESS
- [ ] **Step 4: Commit** — `git commit -m "feat(es): add search strategy interface and db impl"`

---

## Task 11: ES 搜索实现

**Files:**
- Create: `search/impl/ArticleEsSearch.java`

**Interfaces:**
- Consumes: `ElasticsearchClient`、`ObjectProvider<ArticleSearch>`（用于回退到 DbSearch）
- 产出 ES 策略 bean（仅 `search.enabled=true` 时存在）。

- [ ] **Step 1: ArticleEsSearch**（v2 §6.3 + §8.2）

```java
@Component
@ConditionalOnProperty(name = "search.enabled", havingValue = "true", matchIfMissing = true)
public class ArticleEsSearch implements ArticleSearch {
    @Resource private ElasticsearchClient esClient;
    @Resource private ObjectProvider<ArticleSearch> dbProvider;   // 回退用
    @Value("${search.fallback-on-error:true}") private boolean fallbackOnError;

    private ArticleSearch fallback() { return dbProvider.stream().filter(s -> s instanceof ArticleDbSearch).findFirst().orElseThrow(); }

    @Override
    public List<SearchArticleByContentVO> searchByContent(String keyword) {
        try {
            // esClient.search: bool.must=multi_match(articleTitle,articleContent,categoryName.text,tags.text,kw)
            //                 filter=term(status,1); highlight articleTitle/articleContent; sort _score,createTime; size 20
            // 结果转 VO：取 articleContent 高亮片段，无则截前 200 + stripMarkdown()
            return ... ;
        } catch (Exception e) {
            log.error("ES 内容搜索失败，回退DB", e);
            if (fallbackOnError) return fallback().searchByContent(keyword);
            throw new RuntimeException(e);
        }
    }
    // initSearchByTitle: sourceFilter=[id,articleTitle,categoryName,visitCount], filter status=1, sort createTime
    // searchArticles: 动态 bool（match articleTitle / filter categoryId,status,isTop）, sort createTime
}
```

> `ElasticsearchClient` 的 search/highlight/multi_match 具体写法见 v2 §8.2；`stripMarkdown()` 复用项目现有 Markdown 工具（grep 确认 `xyz.kuailemao.utils` 下的 MarkdownUtils 或类似）。

- [ ] **Step 2: （可选）多实体综合搜索方法**

在 `ArticleEsSearch` 或单独 `ComprehensiveEsSearch` 提供 `searchAll(keyword)`：`esClient.search(s -> s.index("blog_article","blog_leave_word","blog_tag","blog_category")...)`，结果按 `_index` 分组归并为 `SearchResultVO`。仅当已启用子开关的索引纳入。

- [ ] **Step 3: 编译验证** — `mvn clean compile` → BUILD SUCCESS
- [ ] **Step 4: Commit** — `git commit -m "feat(es): add es search impl with highlight and fallback"`

---

## Task 12: SearchRouter + Controller 接入

**Files:**
- Create: `search/SearchRouter.java`
- Modify: `controller/ArticleController.java`（3 个搜索接口改调 router；新增综合搜索接口）

**Interfaces:**
- Produces: `SearchRouter.article()` 等，按总/子开关返回 ES 或 DB 策略。

- [ ] **Step 1: SearchRouter**（v2 §6.2）

```java
package xyz.kuailemao.search;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.kuailemao.search.impl.ArticleDbSearch;
import xyz.kuailemao.search.impl.ArticleEsSearch;

@Component
public class SearchRouter {
    @Value("${search.enabled:true}") private boolean enabled;
    @Value("${search.entities.article:true}") private boolean articleEnabled;

    @Resource(name = "articleEsSearch") private ObjectProvider<ArticleSearch> articleEs;
    @Resource private ArticleDbSearch articleDb;

    public ArticleSearch article() {
        return (enabled && articleEnabled) ? articleEs.getIfAvailable(articleDb) : articleDb;
    }
    // leaveWord()/tag()/category() 同理（仅当对应 Search 接口存在）
}
```

> `ArticleSearch` 为本 plan Task 10 定义的接口；ES bean 用 `ObjectProvider` 可选注入（总开关关闭时 ES bean 不存在）。

- [ ] **Step 2: ArticleController 3 个搜索接口改调 router**

```java
@Resource private SearchRouter searchRouter;

// :50 initSearchByTitle
return ControllerUtils.messageHandler(() -> searchRouter.article().initSearchByTitle());
// :66 searchByContent
return ControllerUtils.messageHandler(() -> searchRouter.article().searchByContent(content));
// :225 searchArticle
return ControllerUtils.messageHandler(() -> searchRouter.article().searchArticles(searchArticleDTO));
```

- [ ] **Step 3: 新增综合搜索接口**（v2 §8.1）

```java
@Operation(summary = "综合搜索")
@AccessLimit(seconds = 60, maxCount = 10)
@GetMapping("/search/all")
public ResponseResult<List<SearchResultVO>> searchAll(@RequestParam("keyword") String keyword) {
    return ControllerUtils.messageHandler(() -> searchRouter.article().searchAll(keyword)); // 或独立 ComprehensiveSearch
}
```

> `SearchResultVO{entityType,id,title,snippet,score}` 需新建于 `domain/vo/`。

- [ ] **Step 4: 编译验证** — `mvn clean compile` → BUILD SUCCESS

- [ ] **Step 5: （可选）SearchRouter 路由逻辑单测**

纯逻辑可单测（mock ObjectProvider）。新建 `src/test/java/xyz/kuailemao/search/SearchRouterTest.java`，验证 `enabled=false`/`articleEnabled=false`/ES bean 缺失 三种情况返回 DbSearch，其余返回 EsSearch。**依据**：这是本 plan 唯一价值明确、不依赖外部中间件的纯逻辑单测点。

- [ ] **Step 6: Commit** — `git commit -m "feat(es): add search router and wire controller"`

---

## Task 13: 集成验证（手工，不连真实 ES 的部分用编译/启动）

**Files:** 无代码改动；运维侧：服务器装 IK 8.18.0、提供 `http_ca.crt`、设 `ES_PASSWORD` 环境变量。

- [ ] **Step 1: 整体编译** — `mvn clean package -DskipTests` → BUILD SUCCESS
- [ ] **Step 2: 总开关关闭启动验证** — 设 `search.enabled=false`，启动应用 → 正常启动（ES 客户端不装配），搜索接口走 MySQL
- [ ] **Step 3: 总开关开启启动验证** — 装好 `http_ca.crt` + `ES_PASSWORD`，启动 → ES 客户端装配成功，日志无 SSL/认证错误
- [ ] **Step 4: 全量重建** — `POST /article/back/es/sync?type=ARTICLE` → 返回同步数量；Kibana Dev Tools `GET blog_article/_count` 与 MySQL `isDeleted=0` 文章数一致
- [ ] **Step 5: 增量同步** — 发布/删除/改状态文章 → ES 索引相应 upsert/delete（Kibana 验证）
- [ ] **Step 6: 搜索功能** — 前台 `/article/search/by/content?content=Spring` 返回高亮结果；`/search/init/title` 正常；后台 `/back/search` 多条件组合正确
- [ ] **Step 7: 三级降级**
  - 子开关 `search.entities.article=false` → 文章走 MySQL，其余 ES
  - `search.fallback-on-error=true` + 模拟 ES 异常（断 ES）→ 搜索自动回退 MySQL
  - `search.fallback-on-error=false` + 断 ES → 搜索接口报错
- [ ] **Step 8: 其余实体**（留言/标签/分类开启子开关后）重复 Step 4-6
- [ ] **Step 9: 最终 commit**（如有验证中的微调） — `git commit -m "test(es): integration verification tweaks"`

---

## Self-Review

**1. Spec 覆盖**（v2 各章节 → task）：
- §5.1 pom → Task 1；§5.2 yml → Task 1；§5.3 client → Task 2 ✅
- §4.2-4.5 mapping → Task 2 ✅
- §6 开关（接口/路由/回退）→ Task 10/11/12 ✅
- §7.1 message → Task 4；§7.2 listener → Task 7；§7.3 builder → Task 5/6；§7.4 发送点 → Task 8；§7.5 全量 → Task 9 ✅
- §8.1 综合搜索 → Task 11 Step2 + Task 12 Step3；§8.2 单搜索 → Task 11 ✅
- §9 现有文件变更（pom/yml/RabbitConst/4 Service/Controller）→ Task 1/3/8/12 ✅
- §10 服务器连接 → Task 13 Step 1（运维） ✅
- §12 验证清单 → Task 13 ✅
- §13 三级降级 → Task 13 Step 7 ✅

**2. 占位符扫描**：Task 11 的 `return ...;` 是 ES 查询转 VO 的占位——已注明"见 v2 §8.2"且给出查询结构（bool/highlight/sort/size），属可执行指引而非空占位。Task 6 三个 builder 用"结构与 Article 一致"描述——已列出各字段与索引名差异，机械可执行。其余无 TBD/TODO。

**3. 类型一致性**：`DocumentBuilder.type()` 返回 `EntityType`，`EsSyncListener` 用 `Map<EntityType, DocumentBuilder>`，一致；`ArticleSearch` 接口在 Task 10 定义，Task 11/12 消费，方法名（searchByContent/initSearchByTitle/searchArticles）跨 task 一致；`SearchRouter.article()` 返回 `ArticleSearch`，与 Controller 调用一致。`EsSyncMessage.EntityType` 全链路一致。

**4. 测试策略偏离已注明**：Global Constraints 与 Task 12 Step 5 均显式说明依据（项目无测试基础设施 + dashboard 既定约定）。
