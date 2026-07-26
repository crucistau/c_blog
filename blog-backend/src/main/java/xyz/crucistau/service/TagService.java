package xyz.crucistau.service;

import com.baomidou.mybatisplus.extension.service.IService;
import xyz.crucistau.domain.dto.SearchTagDTO;
import xyz.crucistau.domain.dto.TagDTO;
import xyz.crucistau.domain.entity.Tag;
import xyz.crucistau.domain.response.ResponseResult;
import xyz.crucistau.domain.vo.TagVO;

import java.util.List;


/**
 * (Tag)表服务接口
 *
 * 
 * @since 2023-10-15 02:29:14
 */
public interface TagService extends IService<Tag> {
    /**
     * 查询所有标签
     * @return 标签列表
     */
    List<TagVO> listAllTag();

    /**
     * 添加标签
     * @param tagDTO 标签DTO
     * @return 是否成功
     */
    ResponseResult<Void> addTag(TagDTO tagDTO);

    /**
     * 搜索标签
     * @param searchTagDTO 搜索标签DTO
     * @return 标签列表
     */
    List<TagVO> searchTag(SearchTagDTO searchTagDTO);

    /**
     * 根据id查询
     * @param id id
     * @return 标签
     */
    TagVO getTagById(Long id);

    /**
     * 新增或修改标签
     * @param tagDTO 标签DTO
     * @return 是否成功
     */
    ResponseResult<Void> addOrUpdateTag(TagDTO tagDTO);

    /**
     * 根据id删除
     * @param ids id
     * @return 是否成功
     */
    ResponseResult<Void> deleteTagByIds(List<Long> ids);
}
