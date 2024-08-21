package com.tiktok.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tiktok.config.LocalCache;
import com.tiktok.config.QiNiuConfig;
import com.tiktok.constant.AuditStatus;
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
import com.tiktok.entity.vo.UserVO;
import com.tiktok.exception.BaseException;
import com.tiktok.mapper.VideoMapper;
import com.tiktok.service.*;
import com.tiktok.util.RedisCacheUtil;
import org.springframework.cglib.core.Local;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.index.PathBasedRedisIndexDefinition;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import ws.schild.jave.MultimediaInfo;
import ws.schild.jave.MultimediaObject;

import javax.annotation.Resource;
import java.net.URL;
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
    private VideoMapper videoMapper;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private FollowService followService;
    @Resource
    private FeedService feedService;
    @Resource
    private FileService fileService;


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
        return null;
    }

    @Override
    public IPage<Video> searchVideo(String search, BasePage basePage, Long userId) {
        return null;
    }

    @Override
    public void auditProcess(Video video) {

    }

    @Override
    public boolean startVideo(Long videoId) {
        return false;
    }

    @Override
    public boolean shareVideo(VideoShare videoShare) {
        return false;
    }

    @Override
    public void historyVideo(Long videoId, Long userId) throws Exception {

    }

    @Override
    public boolean favoritesVideo(Long fId, Long vId) {
        return false;
    }

    @Override
    public LinkedHashMap<String, List<Video>> getHistory(BasePage basePage) {
        return null;
    }

    @Override
    public Collection<Video> listVideoByFavorites(Long favoritesId) {
        return null;
    }

    @Override
    public Collection<HotVideo> hotRank() {
        return null;
    }

    @Override
    public Collection<Video> listSimilarVideo(Video video) {
        return null;
    }

    @Override
    public IPage<Video> listByUserIdOpenVideo(Long userId, BasePage basePage) {
        return null;
    }

    @Override
    public String getAuditQueueState() {
        return null;
    }

    @Override
    public List<Video> selectNDaysAgeVideo(long id, int days, int limit) {
        return null;
    }

    @Override
    public Collection<Video> listHotVideo() {
        return null;
    }

    @Override
    public Collection<Video> followFeed(Long userId, Long lastTime) {
        return null;
    }

    @Override
    public void initFollowFeed(Long userId) {

    }

    @Override
    public IPage<Video> listByUserIdVideo(BasePage basePage, Long userId) {
        return null;
    }

    @Override
    public Collection<Long> listVideoIdByUserId(Long userId) {
        return null;
    }

    @Override
    public void violations(Long id) {

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
