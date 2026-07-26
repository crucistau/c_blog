package xyz.crucistau.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import xyz.crucistau.domain.entity.ArticleTag;
import xyz.crucistau.mapper.ArticleTagMapper;
import xyz.crucistau.service.ArticleTagService;

/**
 * (ArticleTag)表服务实现类
 *
 *
 * @since 2023-10-15 02:29:13
 */
@Service("articleTagService")
public class ArticleTagServiceImpl extends ServiceImpl<ArticleTagMapper, ArticleTag> implements ArticleTagService {

}
