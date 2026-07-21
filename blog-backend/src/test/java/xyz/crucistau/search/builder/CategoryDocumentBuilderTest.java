package xyz.crucistau.search.builder;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.crucistau.constants.EsIndexConst;
import xyz.crucistau.domain.dto.EsSyncMessage.EntityType;
import xyz.crucistau.domain.entity.Category;
import xyz.crucistau.mapper.CategoryMapper;

import java.util.Date;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CategoryDocumentBuilder} 单测。
 * <p>
 * 纯 Mockito：验证 upsert 路径调用 {@code esClient.index}，delete 路径调用 {@code esClient.delete}，
 * 且 type/indexName 与常量一致。
 *
 * @author kuailemao
 * @since es-search
 */
@ExtendWith(MockitoExtension.class)
class CategoryDocumentBuilderTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ElasticsearchClient esClient;

    @InjectMocks
    private CategoryDocumentBuilder builder;

    @Test
    void type_and_indexName_are_correct() {
        assertEquals(EntityType.CATEGORY, builder.type());
        assertEquals(EsIndexConst.CATEGORY_INDEX, builder.indexName());
    }

    @Test
    void upsert_loads_category_and_calls_es_index() throws Exception {
        Long id = 10L;
        Category category = new Category();
        category.setId(id);
        category.setCategoryName("后端");
        category.setIsDeleted(0);
        category.setCreateTime(new Date());
        category.setUpdateTime(new Date());
        when(categoryMapper.selectById(id)).thenReturn(category);

        builder.upsert(id);

        verify(esClient).index(any(Function.class));
        verify(esClient, never()).delete(any(Function.class));
    }

    @Test
    void upsert_soft_deleted_calls_es_delete_instead_of_index() throws Exception {
        Long id = 11L;
        Category category = new Category();
        category.setId(id);
        category.setCategoryName("已删");
        category.setIsDeleted(1);
        when(categoryMapper.selectById(id)).thenReturn(category);

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
        when(categoryMapper.selectById(id)).thenReturn(null);

        builder.upsert(id);

        verify(esClient, never()).index(any(Function.class));
        verify(esClient, never()).delete(any(Function.class));
    }
}
