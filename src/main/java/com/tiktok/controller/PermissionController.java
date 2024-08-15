package com.tiktok.controller;

import com.tiktok.authority.Authority;
import com.tiktok.entity.user.Permission;
import com.tiktok.entity.user.UserHolder;
import com.tiktok.service.PermissionService;
import com.tiktok.util.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/authorize/permission")
public class PermissionController {

    @Resource
    private PermissionService permissionService;


    @GetMapping("/list")
    @Authority("permission:list")
    public List<Permission> list(){
        return permissionService.list();
    }

    @GetMapping("/treeSelect")
    @Authority("permission:treeSelect")
    public List<Permission> treeSelect(){
        List<Permission> data = permissionService.treeSelect();
        return data;
    }

    @PostMapping
    @Authority("permission:add")
    public R add(@RequestBody Permission permission){
        permission.setIcon("fa"+permission.getIcon());
        permissionService.save(permission);
        return R.ok();
    }


    @PutMapping
    @Authority("permission:update")
    public R update(@RequestBody Permission permission){
        permission.setIcon("fa"+permission.getIcon());
        permissionService.updateById(permission);
        return R.ok();
    }


    @DeleteMapping("/{id}")
    @Authority("permission:delete")
    public R delete(@PathVariable Long id){
        permissionService.removeMenu(id);
        return R.ok().message("删除成功");
    }

    @GetMapping("/initMenu")
    public Map<String,Object> initMenu(){
        Map<String,Object> data = permissionService.initMenu(UserHolder.get());
        return data;
    }
}
