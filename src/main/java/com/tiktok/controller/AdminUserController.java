package com.tiktok.controller;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tiktok.authority.Authority;
import com.tiktok.entity.user.Role;
import com.tiktok.entity.user.User;
import com.tiktok.entity.user.UserRole;
import com.tiktok.entity.vo.BasePage;
import com.tiktok.service.RoleService;
import com.tiktok.service.UserRoleService;
import com.tiktok.service.UserService;
import com.tiktok.util.R;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    @Resource
    private UserService userService;

    @Resource
    private UserRoleService userRoleService;

    @Resource
    private RoleService roleService;

    @GetMapping("/list")
    @Authority("admin:user:page")
    public R list(){
        return R.ok().data(userService.list(new LambdaQueryWrapper<>()));

    }

    @GetMapping("/page")
    @Authority("admin:user:page")
    public R list(BasePage basePage, @RequestParam(required = false) String name){
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(!ObjectUtils.isEmpty(name),User::getEmail,name);
        IPage<User> page = userService.page(basePage.page(), wrapper);
        // 查出用户角色中间表
        Map<Long, List<UserRole>> userRoleMap = userRoleService.list().stream().collect(Collectors.groupingBy(UserRole::getUserId));
        // 根据角色查出角色表信息
        Map<Long, String> roleMap = roleService.list().stream().collect(Collectors.toMap(Role::getId, Role::getName));
        Map<Long, Set<String>> map = new HashMap();
        userRoleMap.forEach((uId,rIds)->{
            Set<String> roles = new HashSet<>();
            for (UserRole rId : rIds) {
                roles.add(roleMap.get(rId.getRoleId()));
            }
            map.put(uId,roles);
        });
        for (User user : page.getRecords()) {
            user.setRoleName(map.get(user.getId()));
        }
        return R.ok().data(page.getRecords()).count(page.getTotal());
    }

}
