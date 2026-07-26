package xyz.crucistau.search.builder;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.crucistau.constants.EsIndexConst;
import xyz.crucistau.domain.dto.EsSyncMessage.EntityType;
import xyz.crucistau.domain.entity.LeaveWord;
import xyz.crucistau.domain.entity.User;
import xyz.crucistau.mapper.LeaveWordMapper;
import xyz.crucistau.mapper.UserMapper;
import xyz.crucistau.utils.TimeUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 留言（LeaveWord）ES 文档构建器。
 * <p>
 * 从 MySQL 加载留言及其关联用户名，组装为扁平文档后通过
 * {@link ElasticsearchClient#index} 写入 {@link EsIndexConst#LEAVE_WORD_INDEX}。
 * 留言被软删除（或不存在）时改走 {@link #delete(Long)} 清理 ES 文档。
 *
 *
 */
@Slf4j
@Component
public class LeaveWordDocumentBuilder implements DocumentBuilder {

    @Resource
    private ElasticsearchClient esClient;
    @Resource
    private LeaveWordMapper leaveWordMapper;
    @Resource
    private UserMapper userMapper;

    @Override
    public EntityType type() {
        return EntityType.LEAVE_WORD;
    }

    @Override
    public String indexName() {
        return EsIndexConst.LEAVE_WORD_INDEX;
    }

    @Override
    public void upsert(Long id) {
        try {
            LeaveWord lw = leaveWordMapper.selectById(id);
            if (lw == null || lw.getIsDeleted() == 1) {
                delete(id);
                return;
            }
            String username = Optional.ofNullable(userMapper.selectById(lw.getUserId()))
                    .map(User::getUsername)
                    .orElse("");

            Map<String, Object> doc = new HashMap<>();
            doc.put("id", lw.getId());
            doc.put("userId", lw.getUserId());
            doc.put("username", username);
            doc.put("content", lw.getContent());
            doc.put("isCheck", lw.getIsCheck());
            doc.put("createTime", TimeUtils.format(lw.getCreateTime(), "yyyy-MM-dd HH:mm:ss"));
            doc.put("updateTime", TimeUtils.format(lw.getUpdateTime(), "yyyy-MM-dd HH:mm:ss"));

            esClient.index(i -> i.index(indexName()).id(String.valueOf(id)).document(doc));
        } catch (Exception e) {
            log.error("留言同步ES失败 id={}", id, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(Long id) {
        try {
            esClient.delete(d -> d.index(indexName()).id(String.valueOf(id)));
        } catch (Exception e) {
            log.error("留言从ES删除失败 id={}", id, e);
            throw new RuntimeException(e);
        }
    }
}
