package xyz.crucistau.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import xyz.crucistau.annotation.AccessLimit;
import xyz.crucistau.annotation.LogAnnotation;
import xyz.crucistau.constants.LogConst;
import xyz.crucistau.domain.dto.RoleDTO;
import xyz.crucistau.domain.dto.RoleDeleteDTO;
import xyz.crucistau.domain.dto.RoleSearchDTO;
import xyz.crucistau.domain.dto.UpdateRoleStatusDTO;
import xyz.crucistau.domain.response.ResponseResult;
import xyz.crucistau.domain.vo.RoleAllVO;
import xyz.crucistau.domain.vo.RoleByIdVO;
import xyz.crucistau.service.RoleService;
import xyz.crucistau.utils.ControllerUtils;

import java.util.List;

/**
 * @author kuailemao
 * <p>
 * 创建时间：2023/11/28 9:20
 */
@RestController
@Tag(name = "角色相关接口")
@RequestMapping("role")
public class RoleController {

    /**
     * 服务对象
     */
    @Resource
    private RoleService roleService;

    @PreAuthorize("hasAnyAuthority('system:role:list')")
    @Operation(summary = "获取角色列表")
    @AccessLimit(seconds = 60, maxCount = 30)
    @GetMapping("/list")
    public ResponseResult<List<RoleAllVO>> selectAllRole() {
        return ControllerUtils.messageHandler(() -> roleService.selectRole(null));
    }
    @PreAuthorize("hasAnyAuthority('system:role:status:update')")
    @Operation(summary = "更新角色状态")
    @Parameter(name = "roleDeleteDTO", description = "修改角色状态")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module="角色管理",operation=LogConst.UPDATE)
    @PostMapping("/update/status")
    public ResponseResult<Void> updateStatus(@RequestBody @Valid UpdateRoleStatusDTO updateRoleStatusDTO) {
        return roleService.updateStatus(updateRoleStatusDTO.getId(),updateRoleStatusDTO.getStatus());
    }

    @PreAuthorize("hasAnyAuthority('system:role:get')")
    @Operation(summary = "获取修改角色信息")
    @Parameter(name = "id", description = "角色id")
    @AccessLimit(seconds = 60, maxCount = 30)
    @GetMapping("/get/{id}")
    public ResponseResult<RoleByIdVO> selectAll(@PathVariable("id") @NotNull Long id) {
        return roleService.selectRoleById(id);
    }

    @PreAuthorize("hasAnyAuthority('system:role:update')")
    @Operation(summary = "修改角色信息")
    @Parameter(name = "roleDTO", description = "修改角色所需数据")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module="角色管理",operation= LogConst.UPDATE)
    @PutMapping("/update")
    public ResponseResult<Void> updateRole(@RequestBody @Valid RoleDTO roleDTO) {
        return roleService.updateOrInsertRole(roleDTO);
    }

    @PreAuthorize("hasAnyAuthority('system:role:add')")
    @Operation(summary = "添加角色信息")
    @Parameter(name = "roleDTO", description = "添加角色所需数据")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module="角色管理",operation= LogConst.INSERT)
    @PutMapping("/add")
    public ResponseResult<Void> addRole(@RequestBody @Valid RoleDTO roleDTO) {
        return roleService.updateOrInsertRole(roleDTO.setId(null));
    }

    @PreAuthorize("hasAnyAuthority('system:role:delete')")
    @Operation(summary = "根据id删除角色")
    @Parameter(name = "roleDeleteDTO", description = "id数组")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module="角色管理",operation=LogConst.DELETE)
    @DeleteMapping("/delete")
    public ResponseResult<Void> deleteRole(@RequestBody @Valid RoleDeleteDTO roleDeleteDTO) {
        return roleService.deleteRole(roleDeleteDTO.getIds());
    }

    @PreAuthorize("hasAnyAuthority('system:role:search')")
    @Operation(summary = "根据条件搜索角色")
    @Parameter(name = "roleSearchDTO", description = "搜索条件")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module="角色管理",operation=LogConst.SEARCH)
    @PostMapping("/search")
    public ResponseResult<List<RoleAllVO>> searchRole(@RequestBody RoleSearchDTO roleSearchDTO) {
        return ControllerUtils.messageHandler(() -> roleService.selectRole(roleSearchDTO));
    }
}
