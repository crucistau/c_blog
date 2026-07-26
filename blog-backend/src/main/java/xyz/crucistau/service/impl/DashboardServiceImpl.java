package xyz.crucistau.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.crucistau.domain.entity.Article;
import xyz.crucistau.domain.entity.Category;
import xyz.crucistau.domain.entity.Comment;
import xyz.crucistau.domain.entity.Tag;
import xyz.crucistau.domain.vo.CityStatVO;
import xyz.crucistau.domain.vo.DashboardOverviewVO;
import xyz.crucistau.domain.vo.RegionStatVO;
import xyz.crucistau.domain.vo.TrendVO;
import xyz.crucistau.mapper.ArticleMapper;
import xyz.crucistau.mapper.CategoryMapper;
import xyz.crucistau.mapper.CommentMapper;
import xyz.crucistau.mapper.TagMapper;
import xyz.crucistau.mapper.VisitorLogMapper;
import xyz.crucistau.service.DashboardService;

import java.util.List;

@Service
public class DashboardServiceImpl implements DashboardService {

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private TagMapper tagMapper;

    @Resource
    private VisitorLogMapper visitorLogMapper;

    @Override
    public DashboardOverviewVO getOverview() {
        Long articleCount = articleMapper.selectCount(
                new LambdaQueryWrapper<Article>().eq(Article::getStatus, 1));

        List<Article> articles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>().select(Article::getVisitCount));
        Long visitCount = articles.stream()
                .mapToLong(a -> a.getVisitCount() != null ? a.getVisitCount() : 0L)
                .sum();

        Long commentCount = commentMapper.selectCount(null);

        Long categoryCount = categoryMapper.selectCount(null);
        Long tagCount = tagMapper.selectCount(null);

        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setArticleCount(articleCount);
        vo.setVisitCount(visitCount);
        vo.setCommentCount(commentCount);
        vo.setCategoryCount(categoryCount);
        vo.setTagCount(tagCount);
        return vo;
    }

    @Override
    public List<TrendVO> getVisitTrend(Integer days) {
        return visitorLogMapper.selectVisitTrend(days);
    }

    @Override
    public List<TrendVO> getArticleTrend(Integer days) {
        return articleMapper.selectArticleTrend(days);
    }

    @Override
    public List<RegionStatVO> getRegionStatistics(Integer days) {
        return visitorLogMapper.selectRegionStatistics(days);
    }

    @Override
    public List<CityStatVO> getCityStatistics(Integer days) {
        return visitorLogMapper.selectCityStatistics(days);
    }
}
