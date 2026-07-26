package xyz.crucistau.search.builder;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.crucistau.constants.EsIndexConst;
import xyz.crucistau.domain.entity.Article;
import xyz.crucistau.domain.entity.ArticleTag;
import xyz.crucistau.domain.entity.Category;
import xyz.crucistau.domain.entity.Tag;
import xyz.crucistau.domain.entity.User;
import xyz.crucistau.mapper.ArticleMapper;
import xyz.crucistau.mapper.ArticleTagMapper;
import xyz.crucistau.mapper.CategoryMapper;
import xyz.crucistau.mapper.TagMapper;
import xyz.crucistau.mapper.UserMapper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * {@link ArticleDocumentBuilder} 单测：使用 Mockito 模拟 ES 客户端与各 Mapper，
 * 验证：
 * <ol>
 *   <li>正常 upsert：调用 {@link ElasticsearchClient#index} 一次，且文档包含所有字段。</li>
 *   <li>软删（isDeleted=1）：转发到 {@link ElasticsearchClient#delete}。</li>
 *   <li>{@link ArticleDocumentBuilder#delete(Long)}：调用 {@link ElasticsearchClient#delete}。</li>
 * </ol>
 *
 * <p>{@code esClient} 使用 {@link Answers#CALLS_REAL_METHODS} 作为默认回答，
 * 以便 ES 客户端的 {@code index(Function)} / {@code delete(Function)} 默认方法能够触发
 * 对抽象方法 {@code index(IndexRequest)} / {@code delete(DeleteRequest)} 的调用，
 * 从而被桩与抓捕。</p>
 *
 * 
 */
@ExtendWith(MockitoExtension.class)
class ArticleDocumentBuilderTest {

    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private ElasticsearchClient esClient;

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ArticleTagMapper articleTagMapper;

    @Mock
    private TagMapper tagMapper;

    @InjectMocks
    private ArticleDocumentBuilder builder;

    /**
     * 场景 1：文章存在且未删除，应调用一次 {@link ElasticsearchClient#index}，
     * 且组装的文档包含全部字段（含关联查询得到的 username/categoryName/tags）。
     */
    @Test
    void upsert_success_indexes_document_with_all_fields() throws Exception {
        Date createTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2026-01-01 12:00:00");
        Date updateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2026-01-02 12:00:00");
        Article article = Article.builder()
                .id(1L).userId(10L).categoryId(20L).articleCover("cover.jpg")
                .articleTitle("Hello").articleContent("Body").articleType(1)
                .isTop(0).status(1).visitCount(5L)
                .createTime(createTime).updateTime(updateTime)
                .isDeleted(0).build();

        when(articleMapper.selectById(1L)).thenReturn(article);
        when(userMapper.selectById(10L)).thenReturn(
                User.builder().id(10L).username("alice").build());
        when(categoryMapper.selectById(20L)).thenReturn(
                Category.builder().id(20L).categoryName("Tech").build());
        when(articleTagMapper.selectList(any())).thenReturn(List.of());
        when(esClient.index(any(IndexRequest.class))).thenReturn(mock(IndexResponse.class));

        builder.upsert(1L);

        ArgumentCaptor<IndexRequest> captor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(esClient).index(captor.capture());

        IndexRequest captured = captor.getValue();
        assertEquals(EsIndexConst.ARTICLE_INDEX, captured.index());
        assertEquals("1", captured.id());

        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) captured.document();
        assertEquals(1L, doc.get("id"));
        assertEquals(10L, doc.get("userId"));
        assertEquals("alice", doc.get("username"));
        assertEquals(20L, doc.get("categoryId"));
        assertEquals("Tech", doc.get("categoryName"));
        assertEquals("cover.jpg", doc.get("articleCover"));
        assertEquals("Hello", doc.get("articleTitle"));
        assertEquals("Body", doc.get("articleContent"));
        assertEquals(1, doc.get("articleType"));
        assertEquals(0, doc.get("isTop"));
        assertEquals(1, doc.get("status"));
        assertEquals(5L, doc.get("visitCount"));
        assertEquals(List.of(), doc.get("tags"));
        assertEquals("2026-01-01 12:00:00", doc.get("createTime"));
        assertEquals("2026-01-02 12:00:00", doc.get("updateTime"));
    }

    /**
     * 场景 2：文章被软删除（isDeleted=1），upsert 应转发到 delete，且不调用 index。
     */
    @Test
    void upsert_when_soft_deleted_calls_delete_instead_of_index() throws Exception {
        Article article = Article.builder().id(1L).isDeleted(1).build();
        when(articleMapper.selectById(1L)).thenReturn(article);
        when(esClient.delete(any(DeleteRequest.class))).thenReturn(mock(DeleteResponse.class));

        builder.upsert(1L);

        verify(esClient).delete(any(DeleteRequest.class));
        verify(esClient, never()).index(any(IndexRequest.class));
    }

    /**
     * 场景 3：直接调用 delete(id) 应触发一次 {@link ElasticsearchClient#delete}，
     * 且 DeleteRequest 携带正确的索引名与文档 ID。
     */
    @Test
    void delete_invokes_es_delete_with_correct_index_and_id() throws Exception {
        when(esClient.delete(any(DeleteRequest.class))).thenReturn(mock(DeleteResponse.class));

        builder.delete(7L);

        ArgumentCaptor<DeleteRequest> captor = ArgumentCaptor.forClass(DeleteRequest.class);
        verify(esClient).delete(captor.capture());
        DeleteRequest captured = captor.getValue();
        assertEquals(EsIndexConst.ARTICLE_INDEX, captured.index());
        assertEquals("7", captured.id());
    }

    /**
     * 补充场景：upsert 在关联数据齐全（含标签）的情况下，tags 字段为标签名列表。
     */
    @Test
    void upsert_with_tags_indexes_tag_name_list() throws Exception {
        Date createTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2026-01-01 12:00:00");
        Date updateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2026-01-02 12:00:00");
        Article article = Article.builder()
                .id(2L).userId(10L).categoryId(20L)
                .articleTitle("T").articleContent("C").articleType(1)
                .isTop(0).status(1).visitCount(0L)
                .createTime(createTime).updateTime(updateTime)
                .isDeleted(0).build();

        when(articleMapper.selectById(2L)).thenReturn(article);
        when(userMapper.selectById(10L)).thenReturn(
                User.builder().id(10L).username("alice").build());
        when(categoryMapper.selectById(20L)).thenReturn(
                Category.builder().id(20L).categoryName("Tech").build());
        when(articleTagMapper.selectList(any())).thenReturn(List.of(
                ArticleTag.builder().articleId(2L).tagId(100L).build(),
                ArticleTag.builder().articleId(2L).tagId(200L).build()));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(
                Tag.builder().id(100L).tagName("Java").build(),
                Tag.builder().id(200L).tagName("Spring").build()));
        when(esClient.index(any(IndexRequest.class))).thenReturn(mock(IndexResponse.class));

        builder.upsert(2L);

        ArgumentCaptor<IndexRequest> captor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(esClient).index(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) captor.getValue().document();
        assertEquals(List.of("Java", "Spring"), doc.get("tags"));
    }
}
