package com.tiktok.controller;

import com.tiktok.entity.user.UserHolder;
import com.tiktok.entity.video.Video;
import com.tiktok.entity.vo.BasePage;
import com.tiktok.service.QiNiuFileService;
import com.tiktok.service.VideoService;
import com.tiktok.util.Limit;
import com.tiktok.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;

@RestController
@RequestMapping("/tiktok/video")
public class VideoController {

    @Autowired
    private VideoService videoService;

    @Autowired
    private QiNiuFileService fileService;

    /**
     * 获取文件上传token
     * @return
     */
    @GetMapping("/token")
    public R getToken(){
        return R.ok().data(fileService.getToken());
    }

    /**发布视频/修改视频
     * @param video
     * @return
     */
    @PostMapping
    @Limit(limit = 5,time = 3600L,msg = "发布视频一小时内不可超过5次")
    public R publishVideo(@RequestBody @Validated Video video){
        videoService.publishVideo(video);
        return R.ok().message("发布成功,请等待审核");
    }


    /**
     * 删除视频
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public R deleteVideo(@PathVariable Long id){
        videoService.deleteVideo(id);
        return R.ok().message("删除成功");
    }

    /**
     * 查看用户所管理的视频 -稿件管理
     * @param basePage
     * @return
     */
    @GetMapping
    public R listVideo(BasePage basePage){
        return R.ok().data(videoService.listByUserIdVideo(basePage, UserHolder.get()));
    }


    /**
     * 点赞视频
     */
    @PostMapping("/star/{id}")
    public R starVideo(@PathVariable Long id){
        String msg = "已点赞";
        if (!videoService.startVideo(id)) {
            msg = "取消点赞";
        }
        return R.ok().message(msg);
    }

    /**
     * 添加浏览记录
     * @return
     */
    @PostMapping("/history/{id}")
    public R addHistory(@PathVariable Long id) throws Exception {
        videoService.historyVideo(id, UserHolder.get());
        return R.ok();
    }

    /**
     * 获取用户的浏览记录
     * @return
     */
    @GetMapping("/history")
    public R getHistory(BasePage basePage){
        return R.ok().data(videoService.getHistory(basePage));
    }

    /**
     * 获取收藏夹下的视频
     */
    @GetMapping("/favorites/{favoritesId}")
    public R listVideoByFavorites(@PathVariable Long favoritesId){
        return R.ok().data(videoService.listVideoByFavorites(favoritesId));
    }

    /**
     * 收藏视频
     * @param fId
     * @param vId
     * @return
     */
    @PostMapping("/favorites/{fId}/{vId}")
    public R favoritesVideo(@PathVariable Long fId,@PathVariable Long vId){
        String msg = videoService.favoritesVideo(fId,vId) ? "已收藏" : "取消收藏";
        return R.ok().message(msg);
    }

    /**
     * 返回当前审核队列状态
     * @return
     */
    @GetMapping("/audit/queue/state")
    public R getAuditQueueState(){
        return R.ok().message(videoService.getAuditQueueState());
    }


    /**
     * 推送关注的人视频 拉模式
     * @param lastTime 滚动分页
     * @return
     */
    @GetMapping("/follow/feed")
    public R followFeed(@RequestParam(required = false) Long lastTime) throws ParseException {
        final Long userId = UserHolder.get();

        return R.ok().data(videoService.followFeed(userId,lastTime));
    }

    /**
     * 初始化收件箱
     * @return
     */
    @PostMapping("/init/follow/feed")
    public R initFollowFeed(){
        final Long userId = UserHolder.get();
        videoService.initFollowFeed(userId);
        return R.ok();
    }
}
