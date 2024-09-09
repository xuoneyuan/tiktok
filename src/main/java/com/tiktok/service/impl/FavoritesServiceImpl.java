package com.tiktok.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tiktok.entity.user.Favorites;
import com.tiktok.entity.user.FavoritesVideo;
import com.tiktok.entity.user.UserHolder;
import com.tiktok.exception.BaseException;
import com.tiktok.mapper.FavoritesMapper;
import com.tiktok.service.FavoritesService;
import com.tiktok.service.FavoritesVideoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FavoritesServiceImpl extends ServiceImpl<FavoritesMapper, Favorites> implements FavoritesService {

    @Resource
    private FavoritesVideoService favoritesVideoService;

    @Override
    @Transactional
    public void remove (Long id,Long userId){
        Favorites favorites = getOne(new LambdaQueryWrapper<Favorites>()
                .eq(Favorites::getId, id)
                .eq(Favorites::getUserId, userId));
        if(favorites.getName().equals("默认收藏夹")){
            throw new BaseException("不允许被删除");
        }
        boolean result = remove(new LambdaQueryWrapper<Favorites>()
                .eq(Favorites::getId, id)
                .eq(Favorites::getUserId, userId));
        if(result){
            favoritesVideoService.remove(new LambdaQueryWrapper<FavoritesVideo>()
                    .eq(FavoritesVideo::getFavoritesId,id));

        }else {
            throw new BaseException("错误");
        }

    }

    @Override
    public List<Favorites> listByUserId(Long userId){
        List<Favorites> favorites = list(new LambdaQueryWrapper<Favorites>()
                .eq(Favorites::getUserId, userId));
        if(ObjectUtils.isEmpty(favorites)){
            return Collections.EMPTY_LIST;
        }
        List<Long> collect = favorites.stream().map(Favorites::getId).collect(Collectors.toList());
        Map<Long,Long> map = favoritesVideoService.list(new LambdaQueryWrapper<FavoritesVideo>()
                        .in(FavoritesVideo::getFavoritesId, collect))
         .stream()
                .collect(Collectors.groupingBy(FavoritesVideo::getFavoritesId,
                Collectors.counting()));

        for (Favorites favorite : favorites) {
            Long count = map.get(favorite.getId());
            favorite.setVideoCount(count == null ? 0 : count);

        }
        return favorites;
    }

    @Override
    public List<Long> listVideoIds(Long favoritesId, Long userId){
        List<Favorites> favorites = list(new LambdaQueryWrapper<Favorites>()
                .eq(Favorites::getUserId, userId).eq(Favorites::getId,favoritesId));
        if(favorites==null){
            throw new BaseException("收藏夹为空");
        }
        List<Long> collect = favoritesVideoService.list(new LambdaQueryWrapper<FavoritesVideo>()
                        .eq(FavoritesVideo::getFavoritesId,favoritesId))
                .stream().map(FavoritesVideo::getVideoId).collect(Collectors.toList());

        return collect;
    }

    @Override
    public boolean favorites(Long fid,Long vid){
        Long userId = UserHolder.get();
        try{
            FavoritesVideo favoritesVideo = new FavoritesVideo();
            favoritesVideo.setFavoritesId(fid);
            favoritesVideo.setVideoId(vid);
            favoritesVideo.setUserId(userId);
            favoritesVideoService.save(favoritesVideo);
        }catch (Exception e){
            favoritesVideoService.remove(new LambdaQueryWrapper<FavoritesVideo>()
                    .eq(FavoritesVideo::getFavoritesId,fid)
                    .eq(FavoritesVideo::getVideoId,vid)
                    .eq(FavoritesVideo::getUserId,userId));
            return false;
        }
        return true;
    }

    @Override
    public boolean favoritesState(Long videoId, Long userId){
        if(userId==null){
            return false;
        }
        return favoritesVideoService.count(new LambdaQueryWrapper<FavoritesVideo>()
                .eq(FavoritesVideo::getVideoId,videoId)
                .eq(FavoritesVideo::getUserId,userId))==1;
    }

    @Override
    public void exist(Long userId,Long fid){
        long count = count(new LambdaQueryWrapper<Favorites>()
                .eq(Favorites::getUserId, userId)
                .eq(Favorites::getId, fid));
        if(count==0){
            throw new BaseException("收藏夹错误");
        }
    }
}
