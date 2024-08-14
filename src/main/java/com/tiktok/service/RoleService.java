package com.tiktok.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tiktok.entity.user.Role;
import com.tiktok.entity.user.Tree;
import com.tiktok.entity.vo.AssignRoleVO;
import com.tiktok.entity.vo.AuthorityVO;
import com.tiktok.util.R;

import java.util.List;

public interface RoleService extends IService<Role> {
    List<Tree> tree();

    R removeRole(String id);

    R gavePermission(AuthorityVO authorityVO);

    R gaveRole(AssignRoleVO assignRoleVO);

}
