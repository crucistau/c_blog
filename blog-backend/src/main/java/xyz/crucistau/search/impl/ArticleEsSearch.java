package xyz.crucistau.search.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import xyz.crucistau.constants.EsIndexConst;
import xyz.crucistau.domain.dto.SearchArticleDTO;
import xyz.crucistau.domain.vo.ArticleListVO;
import xyz.crucistau.domain.vo.InitSearchTitleVO;
import xyz.crucistau.domain.vo.SearchArticleByContentVO;
import xyz.crucistau.search.ArticleSearch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文章搜索的 ES 实现。
 * <p>
 * 使用 {@link ElasticsearchClient} 进行全文检索，支持多字段匹配、
 * 高亮、条件过滤；ES 异常时自动回退至 {@link ArticleDbSearch}。
 *
 * @author kuailemao
 * @since es-search
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "search.enabled", havingValue = "true", matchIfMissing = true)
public class ArticleEsSearch implements ArticleSearch {

    @Resource
    private ElasticsearchClient esClient;

    @Resource
    private ObjectProvider<ArticleSearch> dbProvider;

    @Value("${search.fallback-on-error:true}")
    private boolean fallbackOnError;

    private ArticleSearch fallback() {
        return dbProvider.stream()
                .filter(s -> s instanceof ArticleDbSearch)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ArticleDbSearch not found"));
    }

    @Override
    public List<SearchArticleByContentVO> searchByContent(String keyword) {
        try {
            SearchResponse<Map> response = esClient.search(s -> s
                    .index(EsIndexConst.ARTICLE_INDEX)
                    .query(q -> q
                            .bool(b -> b
                                    .must(m -> m
                                            .multiMatch(mm -> mm
                                                    .fields("articleTitle", "articleContent", "categoryName", "tags")
                                                    .query(keyword)
                                            )
                                    )
                                    .filter(f -> f
                                            .term(t -> t.field("status").value(FieldValue.of(1)))
                                    )
                            )
                    )
                    .highlight(h -> h
                            .requireFieldMatch(false)
                            .fields("articleTitle", hf -> hf
                                    .preTags(EsIndexConst.HIGHLIGHT_PRE_TAG)
                                    .postTags(EsIndexConst.HIGHLIGHT_POST_TAG)
                                    .numberOfFragments(0))
                            .fields("articleContent", hf -> hf
                                    .preTags(EsIndexConst.HIGHLIGHT_PRE_TAG)
                                    .postTags(EsIndexConst.HIGHLIGHT_POST_TAG)
                                    .fragmentSize(EsIndexConst.CONTENT_SNIPPET_LENGTH)
                                    .numberOfFragments(3))
                    )
                    .sort(so -> so.score(sc -> sc.order(SortOrder.Desc)))
                    .sort(so -> so.field(f -> f.field("createTime").order(SortOrder.Desc)))
                    .size(20), Map.class);

            return response.hits().hits().stream().map(hit -> {
                Map<String, Object> source = hit.source();
                SearchArticleByContentVO vo = new SearchArticleByContentVO();
                vo.setId(source.get("id") != null ? Long.valueOf(source.get("id").toString()) : null);
                vo.setArticleTitle(source.get("articleTitle") != null ? source.get("articleTitle").toString() : "");
                vo.setVisitCount(source.get("visitCount") != null ? Long.valueOf(source.get("visitCount").toString()) : 0L);
                vo.setCategoryName(source.get("categoryName") != null ? source.get("categoryName").toString() : "");

                // 高亮处理
                Map<String, List<String>> highlights = hit.highlight();
                if (highlights != null) {
                    List<String> titleHighlights = highlights.get("articleTitle");
                    if (titleHighlights != null && !titleHighlights.isEmpty()) {
                        vo.setArticleTitle(titleHighlights.get(0));
                    }
                    List<String> contentHighlights = highlights.get("articleContent");
                    if (contentHighlights != null && !contentHighlights.isEmpty()) {
                        vo.setArticleContent(String.join("...", contentHighlights));
                    } else if (source.get("articleContent") != null) {
                        String content = source.get("articleContent").toString();
                        vo.setArticleContent(content.length() > EsIndexConst.CONTENT_SNIPPET_LENGTH
                                ? content.substring(0, EsIndexConst.CONTENT_SNIPPET_LENGTH) + "..."
                                : content);
                    }
                } else if (source.get("articleContent") != null) {
                    String content = source.get("articleContent").toString();
                    vo.setArticleContent(content.length() > EsIndexConst.CONTENT_SNIPPET_LENGTH
                            ? content.substring(0, EsIndexConst.CONTENT_SNIPPET_LENGTH) + "..."
                            : content);
                }
                return vo;
            }).toList();
        } catch (Exception e) {
            log.error("ES 内容搜索失败，回退DB keyword={}", keyword, e);
            if (fallbackOnError) return fallback().searchByContent(keyword);
            throw new RuntimeException("ES 搜索失败", e);
        }
    }

    @Override
    public List<InitSearchTitleVO> initSearchByTitle() {
        try {
            SearchResponse<Map> response = esClient.search(s -> s
                    .index(EsIndexConst.ARTICLE_INDEX)
                    .query(q -> q
                            .bool(b -> b
                                    .filter(f -> f.term(t -> t.field("status").value(FieldValue.of(1))))
                            )
                    )
                    .source(sc -> sc.filter(sf -> sf.includes("id", "articleTitle", "categoryName", "visitCount")))
                    .sort(so -> so.field(f -> f.field("createTime").order(SortOrder.Desc)))
                    .size(50), Map.class);

            return response.hits().hits().stream().map(hit -> {
                Map<String, Object> source = hit.source();
                InitSearchTitleVO vo = new InitSearchTitleVO();
                vo.setId(source.get("id") != null ? Long.valueOf(source.get("id").toString()) : null);
                vo.setArticleTitle(source.get("articleTitle") != null ? source.get("articleTitle").toString() : "");
                vo.setCategoryName(source.get("categoryName") != null ? source.get("categoryName").toString() : "");
                vo.setVisitCount(source.get("visitCount") != null ? Long.valueOf(source.get("visitCount").toString()) : 0L);
                return vo;
            }).toList();
        } catch (Exception e) {
            log.error("ES 标题初始化搜索失败，回退DB", e);
            if (fallbackOnError) return fallback().initSearchByTitle();
            throw new RuntimeException("ES 搜索失败", e);
        }
    }

    @Override
    public List<ArticleListVO> searchArticles(SearchArticleDTO dto) {
        try {
            SearchResponse<Map> response = esClient.search(s -> {
                s.index(EsIndexConst.ARTICLE_INDEX)
                        .query(q -> q
                                .bool(b -> {
                                    b.filter(f -> f.term(t -> t.field("status").value(FieldValue.of(1))));
                                    if (dto.getArticleTitle() != null && !dto.getArticleTitle().isEmpty()) {
                                        b.must(m -> m.match(ma -> ma.field("articleTitle").query(dto.getArticleTitle())));
                                    }
                                    if (dto.getCategoryId() != null) {
                                        b.filter(f -> f.term(t -> t.field("categoryId").value(FieldValue.of(dto.getCategoryId()))));
                                    }
                                    if (dto.getIsTop() != null) {
                                        b.filter(f -> f.term(t -> t.field("isTop").value(FieldValue.of(dto.getIsTop()))));
                                    }
                                    return b;
                                })
                        )
                        .sort(so -> so.field(f -> f.field("createTime").order(SortOrder.Desc)))
                        .size(50);
                return s;
            }, Map.class);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return response.hits().hits().stream().map(hit -> {
                Map<String, Object> source = hit.source();
                ArticleListVO vo = new ArticleListVO();
                vo.setId(source.get("id") != null ? Long.valueOf(source.get("id").toString()) : null);
                vo.setArticleTitle(source.get("articleTitle") != null ? source.get("articleTitle").toString() : "");
                vo.setArticleCover(source.get("articleCover") != null ? source.get("articleCover").toString() : "");
                vo.setCategoryName(source.get("categoryName") != null ? source.get("categoryName").toString() : "");
                vo.setVisitCount(source.get("visitCount") != null ? Long.valueOf(source.get("visitCount").toString()) : 0L);
                vo.setIsTop(source.get("isTop") != null ? Integer.valueOf(source.get("isTop").toString()) : 0);
                // createTime: ES 存储为格式化字符串，VO 字段为 Date
                if (source.get("createTime") != null) {
                    try {
                        vo.setCreateTime(sdf.parse(source.get("createTime").toString()));
                    } catch (Exception ex) {
                        log.warn("解析 createTime 失败: {}", source.get("createTime"));
                    }
                }
                // tags: ES 存储为 List，VO 字段为 tagsName
                Object tagsObj = source.get("tags");
                if (tagsObj instanceof List<?> tagList) {
                    List<String> tagsName = new ArrayList<>();
                    for (Object tag : tagList) {
                        if (tag != null) {
                            tagsName.add(tag.toString());
                        }
                    }
                    vo.setTagsName(tagsName);
                }
                return vo;
            }).toList();
        } catch (Exception e) {
            log.error("ES 文章搜索失败，回退DB dto={}", dto, e);
            if (fallbackOnError) return fallback().searchArticles(dto);
            throw new RuntimeException("ES 搜索失败", e);
        }
    }
}
