package xyz.crucistau.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.crucistau.constants.FunctionConst;
import xyz.crucistau.domain.dto.EsSyncMessage;
import xyz.crucistau.domain.dto.SearchTagDTO;
import xyz.crucistau.domain.dto.TagDTO;
import xyz.crucistau.domain.entity.ArticleTag;
import xyz.crucistau.domain.entity.Tag;
import xyz.crucistau.domain.response.ResponseResult;
import xyz.crucistau.domain.vo.TagVO;
import xyz.crucistau.mapper.ArticleTagMapper;
import xyz.crucistau.mapper.TagMapper;
import xyz.crucistau.service.TagService;
import xyz.crucistau.utils.StringUtils;

import java.util.List;

/**
 * (Tag)表服务实现类
 *
 * 
 * @since 2023-10-15 02:29:14
 */
@Service("tagService")
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    @Resource
    private ArticleTagMapper articleTagMapper;

    @Resource
    private TagMapper tagMapper;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.exchange.es}")
    private String ES_EXCHANGE;

    @Value("${spring.rabbitmq.routingKey.es-sync}")
    private String ES_SYNC_ROUTING_KEY;

    @Override
    public List<TagVO> listAllTag() {
        return this.query().list().stream().map(tag -> tag.asViewObject(TagVO.class, item -> item.setArticleCount(articleTagMapper.selectCount(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, tag.getId()))))).toList();
    }

    @Override
    public ResponseResult<Void> addTag(TagDTO tagDTO) {
        Tag tag = tagDTO.asViewObject(Tag.class);
        if (this.save(tag)) {
            // 同步 ES 索引
            rabbitTemplate.convertAndSend(ES_EXCHANGE, ES_SYNC_ROUTING_KEY,
                    EsSyncMessage.builder().entityType(EsSyncMessage.EntityType.TAG).id(tag.getId()).syncType(EsSyncMessage.SyncType.SAVE).build());
            return ResponseResult.success();
        }
        return ResponseResult.failure();
    }

    @Override
    public List<TagVO> searchTag(SearchTagDTO searchTagDTO) {
        LambdaQueryWrapper<Tag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.isNotEmpty(searchTagDTO.getTagName()), Tag::getTagName, searchTagDTO.getTagName());
        if (StringUtils.isNotNull(searchTagDTO.getStartTime()) && StringUtils.isNotNull(searchTagDTO.getEndTime()))
            queryWrapper.between(Tag::getCreateTime, searchTagDTO.getStartTime(), searchTagDTO.getEndTime());

        return tagMapper.selectList(queryWrapper)
                .stream()
                .map(tag ->
                        tag.asViewObject(TagVO.class, item ->
                                item.setArticleCount(articleTagMapper.selectCount(new LambdaQueryWrapper<ArticleTag>()
                                        .eq(ArticleTag::getTagId, tag.getId())))))
                .toList();
    }

    @Override
    public TagVO getTagById(Long id) {
        return tagMapper.selectById(id).asViewObject(TagVO.class);
    }

    @Transactional
    @Override
    public ResponseResult<Void> addOrUpdateTag(TagDTO tagDTO) {
        Tag tag = tagDTO.asViewObject(Tag.class);
        if (this.saveOrUpdate(tag)) {
            // 同步 ES 索引（saveOrUpdate 后 tag.getId() 已自动填充）
            rabbitTemplate.convertAndSend(ES_EXCHANGE, ES_SYNC_ROUTING_KEY,
                    EsSyncMessage.builder().entityType(EsSyncMessage.EntityType.TAG).id(tag.getId()).syncType(EsSyncMessage.SyncType.UPDATE).build());
            return ResponseResult.success();
        }
        return ResponseResult.failure();
    }

    @Transactional
    @Override
    public ResponseResult<Void> deleteTagByIds(List<Long> ids) {
        // 是否有剩下文章
        Long count = articleTagMapper.selectCount(new LambdaQueryWrapper<ArticleTag>().in(ArticleTag::getTagId, ids));
        if (count > 0) return ResponseResult.failure(FunctionConst.TAG_EXIST_ARTICLE);
        // 执行删除
        if (this.removeByIds(ids)) {
            // 同步 ES 删除
            ids.forEach(id -> rabbitTemplate.convertAndSend(ES_EXCHANGE, ES_SYNC_ROUTING_KEY,
                    EsSyncMessage.builder().entityType(EsSyncMessage.EntityType.TAG).id(id).syncType(EsSyncMessage.SyncType.DELETE).build()));
            return ResponseResult.success();
        }
        return ResponseResult.failure();
    }
}
