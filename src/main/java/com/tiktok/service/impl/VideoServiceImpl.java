package com.tiktok.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.otp.TOTP;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.config.LocalCache;
import com.tiktok.config.QiNiuConfig;
import com.tiktok.constant.AuditStatus;
import com.tiktok.constant.RedisConstant;
import com.tiktok.entity.File;
import com.tiktok.entity.task.VideoTask;
import com.tiktok.entity.user.User;
import com.tiktok.entity.user.UserHolder;
import com.tiktok.entity.video.Type;
import com.tiktok.entity.video.Video;
import com.tiktok.entity.video.VideoShare;
import com.tiktok.entity.video.VideoStar;
import com.tiktok.entity.vo.BasePage;
import com.tiktok.entity.vo.HotVideo;
import com.tiktok.entity.vo.UserModel;
import com.tiktok.entity.vo.UserVO;
import com.tiktok.exception.BaseException;
import com.tiktok.mapper.VideoMapper;
import com.tiktok.service.*;
import com.tiktok.util.RedisCacheUtil;
import org.springframework.cglib.core.Local;
import org.springframework.data.redis.connection.ReactiveListCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.index.PathBasedRedisIndexDefinition;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import ws.schild.jave.MultimediaInfo;
import ws.schild.jave.MultimediaObject;

import javax.annotation.Resource;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    @Resource
    private TypeService typeService;
    @Resource
    private InterestPushService interestPushService;
    @Resource
    private UserService userService;
    @Resource
    private VideoService videoService;
    @Resource
    private VideoStarService videoStarService;
    @Resource
    private VideoShareService videoShareService;
    @Resource
    private RedisCacheUtil redisCacheUtil;
    @Resource
    private FavoritesService favoritesService;
    @Resource
    private VideoPublishAuditServiceImpl videoPublishAuditService;
    @Resource
    private VideoMapper videoMapper;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private FollowService followService;
    @Resource
    private FeedService feedService;
    @Resource
    private FileService fileService;


    ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public Video getVideoById(Long id, Long userId) {
        Video video = this.getOne(new LambdaQueryWrapper<Video>().eq(Video::getId, id));
        if (video == null) throw new BaseException("指定视频不存在");

        // 私密则返回为空
        if (video.getOpen()) return new Video();
        setUserVoAndUrl(Collections.singleton(video));
        // 当前视频用户自己是否有收藏/点赞过等信息
        // 这里需要优化 如果这里开线程获取则系统g了(因为这里的场景不适合) -> 请求数很多
        // 正确做法: 视频存储在redis中，点赞收藏等行为异步放入DB, 定时任务扫描DB中不重要更新redis

        video.setStart(videoStarService.starState(id, userId));
        video.setFavorites(favoritesService.favoritesState(id, userId));
        video.setFollow(followService.isFollows(video.getUserId(), userId));
        return video;
    }

    @Override
    public void publishVideo(Video video) {

        Long userId = UserHolder.get();
        Long videoId = video.getId();
        Video oldVideo = null;
        if(videoId!=null){
            oldVideo = this.getOne(new LambdaQueryWrapper<Video>().eq(Video::getId,videoId).eq(Video::getUserId,userId));
            if(!(video.buildVideoUrl()).equals(oldVideo.buildVideoUrl())||
            !video.buildCoverUrl().equals(oldVideo.buildCoverUrl())){
                throw new BaseException("不能更换视频源");
            }
        }
        Type type = typeService.getById(video.getTypeId());
        if(type==null){
            throw new BaseException("分类不存在");
        }
        if(video.buildLabel().size()>5){
            throw new BaseException("标签最多选择5个");
        }

        video.setAuditStatus(AuditStatus.PROCESS);
        video.setUserId(userId);

        boolean isAdd = videoId == null ? true : false;
        if(!isAdd){
            video.setVideoType(null);
            video.setLabelNames(null);
            video.setUrl(null);
            video.setCover(null);
        }
        else{
            if(ObjectUtils.isEmpty(video.getCover())){
                video.setCover(fileService.generatePhoto(video.getUrl(),userId));
            }
            video.setYv("Yv"+UUID.randomUUID().toString().replace("-","").substring(8));
        }

        if(isAdd||!StringUtils.hasLength(oldVideo.getDuration())){
            String uuid = UUID.randomUUID().toString();
            LocalCache.put(uuid,true);
            try{
                Long url = video.getUrl();
                if(url==null||url==0){
                    url = oldVideo.getUrl();
                }
                String fileKey = fileService.getById(url).getFileKey();
                String videoUrl = QiNiuConfig.CNAME + "/" + fileKey + "?uuid=" + uuid;

                String duration = null;
                try {
                    URL source = new URL(videoUrl);
                    MultimediaObject multimediaObject = new MultimediaObject(source);
                    MultimediaInfo multimediaInfo = multimediaObject.getInfo();
                    long durationInSeconds = multimediaInfo.getDuration() / 1000;

                    int hours = (int) (durationInSeconds / 3600);
                    int minutes = (int) ((durationInSeconds % 3600) / 60);
                    int seconds = (int) (durationInSeconds % 60);

                    duration = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                } catch (Exception e) {
                    e.printStackTrace(); // 这里可以添加日志或其他异常处理
                }

                video.setDuration(duration);
            }

            finally {
                LocalCache.rem(uuid);
            }
            }
        this.saveOrUpdate(video);
        VideoTask videoTask = new VideoTask();
        videoTask.setVideo(video);
        videoTask.setOldVideo(video);
        videoTask.setIsAdd(isAdd);
        videoTask.setOldState(isAdd?true:video.getOpen());
        videoTask.setNewState(true);


    }

    @Override
    public void deleteVideo(Long id) {

        Long userId = UserHolder.get();
        Video video = this.getOne(new LambdaQueryWrapper<Video>().eq(Video::getId,id).eq(Video::getUserId,userId));
        if(video==null){
            throw new BaseException("不存在");
        }
        boolean b = removeById(id);
        if(b){
            new Thread(()->{
                videoShareService.remove(new LambdaQueryWrapper<VideoShare>().eq(VideoShare::getUserId,userId).eq(VideoShare::getVideoId,id));
                videoStarService.remove(new LambdaQueryWrapper<VideoStar>().eq(VideoStar::getUserId,userId).eq(VideoStar::getVideoId,id));
                interestPushService.deleteSystemStockIn(video);
                interestPushService.deleteSystemTypeStockIn(video);


            }).start();
        }
    }

    @Override
    public Collection<Video> pushVideos(Long userId) {
        User user = userService.getById(userId);
        Collection<Long> videoIds = interestPushService.listVideoIdByUserModel(user);
        ArrayList<Video> videos = new ArrayList<>();
        if(ObjectUtils.isEmpty(videoIds)){
            videoIds = list(new LambdaQueryWrapper<Video>().orderByDesc(Video::getGmtCreated)).stream().map(Video::getId).collect(Collectors.toList());
            videoIds = new HashSet<>(videoIds).stream().limit(10).collect(Collectors.toList());
        }
        videos = new ArrayList<>(listByIds(videoIds));
        setUserVoAndUrl(videos);
        return videos;
    }

    @Override
    public Collection<Video> getVideoByTypeId(Long typeId) {
        if(typeId==null)return Collections.EMPTY_LIST;
        Type type = typeService.getById(typeId);
        if(type==null)return Collections.EMPTY_LIST;
        Collection<Long> videoIdByTypeId = interestPushService.listVideoIdByTypeId(typeId);
        if(ObjectUtils.isEmpty(videoIdByTypeId)){
            return Collections.EMPTY_LIST;
        }
        List<Video> videos = listByIds(videoIdByTypeId);
        setUserVoAndUrl(videos);
        return videos;
    }

    @Override
    public IPage<Video> searchVideo(String search, BasePage basePage, Long userId) {
        IPage page = basePage.page();
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Video::getAuditStatus,AuditStatus.SUCCESS);
        if(search.contains("YV")){
            wrapper.eq(Video::getYv,search);
        }else{
            wrapper.like(!ObjectUtils.isEmpty(search),Video::getTitle,search);
        }
        IPage<Video> page1 = this.page(page,wrapper);
        List<Video> records = page1.getRecords();
        setUserVoAndUrl(records);
        userService.addSearchHistory(userId,search);
        return page1;
    }

    @Override
    public void auditProcess(Video video) {

        updateById(video);
        interestPushService.pushSystemStockIn(video);
        interestPushService.pushSystemTypeStockIn(video);
        feedService.pushInBoxFeed(video.getUserId(),video.getId(),video.getGmtCreated().getTime());

    }

    @Override
    public boolean startVideo(Long videoId) {
        Video video = getById(videoId);
        if(video==null)throw new BaseException("视频不存在");
        VideoStar videoStar = new VideoStar();
        videoStar.setVideoId(videoId);
        videoStar.setUserId(UserHolder.get());
        boolean starVideo = videoStarService.starVideo(videoStar);
        updateStar(video,starVideo?1L:-1L);
        List<String> label = video.buildLabel();
        UserModel userModel = UserModel.buildUserModel(label, videoId, 1.0);
        interestPushService.updateUserModel(userModel);
        return starVideo;
    }

    private void updateStar(Video video,Long value) {
        UpdateWrapper<Video> videoUpdateWrapper = new UpdateWrapper<>();
        videoUpdateWrapper.setSql("star_count=star_count+"+value);
        videoUpdateWrapper.lambda().eq(Video::getId, video.getId()).eq(Video::getStartCount, video.getStartCount());
        update(video,videoUpdateWrapper);
    }

    @Override
    public boolean shareVideo(VideoShare videoShare) {
        Video video = getById(videoShare.getVideoId());
        if(video==null)throw new BaseException("视频不存在");
        boolean share = videoShareService.share(videoShare);
        updateShare(video,share?1L:0L);
        return share;
    }
    private void updateShare(Video video,Long value) {
        UpdateWrapper<Video> videoUpdateWrapper = new UpdateWrapper<>();
        videoUpdateWrapper.setSql("share_count=share_count+"+value);
        videoUpdateWrapper.lambda().eq(Video::getId, video.getId()).eq(Video::getShareCount, video.getShareCount());
        update(video,videoUpdateWrapper);
    }

    @Override
    public void historyVideo(Long videoId, Long userId) throws Exception {
        String key = RedisConstant.HISTORY_VIDEO + videoId + ":" + userId;
        Object o = redisCacheUtil.get(key);
        if(o==null){
            redisCacheUtil.set(key,videoId,RedisConstant.HISTORY_TIME);
            Video video = getById(videoId);
            video.setUser(userService.getInfo(video.getUserId()));
            video.setTypeName(typeService.getById(video.getTypeId()).getName());
            redisCacheUtil.zadd(RedisConstant.USER_HISTORY_VIDEO+userId,new Date().getTime(),video,RedisConstant.HISTORY_TIME);
            updateHistory(video,1L);

        }

    }
    private void updateHistory(Video video,Long value) {
        UpdateWrapper<Video> videoUpdateWrapper = new UpdateWrapper<>();
        videoUpdateWrapper.setSql("history_count=history_count+"+value);
        videoUpdateWrapper.lambda().eq(Video::getId, video.getId()).eq(Video::getHistoryCount, video.getHistoryCount());
        update(video,videoUpdateWrapper);
    }

    @Override
    public boolean favoritesVideo(Long fId, Long vId) {
        Video video = getById(vId);
        if(video==null) throw new BaseException("视频不存在");
        boolean favorites = favoritesService.favorites(fId, vId);
        updateFavorites(video,favorites?1L:-1L);
        List<String> label = video.buildLabel();
        UserModel userModel = UserModel.buildUserModel(label, vId, 2.0);
        interestPushService.updateUserModel(userModel);
        return favorites;
    }
    private void updateFavorites(Video video,Long value) {
        UpdateWrapper<Video> videoUpdateWrapper = new UpdateWrapper<>();
        videoUpdateWrapper.setSql("favorites_count=favorites_count+"+value);
        videoUpdateWrapper.lambda().eq(Video::getId, video.getId()).eq(Video::getFavoritesCount, video.getFavoritesCount());
        update(video,videoUpdateWrapper);
    }

    @Override
    public LinkedHashMap<String, List<Video>> getHistory(BasePage basePage) {
        Long userId = UserHolder.get();
        String key = RedisConstant.USER_HISTORY_VIDEO + userId;
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisCacheUtil.zSetGetByPage(key, basePage.getPage(), basePage.getLimit());
        if(ObjectUtils.isEmpty(typedTuples)){
            return new LinkedHashMap<>();
        }
        ArrayList<Video> videos = new ArrayList<>();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        LinkedHashMap<String, List<Video>> result = new LinkedHashMap<>();
        for (ZSetOperations.TypedTuple<Object> typedTuple : typedTuples) {
            Date date = new Date(typedTuple.getScore().longValue());
            String format = simpleDateFormat.format(date);
            if(!result.containsKey(format)){
                result.put(format,new ArrayList<>());
            }
            Video video = (Video) typedTuple.getValue();
            result.get(format).add(video);
            videos.add(video);
        }
        setUserVoAndUrl(videos);

        return result;
    }

    @Override
    public Collection<Video> listVideoByFavorites(Long favoritesId) {
        Long userId = UserHolder.get();
        List<Long> videoIds = favoritesService.listVideoIds(favoritesId, userId);
        if(ObjectUtils.isEmpty(videoIds)){
            return Collections.EMPTY_LIST;
        }
        List<Video> videos = listByIds(videoIds);
        setUserVoAndUrl(videos);
        return videos;
    }

    @Override
    public Collection<HotVideo> hotRank() {

        Set<ZSetOperations.TypedTuple<Object>> scores = redisTemplate.opsForZSet().reverseRangeWithScores(RedisConstant.HOT_RANK, 0, -1);
        ArrayList<HotVideo> hotVideos = new ArrayList<>();
        for (ZSetOperations.TypedTuple<Object> score : scores) {
            HotVideo hotVideo;
            try{
                hotVideo = objectMapper.readValue(score.getValue().toString(),HotVideo.class);
                hotVideo.setHot((double) score.getScore().intValue());
                hotVideo.hotFormat();
                hotVideos.add(hotVideo);
            }catch (JsonProcessingException e){
                e.printStackTrace();
            }
        }

        return hotVideos;
    }

    @Override
    public Collection<Video> listSimilarVideo(Video video) {
        if (ObjectUtils.isEmpty(video) || ObjectUtils.isEmpty(video.getLabelNames())) return Collections.EMPTY_LIST;
        List<String> labels = video.buildLabel();
        ArrayList<String> labelNames = new ArrayList<>();
        labelNames.addAll(labels);
        labelNames.addAll(labels);
        Set<Long> videoIds = (Set<Long>) interestPushService.listVideoIdByLabels(labelNames);
        videoIds.remove(video.getId());

        // 初始化视频列表
        List<Video> videos = new ArrayList<>();

        if (!ObjectUtils.isEmpty(videoIds)) {
            videos = listByIds(videoIds);
            setUserVoAndUrl(videos);
        }

        return videos;
    }

    @Override
    public IPage<Video> listByUserIdOpenVideo(Long userId, BasePage basePage) {
        if(userId==null){
            return new Page<>();
        }
        IPage page = page(basePage.page(), new LambdaQueryWrapper<Video>()
                .eq(Video::getUserId, userId)
                .eq(Video::getAuditStatus, AuditStatus.SUCCESS)
                .orderByDesc(Video::getGmtCreated));

        List<Video> records = page.getRecords();
        setUserVoAndUrl(records);
        return page;
    }

    @Override
    public String getAuditQueueState() {
        return videoPublishAuditService.getAuditQueueState()?"quick":"slow";
    }

    @Override
    public List<Video> selectNDaysAgeVideo(long id, int days, int limit) {
        return videoMapper.selectNDaysAgeVideo(id,days,limit);
    }

    @Override
    public Collection<Video> listHotVideo() {
        Calendar instance = Calendar.getInstance();
        int today = instance.get(Calendar.DATE);
        HashMap<String, Integer> map = new HashMap<>();
        map.put(RedisConstant.HOT_VIDEO + today,10);
        map.put(RedisConstant.HOT_VIDEO + (today-1),3);
        map.put(RedisConstant.HOT_VIDEO + (today-2),2);
        List<Long> hotVideoIds = redisCacheUtil.pipeline(connection -> {
           map.forEach((k,v)->{
               connection.sRandMember(k.getBytes(),v);
           });
            return null;
        });

        if(ObjectUtils.isEmpty(hotVideoIds))return Collections.EMPTY_LIST;
        ArrayList<Long> videoIds = new ArrayList<>();
        for (Long hotVideoId : hotVideoIds) {
            if(!ObjectUtils.isEmpty(hotVideoId)){
                videoIds.addAll(hotVideoIds);
            }
        }
        List<Video> videos = listByIds(videoIds);
        setUserVoAndUrl(videos);
        return videos;

    }

    @Override
    public Collection<Video> followFeed(Long userId, Long lastTime) {
        Set<ZSetOperations.TypedTuple<Long>> set = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(
                        RedisConstant.IN_FOLLOW + userId,        // 键名
                        lastTime == null ? 0 : lastTime,         // 分数范围下限
                        lastTime == null ? new Date().getTime() : lastTime,  // 分数范围上限
                        0,                                      // 起始偏移量 (offset)
                        5                                       // 返回的最大元素个数 (count)
                );

        if(ObjectUtils.isEmpty(set))return Collections.EMPTY_LIST;

        Collection<Video> videos = list(new LambdaQueryWrapper<Video>().in(Video::getId, set)).stream()
                .sorted(Comparator.comparing(Video::getGmtCreated).reversed())
                .collect(Collectors.toList());

        setUserVoAndUrl(videos);

        return videos;
    }

    @Override
    public void initFollowFeed(Long userId) {

        Collection<Long> follow = followService.getFollow(userId, null);
        feedService.initFollowFeed(userId,follow);
    }

    @Override
    public IPage<Video> listByUserIdVideo(BasePage basePage, Long userId) {
        IPage page = page(basePage.page(), new LambdaQueryWrapper<Video>()
                .eq(Video::getUserId, userId).orderByDesc(Video::getGmtCreated));


        return page;
    }

    @Override
    public Collection<Long> listVideoIdByUserId(Long userId) {
        List<Long> ids = list(new LambdaQueryWrapper<Video>().eq(Video::getUserId,userId)
                .eq(Video::getOpen,0)
                .select(Video::getId)).stream().map(Video::getId).collect(Collectors.toList());
        return ids;
    }

    @Override
    public void violations(Long id) {

        Video video = getById(id);
        Type type = typeService.getById(video.getTypeId());
        video.setLabelNames(type.getLabelNames());
        // 修改视频信息
        video.setOpen(true);
        video.setMsg("该视频已下架");
        video.setAuditStatus(AuditStatus.PASS);
        // 删除分类中的视频
        interestPushService.deleteSystemTypeStockIn(video);
        // 删除标签中的视频
        interestPushService.deleteSystemStockIn(video);
        // 获取视频发布者id,删除对应的发件箱
        Long userId = video.getUserId();
        redisTemplate.opsForZSet().remove(RedisConstant.OUT_FOLLOW + userId, id);

        // 获取视频发布者粉丝，删除对应的收件箱
        Collection<Long> fansIds = followService.getFans(userId, null);
        feedService.deleteInBoxFeed(userId, Collections.singletonList(id));
        feedService.deleteOutBoxFeed(userId, fansIds, id);

        // 热门视频以及热度排行榜视频
        Calendar calendar = Calendar.getInstance();
        int today = calendar.get(Calendar.DATE);
        Long videoId = video.getId();
        // 尝试去找到删除
        redisTemplate.opsForSet().remove(RedisConstant.HOT_VIDEO + today, videoId);
        redisTemplate.opsForSet().remove(RedisConstant.HOT_VIDEO + (today - 1), videoId);
        redisTemplate.opsForSet().remove(RedisConstant.HOT_VIDEO + (today - 2), videoId);
        redisTemplate.opsForZSet().remove(RedisConstant.HOT_RANK, videoId);
        // 修改视频
        updateById(video);

    }

    public void setUserVoAndUrl(Collection<Video> videos) {
        if (!ObjectUtils.isEmpty(videos)) {
            Set<Long> userIds = new HashSet<>();
            ArrayList<Long> fileIds = new ArrayList<>();
            for (Video video : videos) {
                userIds.add(video.getUserId());
                fileIds.add(video.getUrl());
                fileIds.add(video.getCover());
            }
            Map<Long, File> fileMap = fileService.listByIds(fileIds).stream().collect(Collectors.toMap(File::getId, Function.identity()));
            Map<Long, User> userMap = userService.list(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
            for (Video video : videos) {
                UserVO userVO = new UserVO();
                User user = userMap.get(video.getUserId());
                userVO.setId(video.getUserId());
                userVO.setNickName(user.getNickName());
                userVO.setDescription(user.getDescription());
                userVO.setSex(user.getSex());
                video.setUser(userVO);
                File file = fileMap.get(video.getUrl());
                video.setVideoType(file.getFormat());
            }
        }
    }
}
