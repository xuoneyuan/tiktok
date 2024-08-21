package com.tiktok.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tiktok.entity.video.Video;
import com.tiktok.entity.video.VideoStar;
import com.tiktok.mapper.VideoStarMapper;
import com.tiktok.service.VideoStarService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class VideoStarServiceImpl extends ServiceImpl<VideoStarMapper,VideoStar> implements VideoStarService {
    @Override
    public boolean starVideo(VideoStar videoStar) {
        try{
            this.save(videoStar);
        }
        catch (Exception e){

            this.remove(new LambdaQueryWrapper<VideoStar>().eq(VideoStar::getVideoId,videoStar.getVideoId()).eq(VideoStar::getUserId,videoStar.getUserId()));
            return false;
        }
        return true;
    }

    @Override
    public List<Long> getStarUserIds(Long videoId) {
        return this.list(new LambdaQueryWrapper<VideoStar>().eq(VideoStar::getVideoId,videoId)).stream().map(VideoStar::getUserId).collect(Collectors.toList());
    }

    @Override
    public Boolean starState(Long videoId, Long userId) {
        if(userId==null)return false;
        return this.count(new LambdaQueryWrapper<VideoStar>().eq(VideoStar::getVideoId,videoId).eq(VideoStar::getUserId,userId))==1;

    }
}
