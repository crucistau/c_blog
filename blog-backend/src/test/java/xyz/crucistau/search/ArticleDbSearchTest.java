package xyz.crucistau.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.crucistau.domain.dto.SearchArticleDTO;
import xyz.crucistau.domain.vo.ArticleListVO;
import xyz.crucistau.domain.vo.InitSearchTitleVO;
import xyz.crucistau.domain.vo.SearchArticleByContentVO;
import xyz.crucistau.search.impl.ArticleDbSearch;
import xyz.crucistau.service.ArticleService;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ArticleDbSearch} 纯委托单测：验证三个方法各调用一次对应的 {@link ArticleService} 方法，
 * 并原样回传结果。
 *
 * @author kuailemao
 * @since es-search
 */
@ExtendWith(MockitoExtension.class)
class ArticleDbSearchTest {

    @Mock
    private ArticleService articleService;

    @InjectMocks
    private ArticleDbSearch articleDbSearch;

    @Test
    void searchByContent_delegates_to_articleService() {
        String keyword = "spring";
        List<SearchArticleByContentVO> expected = Collections.singletonList(mock(SearchArticleByContentVO.class));
        when(articleService.searchArticleByContent(keyword)).thenReturn(expected);

        List<SearchArticleByContentVO> actual = articleDbSearch.searchByContent(keyword);

        assertSame(expected, actual);
        verify(articleService, times(1)).searchArticleByContent(keyword);
    }

    @Test
    void initSearchByTitle_delegates_to_articleService() {
        List<InitSearchTitleVO> expected = Collections.singletonList(mock(InitSearchTitleVO.class));
        when(articleService.initSearchByTitle()).thenReturn(expected);

        List<InitSearchTitleVO> actual = articleDbSearch.initSearchByTitle();

        assertSame(expected, actual);
        verify(articleService, times(1)).initSearchByTitle();
    }

    @Test
    void searchArticles_delegates_to_articleService() {
        SearchArticleDTO dto = new SearchArticleDTO();
        List<ArticleListVO> expected = Collections.singletonList(mock(ArticleListVO.class));
        when(articleService.searchArticle(dto)).thenReturn(expected);

        List<ArticleListVO> actual = articleDbSearch.searchArticles(dto);

        assertSame(expected, actual);
        verify(articleService, times(1)).searchArticle(dto);
    }
}
