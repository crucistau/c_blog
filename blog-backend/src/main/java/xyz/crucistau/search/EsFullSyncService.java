package xyz.crucistau.search;

import xyz.crucistau.domain.dto.EsSyncMessage.EntityType;

/**
 * ES 全量重建服务：从 MySQL 全量数据重建指定实体的 ES 索引。
 *
 *
 * @date 2026-07-20
 * @description ES 全量同步服务接口
 */
public interface EsFullSyncService {

    /**
     * 全量同步指定实体类型的数据到 ES。
     *
     * @param type 实体类型
     * @return 同步的数据条数
     */
    long fullSync(EntityType type);
}
