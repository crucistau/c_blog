package xyz.crucistau.search.impl;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.crucistau.domain.dto.SearchArticleDTO;
import xyz.crucistau.domain.vo.ArticleListVO;
import xyz.crucistau.domain.vo.InitSearchTitleVO;
import xyz.crucistau.domain.vo.SearchArticleByContentVO;
import xyz.crucistau.search.ArticleSearch;
import xyz.crucistau.service.ArticleService;

import java.util.List;

/**
 * 文章搜索的 DB 实现。
 * <p>
 * 委托 {@link ArticleService} 现有的 LIKE 查询方法，作为 ES 接入前的默认策略，
 * 不引入任何业务逻辑变更。
 *
 * @author kuailemao
 * @since es-search
 */
@Component
public class ArticleDbSearch implements ArticleSearch {

    @Resource
    private ArticleService articleService;

    @Override
    public List<SearchArticleByContentVO> searchByContent(String keyword) {
        return articleService.searchArticleByContent(keyword);
    }

    @Override
    public List<InitSearchTitleVO> initSearchByTitle() {
        return articleService.initSearchByTitle();
    }

    @Override
    public List<ArticleListVO> searchArticles(SearchArticleDTO dto) {
        return articleService.searchArticle(dto);
    }
}
