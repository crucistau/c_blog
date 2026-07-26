package xyz.crucistau.search.builder;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.crucistau.constants.EsIndexConst;
import xyz.crucistau.domain.dto.EsSyncMessage.EntityType;
import xyz.crucistau.domain.entity.LeaveWord;
import xyz.crucistau.domain.entity.User;
import xyz.crucistau.mapper.LeaveWordMapper;
import xyz.crucistau.mapper.UserMapper;

import java.util.Date;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LeaveWordDocumentBuilder} 单测。
 * <p>
 * 纯 Mockito：验证 upsert 路径调用 {@code esClient.index}，delete 路径调用 {@code esClient.delete}，
 * 且 type/indexName 与常量一致。
 *
 * 
 * @since es-search
 */
@ExtendWith(MockitoExtension.class)
class LeaveWordDocumentBuilderTest {

    @Mock
    private LeaveWordMapper leaveWordMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ElasticsearchClient esClient;

    @InjectMocks
    private LeaveWordDocumentBuilder builder;

    @Test
    void type_and_indexName_are_correct() {
        assertEquals(EntityType.LEAVE_WORD, builder.type());
        assertEquals(EsIndexConst.LEAVE_WORD_INDEX, builder.indexName());
    }

    @Test
    void upsert_loads_leave_word_and_calls_es_index() throws Exception {
        Long id = 10L;
        LeaveWord lw = LeaveWord.builder()
                .id(id)
                .userId(20L)
                .content("你好")
                .isCheck(1)
                .isDeleted(0)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
        when(leaveWordMapper.selectById(id)).thenReturn(lw);
        User user = new User();
        user.setId(20L);
        user.setUsername("alice");
        when(userMapper.selectById(20L)).thenReturn(user);

        builder.upsert(id);

        verify(esClient).index(any(Function.class));
        verify(esClient, never()).delete(any(Function.class));
    }

    @Test
    void upsert_soft_deleted_calls_es_delete_instead_of_index() throws Exception {
        Long id = 11L;
        LeaveWord lw = LeaveWord.builder()
                .id(id)
                .userId(20L)
                .content("已删")
                .isCheck(0)
                .isDeleted(1)
                .build();
        when(leaveWordMapper.selectById(id)).thenReturn(lw);

        builder.upsert(id);

        verify(esClient).delete(any(Function.class));
        verify(esClient, never()).index(any(Function.class));
    }

    @Test
    void delete_calls_es_delete_directly() throws Exception {
        Long id = 12L;

        builder.delete(id);

        verify(esClient).delete(any(Function.class));
        verify(esClient, never()).index(any(Function.class));
    }

    @Test
    void upsert_skips_when_mysql_record_missing() throws Exception {
        Long id = 13L;
        when(leaveWordMapper.selectById(id)).thenReturn(null);

        builder.upsert(id);

        verify(esClient, never()).index(any(Function.class));
        verify(esClient, never()).delete(any(Function.class));
    }
}
