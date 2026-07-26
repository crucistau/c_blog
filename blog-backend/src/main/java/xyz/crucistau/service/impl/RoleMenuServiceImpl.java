package xyz.crucistau.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import xyz.crucistau.domain.entity.RoleMenu;
import xyz.crucistau.mapper.RoleMenuMapper;
import xyz.crucistau.service.RoleMenuService;

/**
 * (RoleMenu)表服务实现类
 *
 * 
 * @since 2023-11-28 10:23:17
 */
@Service("roleMenuService")
public class RoleMenuServiceImpl extends ServiceImpl<RoleMenuMapper, RoleMenu> implements RoleMenuService {

}
