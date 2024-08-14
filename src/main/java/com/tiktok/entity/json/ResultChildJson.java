package com.tiktok.entity.json;

import lombok.Data;

import java.io.Serializable;

@Data
public class ResultChildJson implements Serializable {
    String suggestion;
    ScenesJson scenes;
}
