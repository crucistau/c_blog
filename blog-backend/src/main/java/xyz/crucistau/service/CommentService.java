package xyz.crucistau.service;

import com.baomidou.mybatisplus.extension.service.IService;
import xyz.crucistau.domain.dto.CommentIsCheckDTO;
import xyz.crucistau.domain.dto.SearchCommentDTO;
import xyz.crucistau.domain.dto.UserCommentDTO;
import xyz.crucistau.domain.entity.Comment;
import xyz.crucistau.domain.response.ResponseResult;
import xyz.crucistau.domain.vo.ArticleCommentVO;
import xyz.crucistau.domain.vo.CommentListVO;
import xyz.crucistau.domain.vo.PageVO;

import java.util.List;


/**
 * (CommentEmail)表服务接口
 *
 *
 * @since 2023-10-19 15:44:57
 */
public interface CommentService extends IService<Comment> {
    /**
     * 查询文章评论
     */
    PageVO<List<ArticleCommentVO>> getComment(Integer type, Integer typeId, Integer pageNum, Integer pageSize);

    /**
     * 添加评论
     */
    ResponseResult<String> userComment(UserCommentDTO commentDTO);

    /**
     * 后台评论列表
     * @return 结果
     */
    List<CommentListVO> getBackCommentList(SearchCommentDTO searchDTO);

    /**
     * 是否通过评论
     * @param isCheckDTO 是否通过
     * @return 是否成功
     */
    ResponseResult<Void> isCheckComment(CommentIsCheckDTO isCheckDTO);

    /**
     * 删除评论
     * @param id id 列表
     * @return 是否成功
     */
    ResponseResult<Void> deleteComment(Long id);
}
