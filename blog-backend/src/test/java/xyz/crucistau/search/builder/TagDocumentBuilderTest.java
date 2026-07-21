package xyz.crucistau.search.builder;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.crucistau.constants.EsIndexConst;
import xyz.crucistau.domain.dto.EsSyncMessage.EntityType;
import xyz.crucistau.domain.entity.Tag;
import xyz.crucistau.mapper.TagMapper;

import java.util.Date;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TagDocumentBuilder} 单测。
 * <p>
 * 纯 Mockito：验证 upsert 路径调用 {@code esClient.index}，delete 路径调用 {@code esClient.delete}，
 * 且 type/indexName 与常量一致。
 *
 * @author kuailemao
 * @since es-search
 */
@ExtendWith(MockitoExtension.class)
class TagDocumentBuilderTest {

    @Mock
    private TagMapper tagMapper;

    @Mock
    private ElasticsearchClient esClient;

    @InjectMocks
    private TagDocumentBuilder builder;

    @Test
    void type_and_indexName_are_correct() {
        assertEquals(EntityType.TAG, builder.type());
        assertEquals(EsIndexConst.TAG_INDEX, builder.indexName());
    }

    @Test
    void upsert_loads_tag_and_calls_es_index() throws Exception {
        Long id = 10L;
        Tag tag = new Tag();
        tag.setId(id);
        tag.setTagName("Java");
        tag.setIsDeleted(0);
        tag.setCreateTime(new Date());
        tag.setUpdateTime(new Date());
        when(tagMapper.selectById(id)).thenReturn(tag);

        builder.upsert(id);

        verify(esClient).index(any(Function.class));
        verify(esClient, never()).delete(any(Function.class));
    }

    @Test
    void upsert_soft_deleted_calls_es_delete_instead_of_index() throws Exception {
        Long id = 11L;
        Tag tag = new Tag();
        tag.setId(id);
        tag.setTagName("已删");
        tag.setIsDeleted(1);
        when(tagMapper.selectById(id)).thenReturn(tag);

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
        when(tagMapper.selectById(id)).thenReturn(null);

        builder.upsert(id);

        verify(esClient, never()).index(any(Function.class));
        verify(esClient, never()).delete(any(Function.class));
    }
}
