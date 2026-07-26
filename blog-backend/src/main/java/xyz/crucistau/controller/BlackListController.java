package xyz.crucistau.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import xyz.crucistau.annotation.AccessLimit;
import xyz.crucistau.annotation.LogAnnotation;
import xyz.crucistau.constants.LogConst;
import xyz.crucistau.domain.dto.AddBlackListDTO;
import xyz.crucistau.domain.dto.SearchBlackListDTO;
import xyz.crucistau.domain.dto.UpdateBlackListDTO;
import xyz.crucistau.domain.response.ResponseResult;
import xyz.crucistau.domain.vo.BlackListVO;
import xyz.crucistau.exceptions.BlackListException;
import xyz.crucistau.service.BlackListService;
import org.springframework.web.bind.annotation.*;
import xyz.crucistau.utils.ControllerUtils;

import java.util.List;

/**
 * (BlackList)表控制层
 *
 *
 * @since 2024-09-05 16:13:19
 */
@RestController
@RequestMapping("blackList")
public class BlackListController {
    /**
     * 服务对象
     */
    @Resource
    private BlackListService blackListService;

    /**
     * 增加黑名单
     */
    @PreAuthorize("hasAnyAuthority('blog:black:add')")
    @Operation(summary = "添加黑名单")
    @Parameter(name = "addBlackListDTO", description = "添加黑名单DTO")
    @LogAnnotation(module = "黑名单管理", operation = LogConst.INSERT)
    @AccessLimit(seconds = 60, maxCount = 30)
    @PostMapping("/add")
    public ResponseResult<Void> addBlackList(@RequestBody @Valid AddBlackListDTO addBlackListDTO) throws BlackListException {
        return blackListService.addBlackList(addBlackListDTO);
    }

    /**
     * 修改黑名单
     */
    @PreAuthorize("hasAnyAuthority('blog:black:update')")
    @Operation(summary = "修改黑名单")
    @Parameter(name = "updateBlackListDTO", description = "修改黑名单")
    @LogAnnotation(module = "黑名单管理", operation = LogConst.UPDATE)
    @AccessLimit(seconds = 60, maxCount = 30)
    @PutMapping("/update")
    public ResponseResult<Void> updateBlackList(@RequestBody @Valid UpdateBlackListDTO updateBlackListDTO) {
        return blackListService.updateBlackList(updateBlackListDTO);
    }

    /**
     * 查询黑名单
     */
    @PreAuthorize("hasAnyAuthority('blog:black:select')")
    @Operation(summary = "查询黑名单")
    @LogAnnotation(module = "黑名单管理", operation = LogConst.GET)
    @AccessLimit(seconds = 60, maxCount = 30)
    @PostMapping("/getBlackListing")
    public ResponseResult<List<BlackListVO>> getBlackList(@RequestBody(required = false) SearchBlackListDTO searchBlackListDTO) {
        return ControllerUtils.messageHandler(() -> blackListService.getBlackList(searchBlackListDTO));
    }

    /**
     * 删除黑名单
     */
    @PreAuthorize("hasAnyAuthority('blog:black:delete')")
    @Operation(summary = "删除黑名单")
    @Parameter(name = "id", description = "id")
    @LogAnnotation(module = "黑名单管理", operation = LogConst.DELETE)
    @AccessLimit(seconds = 60, maxCount = 30)
    @DeleteMapping("/delete")
    public ResponseResult<Void> deleteBlackList(@RequestBody List<Long> ids) {
        return blackListService.deleteBlackList(ids);
    }

}

