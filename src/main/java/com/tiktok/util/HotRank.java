package com.tiktok.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tiktok.constant.AuditStatus;
import com.tiktok.constant.RedisConstant;
import com.tiktok.entity.Setting;
import com.tiktok.entity.video.Video;
import com.tiktok.entity.vo.HotVideo;
import com.tiktok.service.AuditService;
import com.tiktok.service.SettingService;
import com.tiktok.service.VideoService;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.sql.Array;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
public class HotRank {

    @Resource
    private VideoService videoService;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private SettingService settingService;


    Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);
    ObjectMapper om = new ObjectMapper();


    @Scheduled(cron = "0 0 */1 * * ?")
    public void hotRank(){
        TopK topK = new TopK(10, new PriorityQueue<HotVideo>(10, Comparator.comparing(HotVideo::getHot)));
        long limit = 1000;
        long id = 0;
        List<Video> videos = videoService.list(new LambdaQueryWrapper<Video>()
                .select(Video::getId,Video::getShareCount,Video::getHistoryCount,Video::getStartCount,Video::getFavoritesCount,Video::getGmtCreated,Video::getTitle)
                .gt(Video::getId,id)
                .eq(Video::getAuditStatus, AuditStatus.SUCCESS)
                .eq(Video::getOpen,0)
                .last("limit" + limit));

        while(!ObjectUtils.isEmpty(videos)){
            for (Video video : videos) {
                Long shareCount = video.getShareCount();
                Double historyCount = video.getHistoryCount() * 0.8;
                Long startCount = video.getStartCount();
                Double favoritesCount = video.getFavoritesCount() * 1.5;
                Date date = new Date();
                long t = date.getTime() - video.getGmtCreated().getTime();
                double a = 0.011;
                double hot = (shareCount + historyCount + startCount + favoritesCount) * Math.exp(-a * TimeUnit.MILLISECONDS.toDays(t));

                HotVideo hotVideo = new HotVideo(hot, video.getId(),video.getTitle());
                topK.add(hotVideo);

            }
            id = videos.get(videos.size()-1).getId();
            videos = videoService.list(new LambdaQueryWrapper<Video>()
                    .gt(Video::getId,id)
                    .last("limit"+limit));

        }

        byte[] bytes = RedisConstant.HOT_RANK.getBytes();
        List<HotVideo> hotVideos = topK.get();
        Double hot = hotVideos.get(0).getHot();
        redisTemplate.executePipelined((RedisCallback<Object>) connection ->{

            for (HotVideo hotVideo : hotVideos) {
                Double hot1 = hotVideo.getHot();
                try{
                    hotVideo.setHot(null);
                    connection.zAdd(bytes,hot1,jackson2JsonRedisSerializer.serialize(om.writeValueAsString(hotVideo)));

                }catch (JsonProcessingException e){
                    e.printStackTrace();
                }
            }
            return null;
        });

        redisTemplate.opsForZSet().removeRangeByScore(RedisConstant.HOT_RANK,hot,0);

    }

    @Scheduled(cron = "0 0 */3 * * ?")
    public void hotVideo(){
        int limit = 1000;
        long id = 1;
        List<Video> videos = videoService.selectNDaysAgeVideo(id,3,limit);
        Double hotLimit = settingService.list(new LambdaQueryWrapper<Setting>()).get(0).getHotLimit();
        Calendar instance = Calendar.getInstance();
        int today = instance.get(Calendar.DATE);
        while(!ObjectUtils.isEmpty(videos)){
            ArrayList<Long> hotVideos = new ArrayList<>();
            for (Video video : videos) {
                Long shareCount = video.getShareCount();
                Double historyCount = video.getHistoryCount() * 0.8;
                Long startCount = video.getStartCount();
                Double favoritesCount = video.getFavoritesCount() * 1.5;
                Date date = new Date();
                long t = date.getTime() - video.getGmtCreated().getTime();
                double a = 0.011;
                double hot = (shareCount + historyCount + startCount + favoritesCount) * Math.exp(-a * TimeUnit.MILLISECONDS.toDays(t));
                if(hot>hotLimit){
                    hotVideos.add(video.getId());
                }
            }
            id = videos.get(videos.size()-1).getId();
            videos = videoService.selectNDaysAgeVideo(id,3,limit);
            if(!ObjectUtils.isEmpty(hotVideos)){

                String key = RedisConstant.HOT_VIDEO + today;
                redisTemplate.opsForSet().add(key,hotVideos.toArray(new Object[hotVideos.size()]));
                redisTemplate.expire(key,3,TimeUnit.DAYS);
            }


        }

    }
}
