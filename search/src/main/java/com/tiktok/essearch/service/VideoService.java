package com.tiktok.essearch.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tiktok.entity.video.Video;
import com.tiktok.entity.vo.VideoVO;
import com.tiktok.essearch.dto.VideoDTO;

public interface VideoService extends IService<Video> {


    Page<VideoVO> getVideoPageByDTO(long currentPage, long pageSize, VideoDTO videoDTO);

    Page<VideoVO> searchVideoPageByDTO(long currentPage, long pageSize, VideoDTO videoDTO);

    void addVideo(VideoVO videoVO);


    void updateVideo(VideoVO videoVO);

    void deleteVideo(Long videoId);
}
