package com.tiktok.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tiktok.authority.Authority;
import com.tiktok.entity.video.Type;
import com.tiktok.entity.vo.BasePage;
import com.tiktok.service.TypeService;
import com.tiktok.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

public class AdminTypeController {

    @Autowired
    private TypeService typeService;

    @GetMapping
    @Authority("admin:type:get")
    public R get(@PathVariable Long id){
        return R.ok().data(typeService.getById(id));
    }

    @GetMapping("/page")
    @Authority("admin:type:page")
    public R page(BasePage basePage){
        IPage page = typeService.page(basePage.page(), null);
        return R.ok().data(page.getRecords()).count(page.getTotal());
    }


    @PostMapping
    @Authority("admin:type:page")
    public R add(@RequestBody @Validated Type type){
        Long count = typeService.count(new LambdaQueryWrapper<Type>().eq(Type::getName,type.getName()).ne(Type::getId,type.getId()));
        if(count==1)return R.error().message("分类已存在");
        typeService.save(type);
        return R.ok().message("添加成功");
    }

    @PutMapping
    @Authority("admin:type:page")
    public R update(@RequestBody @Validated Type type){
        Long count = typeService.count(new LambdaQueryWrapper<Type>().eq(Type::getName,type.getName()).ne(Type::getId,type.getId()));
        if(count==1)return R.error().message("分类已存在");
        typeService.updateById(type);
        return R.ok().message("添加成功");
    }

    @DeleteMapping("/{id}")
    @Authority("admin:type:delete")
    public R delete(@PathVariable Long id){
        typeService.removeById(id);
        return R.ok().message("删除成功");
    }
}
