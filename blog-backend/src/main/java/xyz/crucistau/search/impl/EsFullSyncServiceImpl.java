package xyz.crucistau.search.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import xyz.crucistau.constants.EsIndexConst;
import xyz.crucistau.domain.dto.EsSyncMessage.EntityType;
import xyz.crucistau.mapper.ArticleMapper;
import xyz.crucistau.mapper.CategoryMapper;
import xyz.crucistau.mapper.LeaveWordMapper;
import xyz.crucistau.mapper.TagMapper;
import xyz.crucistau.search.EsFullSyncService;
import xyz.crucistau.search.builder.DocumentBuilder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ES 全量同步服务实现。
 * <p>
 * 从 MySQL 全量数据重建指定实体类型的 ES 索引，
 * 分批调用对应 {@link DocumentBuilder#upsert(Long)} 完成写入。
 *
 * @author kuailemao
 * @date 2026-07-20
 */
@Slf4j
@Service
public class EsFullSyncServiceImpl implements EsFullSyncService {

    @Resource
    private ElasticsearchClient esClient;
    @Resource
    private ArticleMapper articleMapper;
    @Resource
    private LeaveWordMapper leaveWordMapper;
    @Resource
    private TagMapper tagMapper;
    @Resource
    private CategoryMapper categoryMapper;

    private final Map<EntityType, DocumentBuilder> builders;

    @Autowired
    public EsFullSyncServiceImpl(List<DocumentBuilder> list) {
        this.builders = list.stream().collect(Collectors.toMap(DocumentBuilder::type, b -> b));
    }

    @Override
    public long fullSync(EntityType type) {
        DocumentBuilder builder = builders.get(type);
        if (builder == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + type);
        }

        // 1. 确保索引存在
        String indexName = builder.indexName();
        ensureIndexExists(indexName, type);

        // 2. 查询 MySQL 全量数据
        List<Long> ids = fetchAllIds(type);
        if (ids.isEmpty()) {
            log.warn("全量同步 {} 无数据", type);
            return 0;
        }

        // 3. 分批 upsert
        int batchSize = 500;
        for (int i = 0; i < ids.size(); i += batchSize) {
            int end = Math.min(i + batchSize, ids.size());
            List<Long> batch = ids.subList(i, end);
            for (Long id : batch) {
                builder.upsert(id);
            }
            log.info("全量同步 {} 进度: {}/{}", type, end, ids.size());
        }

        return ids.size();
    }

    private void ensureIndexExists(String indexName, EntityType type) {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(indexName)).value();
            if (exists) {
                log.info("索引 {} 已存在，跳过创建", indexName);
                return;
            }
            // 读取 mapping JSON 文件
            String mappingPath = "es/" + indexName + "_mapping.json";
            ClassPathResource resource = new ClassPathResource(mappingPath);
            String mappingJson;
            try (InputStream is = resource.getInputStream()) {
                mappingJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            // 用 withJson 创建索引
            esClient.indices().create(c -> c.index(indexName).withJson(new java.io.StringReader(mappingJson)));
            log.info("索引 {} 创建成功", indexName);
        } catch (Exception e) {
            log.error("创建索引 {} 失败", indexName, e);
            throw new RuntimeException("创建索引失败: " + indexName, e);
        }
    }

    private List<Long> fetchAllIds(EntityType type) {
        return switch (type) {
            case ARTICLE -> articleMapper.selectList(null).stream()
                    .filter(a -> a.getIsDeleted() == null || a.getIsDeleted() == 0)
                    .map(xyz.crucistau.domain.entity.Article::getId).toList();
            case LEAVE_WORD -> leaveWordMapper.selectList(null).stream()
                    .filter(l -> l.getIsDeleted() == null || l.getIsDeleted() == 0)
                    .map(xyz.crucistau.domain.entity.LeaveWord::getId).toList();
            case TAG -> tagMapper.selectList(null).stream()
                    .map(xyz.crucistau.domain.entity.Tag::getId).toList();
            case CATEGORY -> categoryMapper.selectList(null).stream()
                    .map(xyz.crucistau.domain.entity.Category::getId).toList();
        };
    }
}
