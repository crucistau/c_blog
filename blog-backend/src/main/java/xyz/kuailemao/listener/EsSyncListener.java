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

/**
 * ES 同步消息统一消费分发器。
 * <p>
 * 消费 {@link RabbitConst#ES_SYNC_QUEUE}，按 {@link EsSyncMessage.EntityType}
 * 路由到对应的 {@link DocumentBuilder}：
 * <ul>
 *     <li>{@code SAVE}/{@code UPDATE} → {@link DocumentBuilder#upsert(Long)}</li>
 *     <li>{@code DELETE} → {@link DocumentBuilder#delete(Long)}</li>
 * </ul>
 * 消息处理抛出的异常由 {@link RabbitListenerErrorHandler}（bean 名
 * {@code "rabbitListenerErrorHandler"}）接管，再经 {@code application.yml}
 * 配置的重试策略（max-attempts: 3）重试，最终入死信队列。
 *
 * @author kuailemao
 */
@Slf4j
@Component
public class EsSyncListener {

    @Resource
    private ElasticsearchClient esClient;

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
        if (builder == null) {
            log.warn("无对应 DocumentBuilder: {}", msg.getEntityType());
            return;
        }
        switch (msg.getSyncType()) {
            case SAVE, UPDATE -> builder.upsert(msg.getId());
            case DELETE -> builder.delete(msg.getId());
        }
    }
}
