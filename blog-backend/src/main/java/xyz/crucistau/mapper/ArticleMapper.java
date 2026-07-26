package xyz.crucistau.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xyz.crucistau.domain.entity.Article;
import xyz.crucistau.domain.vo.ArticleVO;
import xyz.crucistau.domain.vo.TrendVO;

import java.util.List;


/**
 * (Article)表数据库访问层
 *
 * 
 * @since 2023-10-15 02:29:11
 */
public interface ArticleMapper extends BaseMapper<Article> {

    @Select("SELECT * FROM t_article WHERE status = #{status} and is_deleted = 0 ORDER BY RAND() LIMIT #{limit}")
    List<Article> selectRandomArticles(@Param("status") Integer status, @Param("limit") Integer limit);

    List<TrendVO> selectArticleTrend(@Param("days") Integer days);
}
