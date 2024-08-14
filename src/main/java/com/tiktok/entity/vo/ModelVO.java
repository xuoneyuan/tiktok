package com.tiktok.entity.vo;

import lombok.Data;

import java.util.List;

@Data
public class ModelVO {
    private Long userId;
    private List<String> labels;
}
