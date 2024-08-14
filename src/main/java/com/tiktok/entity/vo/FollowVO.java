package com.tiktok.entity.vo;

import com.tiktok.entity.user.Follow;
import lombok.Data;

@Data
public class FollowVO extends Follow {
    private String nickname;
}
