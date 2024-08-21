package com.tiktok.service.impl;

import cn.hutool.json.ObjectMapper;
import com.tiktok.constant.RedisConstant;
import com.tiktok.service.FeedService;
import com.tiktok.util.RedisCacheUtil;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class FeedServiceImpl implements FeedService {

    @Resource
    private RedisCacheUtil redisCacheUtil;
    @Resource
    private RedisTemplate redisTemplate;

    @Override
    @Async
    public void pushOutBoxFeed(Long userId, Long videoId, Long time) {
        redisCacheUtil.zadd(RedisConstant.OUT_FOLLOW + userId, time, videoId, -1);
    }

    @Override
    @Async
    public void pushInBoxFeed(Long userId, Long videoId, Long time) {
        redisCacheUtil.zadd(RedisConstant.IN_FOLLOW + userId, time, videoId, RedisConstant.HISTORY_TIME);
    }


    @Override
    @Async
    public void deleteOutBoxFeed(Long userId, Collection<Long> fans, Long videoId) {
        for (Long fan : fans) {
            redisTemplate.opsForZSet().remove(RedisConstant.IN_FOLLOW + fan, videoId);
        }

        redisTemplate.opsForZSet().remove(RedisConstant.OUT_FOLLOW + userId, videoId);
    }

    @Override
    @Async
    public void deleteInBoxFeed(Long userId, List<Long> videoIds) {
        redisTemplate.opsForZSet().remove(RedisConstant.IN_FOLLOW + userId, videoIds.toArray());
    }

    @Override
    @Async
    public void initFollowFeed(Long userId, Collection<Long> followIds) {
        Date date = new Date();
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate limitLocalDate = localDate.minusDays(7);
        Date limitDate = Date.from(limitLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        Set<ZSetOperations.TypedTuple<Long>> set = redisTemplate.opsForZSet().rangeWithScores(RedisConstant.IN_FOLLOW + userId, -1, -1);
        if (!ObjectUtils.isEmpty(set)) {
            Double oldTime = set.iterator().next().getScore();
            init(userId, oldTime.longValue(), new Date().getTime(), followIds);
        } else {
            init(userId, limitDate.getTime(), date.getTime(), followIds);
        }
    }

    public void init(Long userId, Long min, Long max, Collection<Long> followIds) {

        List<Set<ZSetOperations.TypedTuple<Object>>> result = new ArrayList<>();

        for (Long followId : followIds) {
            Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                    .reverseRangeByScoreWithScores(RedisConstant.OUT_FOLLOW + followId, min, max, 0, 50);
            result.add(tuples);
        }
        HashSet<Long> ids = new HashSet<>();

        for (Set<ZSetOperations.TypedTuple<Object>> tuples : result) {
            if (!ObjectUtils.isEmpty(tuples)) {
                for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                    Object value = tuple.getValue();
                    ids.add(Long.parseLong(value.toString()));
                    String key = RedisConstant.IN_FOLLOW + userId;
                    try {
                        // 使用 Java 序列化机制
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(bos);
                        oos.writeObject(value);
                        oos.flush();
                        byte[] valueBytes = bos.toByteArray();

                        redisTemplate.opsForZSet().add(key, valueBytes, tuple.getScore());
                        redisTemplate.expire(key, RedisConstant.HISTORY_TIME, TimeUnit.SECONDS);

                        oos.close();
                        bos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
