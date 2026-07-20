package xyz.kuailemao.search;

import xyz.kuailemao.domain.dto.SearchArticleDTO;
import xyz.kuailemao.domain.vo.ArticleListVO;
import xyz.kuailemao.domain.vo.InitSearchTitleVO;
import xyz.kuailemao.domain.vo.SearchArticleByContentVO;

import java.util.List;

/**
 * 文章搜索策略接口。
 * <p>
 * 抽象底层搜索引擎，便于在 DB LIKE 搜索与 ES 全文检索之间切换。
 * 当前默认实现为 {@code ArticleDbSearch}，后续可替换为 ES 实现。
 *
 * @author kuailemao
 * @since es-search
 */
public interface ArticleSearch {

    /**
     * 根据关键词搜索文章内容。
     *
     * @param keyword 关键词
     * @return 命中的文章列表
     */
    List<SearchArticleByContentVO> searchByContent(String keyword);

    /**
     * 初始化搜索时的文章标题列表（用于前端搜索建议）。
     *
     * @return 文章标题列表
     */
    List<InitSearchTitleVO> initSearchByTitle();

    /**
     * 后台多条件搜索文章。
     *
     * @param dto 搜索条件
     * @return 文章列表
     */
    List<ArticleListVO> searchArticles(SearchArticleDTO dto);
}
