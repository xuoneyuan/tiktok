package com.tiktok.essearch.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class VideoDTO implements Serializable {
    private String keyword; // 搜索关键词
    private Long typeId; // 视频分类ID
    private Integer sortType; // 排序类型，1：按点赞数，2：按发布时间
}
