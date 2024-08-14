package com.tiktok.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.authority.Authority;
import com.tiktok.config.LocalCache;
import com.tiktok.entity.Setting;
import com.tiktok.entity.json.SettingScoreJson;
import com.tiktok.service.SettingService;
import com.tiktok.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/setting")
public class AdminSettingController {
    @Autowired
    private SettingService settingService;

    private ObjectMapper objectMapper;

    @GetMapping
    @Authority("admin:setting:get")
    public R get() throws JsonProcessingException {
        Setting setting = settingService.list().get(0);
        SettingScoreJson settingScoreJson = objectMapper.readValue(setting.getAuditPolicy(), SettingScoreJson.class);
        setting.setSettingScoreJson(settingScoreJson);
        return R.ok().data(setting);
    }

    @PutMapping
    @Authority("admin:setting:update")
    public R update(@RequestBody @Validated Setting setting){
        settingService.updateById(setting);
        for(String s : setting.getAllowIp().split(",")){
            LocalCache.put(s,true);
        }
        return R.ok().message("修改成功");
    }
}
