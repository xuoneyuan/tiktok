package com.tiktok.controller;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tiktok.authority.Authority;
import com.tiktok.constant.AuditStatus;
import com.tiktok.entity.user.User;
import com.tiktok.entity.video.Type;
import com.tiktok.entity.video.Video;
import com.tiktok.entity.vo.BasePage;
import com.tiktok.entity.vo.VideoStatistics;
import com.tiktok.service.TypeService;
import com.tiktok.service.UserService;
import com.tiktok.service.VideoService;
import com.tiktok.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/video")
public class AdminVideoController {

    @Autowired
    private VideoService videoService;
    @Autowired
    private UserService userService;
    @Autowired
    private TypeService typeService;

    @GetMapping("/{id}")
    @Authority("admin:video:get")
    public R get(@PathVariable Long id){
        return R.ok().data(videoService.getVideoById(id,null));
    }

    @GetMapping("/page")
    @Authority("admin:video:page")
    public R list(BasePage basePage, @RequestParam(required = false) String YV,
                  @RequestParam(required = false) String title){
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(!ObjectUtil.isEmpty(YV),Video::getYv,YV)
                .like(!ObjectUtil.isEmpty(title),Video::getTitle,title);

        IPage<Video> page = videoService.page(basePage.page());
        List<Video> records = page.getRecords();
        if(ObjectUtil.isEmpty(records))
            return R.ok();

        ArrayList<Long> userId = new ArrayList<>();
        ArrayList<Long> typeId = new ArrayList<>();
        for (Video record : records) {
            userId.add(record.getUserId());
            typeId.add(record.getTypeId());
        }

        Map<Long,String> userMap = userService.list(new LambdaQueryWrapper<User>()
                .select(User::getId,User::getNickName))
                .stream()
                .collect(Collectors.toMap(User::getId,User::getNickName));

        Map<Long,String> typeMap = typeService.listByIds(typeId).stream()
                .collect(Collectors.toMap(Type::getId,Type::getName));
        for (Video record : records) {
            record.setAuditStateName(AuditStatus.getName(record.getAuditStatus()));
            record.setUserName(userMap.get(record.getUserId()));
            record.setOpenName(record.getOpen()?"私密":"公开");
            record.setTypeName(typeMap.get(record.getTypeId()));

        }
        return R.ok().data(records).count(page.getTotal());

    }


    @DeleteMapping("/{id}")
    @Authority("admin:video:delete")
    public R delete(@PathVariable Long id){
        videoService.deleteVideo(id);
        return R.ok().message("删除成功");
    }

    @PostMapping("/audit")
    @Authority("admin:video:audit")
    public R audit(@RequestBody Video video){
        videoService.auditProcess(video);
        return R.ok().message("审核通过");
    }

    @PostMapping("/violations/{id}")
    @Authority("admin:video:violations")
    public R violations(@PathVariable Long id){
        videoService.violations(id);
        return R.ok().message("下架成功");
    }

    @GetMapping("/statistics")
    @Authority("admin:video:statistics")
    public R show(){
        VideoStatistics videoStatistics = new VideoStatistics();
        long allCount = videoService.count(new LambdaQueryWrapper<>());
        long processCount = videoService.count(new LambdaQueryWrapper<Video>().eq(Video::getAuditStatus,AuditStatus.PROCESS));
        long successCount = videoService.count(new LambdaQueryWrapper<Video>().eq(Video::getAuditStatus,AuditStatus.SUCCESS));
        long passCount = videoService.count(new LambdaQueryWrapper<Video>().eq(Video::getAuditStatus,AuditStatus.PASS));
        videoStatistics.setAllCount(allCount);
        videoStatistics.setPassCount(passCount);
        videoStatistics.setProcessCount(processCount);
        videoStatistics.setSuccessCount(successCount);
        return R.ok().data(videoStatistics);


    }




}
