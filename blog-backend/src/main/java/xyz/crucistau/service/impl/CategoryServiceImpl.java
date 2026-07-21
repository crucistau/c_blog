package xyz.crucistau.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.crucistau.constants.FunctionConst;
import xyz.crucistau.domain.dto.CategoryDTO;
import xyz.crucistau.domain.dto.EsSyncMessage;
import xyz.crucistau.domain.dto.SearchCategoryDTO;
import xyz.crucistau.domain.entity.Article;
import xyz.crucistau.domain.entity.Category;
import xyz.crucistau.domain.response.ResponseResult;
import xyz.crucistau.domain.vo.CategoryVO;
import xyz.crucistau.mapper.ArticleMapper;
import xyz.crucistau.mapper.CategoryMapper;
import xyz.crucistau.service.CategoryService;
import xyz.crucistau.utils.StringUtils;

import java.util.List;

/**
 * (Category)表服务实现类
 *
 * @author kuailemao
 * @since 2023-10-15 02:29:14
 */
@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.exchange.es}")
    private String ES_EXCHANGE;

    @Value("${spring.rabbitmq.routingKey.es-sync}")
    private String ES_SYNC_ROUTING_KEY;

    @Override
    public List<CategoryVO> listAllCategory() {
        List<Category> categories = this.query().list();

        return categories.stream().map(category -> category.asViewObject(CategoryVO.class, item -> {
            item.setArticleCount(articleMapper.selectCount(new LambdaQueryWrapper<Article>().eq(Article::getCategoryId, category.getId())));
        })).toList();
    }

    @Override
    public ResponseResult<Void> addCategory(CategoryDTO categoryDTO) {
        categoryDTO.setId(null);
        Category category = categoryDTO.asViewObject(Category.class);
        if (this.save(category)) {
            // 同步 ES 索引
            rabbitTemplate.convertAndSend(ES_EXCHANGE, ES_SYNC_ROUTING_KEY,
                    EsSyncMessage.builder().entityType(EsSyncMessage.EntityType.CATEGORY).id(category.getId()).syncType(EsSyncMessage.SyncType.SAVE).build());
            return ResponseResult.success();
        }
        return ResponseResult.failure();
    }

    @Override
    public List<CategoryVO> searchCategory(SearchCategoryDTO searchCategoryDTO) {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.isNotEmpty(searchCategoryDTO.getCategoryName()), Category::getCategoryName, searchCategoryDTO.getCategoryName());
        if (StringUtils.isNotNull(searchCategoryDTO.getStartTime()) && StringUtils.isNotNull(searchCategoryDTO.getEndTime()))
            queryWrapper.between(Category::getCreateTime, searchCategoryDTO.getStartTime(), searchCategoryDTO.getEndTime());

        return categoryMapper.selectList(queryWrapper)
                .stream()
                .map(category ->
                        category.asViewObject(CategoryVO.class, item ->
                                item.setArticleCount(articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                                        .eq(Article::getCategoryId, category.getId())))))
                .toList();
    }

    @Override
    public CategoryVO getCategoryById(Long id) {
        return categoryMapper.selectById(id).asViewObject(CategoryVO.class);
    }

    @Transactional
    @Override
    public ResponseResult<Void> addOrUpdateCategory(CategoryDTO categoryDTO) {
        Category category = categoryDTO.asViewObject(Category.class);
        if (this.saveOrUpdate(category)) {
            // 同步 ES 索引
            rabbitTemplate.convertAndSend(ES_EXCHANGE, ES_SYNC_ROUTING_KEY,
                    EsSyncMessage.builder().entityType(EsSyncMessage.EntityType.CATEGORY).id(category.getId()).syncType(EsSyncMessage.SyncType.UPDATE).build());
            return ResponseResult.success();
        }
        return ResponseResult.failure();
    }

    @Transactional
    @Override
    public ResponseResult<Void> deleteCategoryByIds(List<Long> ids) {
        // 是否有剩下文章
        Long count = articleMapper.selectCount(new LambdaQueryWrapper<Article>().in(Article::getCategoryId, ids));
        if (count > 0) return ResponseResult.failure(FunctionConst.CATEGORY_EXIST_ARTICLE);
        // 执行删除
        if (this.removeByIds(ids)) {
            // 同步 ES 删除
            ids.forEach(id -> rabbitTemplate.convertAndSend(ES_EXCHANGE, ES_SYNC_ROUTING_KEY,
                    EsSyncMessage.builder().entityType(EsSyncMessage.EntityType.CATEGORY).id(id).syncType(EsSyncMessage.SyncType.DELETE).build()));
            return ResponseResult.success();
        }
        return ResponseResult.failure();
    }
}
