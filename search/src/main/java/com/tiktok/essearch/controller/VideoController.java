package com.tiktok.essearch.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tiktok.entity.vo.VideoVO;
import com.tiktok.essearch.dto.Result;
import com.tiktok.essearch.dto.VideoDTO;
import com.tiktok.essearch.service.VideoService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;


/**
 * TikTok 视频控制层
 * @author
 */
@RestController
@RequestMapping("/video")
public class VideoController {

    @Resource
    VideoService videoService;

    /**
     * 搜索视频分页
     *
     * @param currentPage 当前页码
     * @param pageSize    每页大小
     * @param videoDTO    视频搜索DTO
     * @return 搜索结果分页
     */
    @PostMapping("searchVideoPageByDTO/{currentPage}/{pageSize}")
    public Result searchVideoPageByDTO(@PathVariable long currentPage, @PathVariable long pageSize, @RequestBody VideoDTO videoDTO) {
        Page<VideoVO> page = videoService.searchVideoPageByDTO(currentPage, pageSize, videoDTO);
        return Result.ok(page);
    }


    /**
     * 增加视频
     *
     * @param videoVO 视频实体
     */
    @PostMapping("addVideo")
    public void addVideo(@RequestBody VideoVO videoVO) {
        videoService.addVideo(videoVO);
    }

    /**
     * 修改视频
     *
     * @param videoVO 视频实体
     */
    @PostMapping("updateVideo")
    public void updateVideo(@RequestBody VideoVO videoVO) {
        videoService.updateVideo(videoVO);
    }



    /**
     * 删除视频
     *
     * @param videoId 视频ID
     */
    @RequestMapping("deleteVideo/{videoId}")
    public void deleteVideo(@PathVariable Long videoId) {
        videoService.deleteVideo(videoId);
    }
}
