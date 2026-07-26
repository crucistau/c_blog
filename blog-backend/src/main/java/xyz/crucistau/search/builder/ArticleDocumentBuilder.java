package xyz.crucistau.search.builder;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.crucistau.constants.EsIndexConst;
import xyz.crucistau.domain.dto.EsSyncMessage.EntityType;
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
import xyz.crucistau.utils.TimeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文章 ES 文档构建器。
 * <p>
 * 从 MySQL 加载文章及其关联数据（作者昵称、分类名、标签列表），
 * 组装为扁平文档后通过 {@link ElasticsearchClient#index} 写入 {@link EsIndexConst#ARTICLE_INDEX}。
 * 文章被软删除时改走 {@link #delete(Long)} 清理 ES 文档。
 *
 * 
 */
@Slf4j
@Component
public class ArticleDocumentBuilder implements DocumentBuilder {

    @Resource
    private ElasticsearchClient esClient;
    @Resource
    private ArticleMapper articleMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private CategoryMapper categoryMapper;
    @Resource
    private ArticleTagMapper articleTagMapper;
    @Resource
    private TagMapper tagMapper;

    @Override
    public EntityType type() {
        return EntityType.ARTICLE;
    }

    @Override
    public String indexName() {
        return EsIndexConst.ARTICLE_INDEX;
    }

    @Override
    public void upsert(Long id) {
        try {
            Article a = articleMapper.selectById(id);
            if (a == null || a.getIsDeleted() == 1) {
                delete(id);
                return;
            }
            String username = Optional.ofNullable(userMapper.selectById(a.getUserId()))
                    .map(User::getUsername)
                    .orElse("");
            String categoryName = Optional.ofNullable(categoryMapper.selectById(a.getCategoryId()))
                    .map(Category::getCategoryName)
                    .orElse("");
            List<Long> tagIds = articleTagMapper.selectList(
                            new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, id))
                    .stream()
                    .map(ArticleTag::getTagId)
                    .toList();
            List<String> tags = tagIds.isEmpty()
                    ? List.of()
                    : tagMapper.selectBatchIds(tagIds).stream().map(Tag::getTagName).toList();

            Map<String, Object> doc = new HashMap<>();
            doc.put("id", a.getId());
            doc.put("userId", a.getUserId());
            doc.put("username", username);
            doc.put("categoryId", a.getCategoryId());
            doc.put("categoryName", categoryName);
            doc.put("articleCover", a.getArticleCover());
            doc.put("articleTitle", a.getArticleTitle());
            doc.put("articleContent", a.getArticleContent());
            doc.put("articleType", a.getArticleType());
            doc.put("isTop", a.getIsTop());
            doc.put("status", a.getStatus());
            doc.put("visitCount", a.getVisitCount());
            doc.put("tags", tags);
            doc.put("createTime", TimeUtils.format(a.getCreateTime(), "yyyy-MM-dd HH:mm:ss"));
            doc.put("updateTime", TimeUtils.format(a.getUpdateTime(), "yyyy-MM-dd HH:mm:ss"));

            esClient.index(i -> i.index(indexName()).id(String.valueOf(id)).document(doc));
        } catch (Exception e) {
            log.error("文章同步ES失败 id={}", id, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(Long id) {
        try {
            esClient.delete(d -> d.index(indexName()).id(String.valueOf(id)));
        } catch (Exception e) {
            log.error("文章从ES删除失败 id={}", id, e);
            throw new RuntimeException(e);
        }
    }
}
