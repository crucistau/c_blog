package xyz.crucistau.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ES 数据同步消息载体。
 * <p>
 * 用于在 RabbitMQ 队列中传递实体变更事件，由消费端根据 {@link EntityType} 与 {@link SyncType}
 * 路由到对应的 {@code DocumentBuilder} 执行索引写入/删除。
 *
 * 
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsSyncMessage implements Serializable {

    /**
     * 实体类型，决定路由到哪个 DocumentBuilder。
     */
    private EntityType entityType;

    /**
     * 实体主键 ID。
     */
    private Long id;

    /**
     * 同步操作类型。
     */
    private SyncType syncType;

    /**
     * ES 索引对应的实体类型。
     */
    public enum EntityType {
        ARTICLE,
        LEAVE_WORD,
        TAG,
        CATEGORY
    }

    /**
     * 同步操作类型：SAVE/UPDATE 走 upsert，DELETE 走 delete。
     */
    public enum SyncType {
        SAVE,
        UPDATE,
        DELETE
    }
}
