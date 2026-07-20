package xyz.kuailemao.search.builder;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.kuailemao.constants.EsIndexConst;
import xyz.kuailemao.domain.dto.EsSyncMessage.EntityType;
import xyz.kuailemao.domain.entity.Tag;
import xyz.kuailemao.mapper.TagMapper;
import xyz.kuailemao.utils.DateUtils;
import xyz.kuailemao.utils.TimeUtils;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 标签（Tag）的 ES 文档构建器。
 * <p>
 * 索引：{@link EsIndexConst#TAG_INDEX}；类型：{@link EntityType#TAG}。
 * <ul>
 *     <li>{@code upsert}：从 {@code t_tag} 加载记录组装为 {@code Map} 后调用 {@code esClient.index}。
 *     若记录已被软删除（{@code isDeleted=1}）则改为调用 {@code esClient.delete} 从索引中清除。</li>
 *     <li>{@code delete}：直接调用 {@code esClient.delete}。</li>
 * </ul>
 * 任何 I/O 异常包装为 {@link RuntimeException} 抛出，由 MQ 重试 / 死信策略接管。
 *
 * @author kuailemao
 * @since es-search
 */
@Slf4j
@Component
public class TagDocumentBuilder implements DocumentBuilder {

    @Resource
    private TagMapper tagMapper;

    @Resource
    private ElasticsearchClient esClient;

    @Override
    public EntityType type() {
        return EntityType.TAG;
    }

    @Override
    public String indexName() {
        return EsIndexConst.TAG_INDEX;
    }

    @Override
    public void upsert(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            // 物理删除或不存在：上游 SAVE/UPDATE 事件后已无对应数据，记录警告后跳过。
            log.warn("Tag upsert 跳过：id={} 在 MySQL 中不存在", id);
            return;
        }
        if (tag.getIsDeleted() != null && tag.getIsDeleted() == 1) {
            // 软删除：从 ES 中清除。
            log.info("Tag 软删除，从 ES 清除：id={}", id);
            doDelete(id);
            return;
        }

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", tag.getId());
        doc.put("tagName", tag.getTagName());
        doc.put("createTime", formatTime(tag.getCreateTime()));
        doc.put("updateTime", formatTime(tag.getUpdateTime()));

        try {
            esClient.index(i -> i
                    .index(indexName())
                    .id(String.valueOf(id))
                    .document(doc));
            log.info("Tag 索引写入成功：id={}", id);
        } catch (IOException e) {
            throw new RuntimeException("Tag upsert 失败：id=" + id, e);
        }
    }

    @Override
    public void delete(Long id) {
        doDelete(id);
    }

    private void doDelete(Long id) {
        try {
            esClient.delete(d -> d
                    .index(indexName())
                    .id(String.valueOf(id)));
            log.info("Tag 索引删除成功：id={}", id);
        } catch (IOException e) {
            throw new RuntimeException("Tag delete 失败：id=" + id, e);
        }
    }

    /**
     * 日期格式化为 {@code yyyy-MM-dd HH:mm:ss} 字符串。
     * <p>
     * 注：{@code createTime}/{@code updateTime} 由 MyBatis-Plus 自动填充，
     * 正常非空；此处对 null 兜底以兼容历史脏数据 / 手工构造的测试数据。
     */
    private static String formatTime(Date date) {
        return date == null ? null : TimeUtils.format(date, DateUtils.YYYY_MM_DD_HH_MM_SS);
    }
}
