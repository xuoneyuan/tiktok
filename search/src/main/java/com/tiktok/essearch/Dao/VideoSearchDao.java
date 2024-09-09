package com.tiktok.essearch.Dao;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tiktok.entity.video.Video;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface VideoSearchDao extends BaseMapper<Video> {

}