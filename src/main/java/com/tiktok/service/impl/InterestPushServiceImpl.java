package com.tiktok.service.impl;

import cn.hutool.core.lang.hash.Hash;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import com.tiktok.constant.RedisConstant;
import com.tiktok.entity.user.User;
import com.tiktok.entity.video.Video;
import com.tiktok.entity.vo.HotVideo;
import com.tiktok.entity.vo.Model;
import com.tiktok.entity.vo.UserModel;
import com.tiktok.service.InterestPushService;
import com.tiktok.service.TypeService;
import com.tiktok.util.RedisCacheUtil;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InterestPushServiceImpl implements InterestPushService {

    @Resource
    private RedisCacheUtil redisCacheUtil;

    @Resource
    private TypeService typeService;

    @Resource
    private RedisTemplate redisTemplate;

    @Override
    public void pushSystemTypeStockIn(Video video) {
        Long typeId = video.getTypeId();
        redisCacheUtil.sSet(RedisConstant.SYSTEM_TYPE_STOCK+typeId,video.getId());

    }


    @Override
    public void pushSystemStockIn(Video video) {
        List<String> labels = video.buildLabel();
        Long videoId = video.getId();

        for (String label : labels) {
            redisTemplate.opsForSet().add(RedisConstant.SYSTEM_STOCK + label, String.valueOf(videoId));
        }

    }

    @Override
    public Collection<Long> listVideoIdByTypeId(Long typeId) {
        List<Object> list = redisTemplate.opsForSet().randomMembers(RedisConstant.SYSTEM_TYPE_STOCK + typeId, 12);
        HashSet<Long> set = new HashSet<>();
        for (Object o : list) {
            if(o!=null){
                set.add(Long.parseLong(o.toString()));
            }
        }

        return set;
    }

    @Override
    public void deleteSystemStockIn(Video video) {

        List<String> labels = video.buildLabel();
        Long id = video.getId();
        for (String label : labels) {
            String key = RedisConstant.SYSTEM_STOCK + label;
            BoundSetOperations<String, Object> boundSetOps = redisTemplate.boundSetOps(key);
            boundSetOps.remove(id.toString());
        }

    }

    @Override
    public void initUserModel(Long userId, List<String> labels) {


        String key = RedisConstant.USER_MODEL + userId;
        HashMap<Object, Object> map = new HashMap<>();
        if(!ObjectUtils.isEmpty(labels)){
            int size = labels.size();
            double  proValue = 100 / size;
            for (String label : labels) {
                map.put(label,proValue);
            }
        }
        redisCacheUtil.del(key);
        redisCacheUtil.hmset(key,map);
    }

    @Override
    public void updateUserModel(UserModel userModel) {

        Long userId = userModel.getUserId();
        if(userId!=null){
            List<Model> models = userModel.getModels();
            String key = RedisConstant.USER_MODEL+userId;
            Map<Object, Object> modelMap = redisCacheUtil.hmget(key);
            if(modelMap==null){
                modelMap = new HashMap<>();
            }
            for (Model model : models) {
                if(modelMap.containsKey(model.getLabel())){

                    modelMap.put(model.getLabel(), Double.parseDouble(modelMap.get(model.getLabel()).toString()) + model.getScore());
                    Object o = modelMap.get(model.getLabel());
                    if(o==null||Double.parseDouble(o.toString())>0.0){
                        modelMap.remove(o);
                    }
                }else {
                    modelMap.put(model.getLabel(),model.getScore());
                }
            }
            int size = modelMap.keySet().size();
            for (Object o : modelMap.keySet()) {
                modelMap.put(o,(Double.parseDouble(modelMap.get(o).toString())+size)/size);
            }
            redisCacheUtil.hmset(key,modelMap);
        }
    }

    @Override
    public Collection<Long> listVideoIdByUserModel(User user) {
        // 创建结果集
        Set<Long> videoIds = new HashSet<>(10);

        if (user != null) {
            Long userId = user.getId();
            // 从模型中拿概率
            Map<Object, Object> modelMap = redisCacheUtil.hmget(RedisConstant.USER_MODEL + userId);
            if (!ObjectUtils.isEmpty(modelMap)) {
                // 组成数组
                String[] probabilityArray = initProbabilityArray(modelMap);
                Boolean sex = user.getSex();
                // 获取视频
                Random randomObject = new Random();
                ArrayList<String> labelNames = new ArrayList<>();
                // 随机获取X个视频
                for (int i = 0; i < 8; i++) {
                    String labelName = probabilityArray[randomObject.nextInt(probabilityArray.length)];
                    labelNames.add(labelName);
                }

                // 随机获取
                List<Object> list = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    for (String labelName : labelNames) {
                        String key = RedisConstant.SYSTEM_STOCK + labelName;
                        connection.sRandMember(key.getBytes());
                    }
                    return null;
                });
                // 获取到的videoIds
                Set<Long> ids = list.stream().filter(id->id!=null).map(id->Long.parseLong(id.toString())).collect(Collectors.toSet());
                String key2 = RedisConstant.HISTORY_VIDEO;

                // 去重
                List simpIds = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    for (Long id : ids) {
                        connection.get((key2 + id + ":" + userId).getBytes());
                    }
                    return null;
                });
                simpIds = (List) simpIds.stream().filter(o->!ObjectUtils.isEmpty(o)).collect(Collectors.toList());;
                if (!ObjectUtils.isEmpty(simpIds)){
                    for (Object simpId : simpIds) {
                        Long l = Long.valueOf(simpId.toString());
                        if (ids.contains(l)){
                            ids.remove(l);
                        }
                    }
                }


                videoIds.addAll(ids);

                // 随机挑选一个视频,根据性别: 男：美女 女：宠物
                Long randomVideo = randomVideoId(sex);
                if (randomVideo!=null){
                    videoIds.add(randomVideo);
                }


                return videoIds;
            }
        }
        // 游客
        // 随机获取10个标签
        List<String> labels = typeService.random10Labels();
        ArrayList<String> labelNames = new ArrayList<>();
        int size = labels.size();
        Random random = new Random();
        // 获取随机的标签
        for (int i = 0; i < 10; i++) {
            int randomIndex = random.nextInt(size);
            labelNames.add(RedisConstant.SYSTEM_STOCK + labels.get(randomIndex));
        }
        // 获取videoId
        List<Object> list = redisCacheUtil.sRandom(labelNames);
        if (!ObjectUtils.isEmpty(list)){
            videoIds = list.stream().filter(id ->!ObjectUtils.isEmpty(id)).map(id -> Long.valueOf(id.toString())).collect(Collectors.toSet());
        }

        return videoIds;
    }

    @Override
    public Collection<Long> listVideoIdByLabels(List<String> labelNames) {
        ArrayList<String> labelKeys = new ArrayList<>();
        for (String labelName : labelNames) {
            labelKeys.add(RedisConstant.SYSTEM_STOCK+labelName);
        }
        Set<Long> videoIds = new HashSet<>();
        List<Object> list = redisCacheUtil.sRandom(labelKeys);
        if(!ObjectUtils.isEmpty(list)){
            videoIds = list.stream().filter(id->!ObjectUtils.isEmpty(id)).map(id->Long.valueOf(id.toString())).collect(Collectors.toSet());
        }
        return videoIds;
    }

    @Override
    public void deleteSystemTypeStockIn(Video video) {

        Long typeId = video.getTypeId();
        redisCacheUtil.setRemove(RedisConstant.SYSTEM_TYPE_STOCK+typeId,video.getId());
    }

    public Long randomHotVideoId(){
        Object o = redisTemplate.opsForZSet().randomMember(RedisConstant.HOT_RANK);
        try {
            return JSONUtil.toBean(o.toString(), HotVideo.class).getVideoId();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Long randomVideoId(Boolean sex) {
        String key = RedisConstant.SYSTEM_STOCK + (sex ? "美女" : "宠物");
        Object o = redisCacheUtil.sRandom(key);
        if (o!=null){
            return Long.parseLong(o.toString());
        }
        return null;
    }

    // 随机获取视频id
    public Long getVideoId(Random random, String[] probabilityArray) {
        String labelName = probabilityArray[random.nextInt(probabilityArray.length)];
        // 获取对应所有视频
        String key = RedisConstant.SYSTEM_STOCK + labelName;
        Object o = redisCacheUtil.sRandom(key);
        if (o!=null){
            return Long.parseLong(o.toString()) ;
        }
        return null;
    }

    // 初始化概率数组 -> 保存的元素是标签
    public String[] initProbabilityArray(Map<Object, Object> modelMap) {
        // key: 标签  value：概率
        Map<String, Integer> probabilityMap = new HashMap<>();
        int size = modelMap.size();
        AtomicInteger n = new AtomicInteger(0);
        modelMap.forEach((k, v) -> {
            // 防止结果为0,每个同等加上标签数
            int probability = (((Double) v).intValue() + size) / size;
            n.getAndAdd(probability);
            probabilityMap.put(k.toString(), probability);
        });
        String[] probabilityArray = new String[n.get()];

        AtomicInteger index = new AtomicInteger(0);
        // 初始化数组
        probabilityMap.forEach((labelsId, p) -> {
            int i = index.get();
            int limit = i + p;
            while (i < limit) {
                probabilityArray[i++] = labelsId;
            }
            index.set(limit);
        });
        return probabilityArray;
    }

}
