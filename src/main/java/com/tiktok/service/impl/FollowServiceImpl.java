package com.tiktok.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ser.Serializers;
import com.tiktok.constant.RedisConstant;
import com.tiktok.entity.user.Follow;
import com.tiktok.entity.vo.BasePage;
import com.tiktok.exception.BaseException;
import com.tiktok.mapper.FollowMapper;
import com.tiktok.service.FeedService;
import com.tiktok.service.FollowService;
import com.tiktok.service.VideoService;
import com.tiktok.util.RedisCacheUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {

    @Resource
    private FeedService feedService;
    @Resource
    private VideoService videoService;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private RedisCacheUtil redisCacheUtil;

    @Override
    public int getFollowCount(Long userId) {
        return (int) count(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId));
    }
    @Override
    public int getFansCount(Long userId){
        return (int) count(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowId, userId));
    }


    @Override
    public Collection<Long> getFollow(Long userId, BasePage basePage){
        if(basePage==null){
            Set<Object> set = redisCacheUtil.zGet(RedisConstant.USER_FOLLOW + userId);
            if(ObjectUtils.isEmpty(set)){
                return Collections.EMPTY_SET;
            }
            return set.stream().map(o->Long.valueOf(o.toString())).collect(Collectors.toList());

        }
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisCacheUtil.zSetGetByPage(RedisConstant.USER_FOLLOW+userId,basePage.getPage(),basePage.getLimit());
        if(ObjectUtils.isEmpty(typedTuples)){
            List<Follow> records = page(basePage.page(), new LambdaQueryWrapper<Follow>().eq(Follow::getFollowId, userId)).getRecords();
            if(ObjectUtils.isEmpty(records)){
                return Collections.EMPTY_LIST;
            }
            return records.stream().map(Follow::getFollowId).collect(Collectors.toList());
        }
        return typedTuples.stream().map(t->Long.parseLong(t.getValue().toString())).collect(Collectors.toList());

    }

    @Override
    public Collection<Long> getFans(Long userId, BasePage basePage){
        if(basePage==null){
            Set<Object> set = redisCacheUtil.zGet(RedisConstant.USER_FANS + userId);
            if(ObjectUtils.isEmpty(set)){
                return Collections.EMPTY_SET;
            }
            return set.stream().map(o->Long.valueOf(o.toString())).collect(Collectors.toList());

        }
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisCacheUtil.zSetGetByPage(RedisConstant.USER_FANS+userId,basePage.getPage(),basePage.getLimit());
        if(ObjectUtils.isEmpty(typedTuples)){
            List<Follow> records = page(basePage.page(), new LambdaQueryWrapper<Follow>().eq(Follow::getFollowId, userId)).getRecords();
            if(ObjectUtils.isEmpty(records)){
                return Collections.EMPTY_LIST;
            }
            return records.stream().map(Follow::getUserId).collect(Collectors.toList());
        }
        return typedTuples.stream().map(t->Long.parseLong(t.getValue().toString())).collect(Collectors.toList());

    }


    @Override
    public Boolean follows(Long followsId, Long userId){
        if(followsId.equals(userId)){
            throw new BaseException("不能关注自己");
        }
        Follow follow = new Follow();
        follow.setFollowId(followsId);
        follow.setUserId(userId);
        try{
            save(follow);
            Date date = new Date();
            redisTemplate.opsForZSet().add(RedisConstant.USER_FOLLOW+userId,followsId,date.getTime());
            redisTemplate.opsForZSet().add(RedisConstant.USER_FANS+followsId,userId,date.getTime());
        }catch (Exception e){
            remove(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowId, followsId).eq(Follow::getUserId, userId));

            List<Long> videoIds = (List<Long>) videoService.listVideoIdByUserId(followsId);
            feedService.deleteInBoxFeed(userId, videoIds);
            // 自己关注列表删除
            redisTemplate.opsForZSet().remove(RedisConstant.USER_FOLLOW + userId, followsId);
            // 对方粉丝列表删除
            redisTemplate.opsForZSet().remove(RedisConstant.USER_FANS + followsId, userId);
            return false;
        }

        return true;
    }

    @Override
    public Boolean isFollows(Long followId, Long userId){
        if(userId==null||followId==null)return false;

        return count(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowId,followId).eq(Follow::getUserId,userId))==1;

    }

}
