package com.tiktok.entity.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Objects;

@Data
@NoArgsConstructor
@ToString
public class HotVideo implements Serializable {
    private static final long serialVersionUID = 1L;
    String hotFormat;
    Double hot;
    Long videoId;
    String title;

    public HotVideo(Double hot, Long videoId, String title){
        this.hot = hot;
        this.videoId = videoId;
        this.title = title;
    }

    public void hotFormat(){
        BigDecimal bigDecimal = new BigDecimal(this.hot);
        BigDecimal decimal = bigDecimal.divide(new BigDecimal("10000"));
        DecimalFormat format = new DecimalFormat("0.0");
        format.setRoundingMode(RoundingMode.HALF_UP);
        String formatNum = format.format(decimal);
        this.setHotFormat(formatNum+"万");

    }

    @Override
    public boolean equals(Object object){
        if(this==object)return true;
        if(object==null||getClass()!=object.getClass())return false;
        HotVideo hotVideo = (HotVideo) object;
        return Objects.equals(videoId,hotVideo.videoId)&&Objects.equals(title,hotVideo.title);
    }

    @Override
    public int hashCode(){
        return Objects.hash(videoId,title);
    }
}
