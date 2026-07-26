package xyz.crucistau.search.builder;

import xyz.crucistau.domain.dto.EsSyncMessage.EntityType;

/**
 * ES 文档构建器接口。
 * <p>
 * 每种 {@link EntityType} 对应一个实现：
 * <ul>
 *     <li>{@link #upsert(Long)} —— 从 MySQL 查询关联数据后写入/更新 ES 索引（{@code esClient.index(...)})。</li>
 *     <li>{@link #delete(Long)} —— 按 ID 删除 ES 文档（{@code esClient.delete(...)})。</li>
 * </ul>
 *
 *
 */
public interface DocumentBuilder {

    /**
     * 当前构建器负责的实体类型，用于消费端路由。
     *
     * @return 实体类型枚举
     */
    EntityType type();

    /**
     * 目标 ES 索引名。
     *
     * @return 索引名称
     */
    String indexName();

    /**
     * 按 ID 从 MySQL 加载关联数据并写入/更新 ES 索引。
     *
     * @param id 实体主键 ID
     */
    void upsert(Long id);

    /**
     * 按 ID 删除 ES 文档。
     *
     * @param id 实体主键 ID
     */
    void delete(Long id);
}
