package com.tiktok.service.impl;

import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.tiktok.config.QiNiuConfig;
import com.tiktok.constant.AuditStatus;
import com.tiktok.entity.response.AuditResponse;
import com.tiktok.entity.task.VideoTask;
import com.tiktok.entity.video.Video;
import com.tiktok.mapper.VideoMapper;
import com.tiktok.service.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class VideoPublishAuditServiceImpl implements AuditService<VideoTask,VideoTask>, InitializingBean, BeanPostProcessor {

    @Resource
    private FeedService feedService;
    @Resource
    private VideoMapper videoMapper;
    @Resource
    private InterestPushService interestPushService;
    @Resource
    private QiNiuFileService qiNiuFileService;
    @Resource
    private TextAuditServiceImpl textAuditService;
    @Resource
    private ImageAuditServiceImpl imageAuditService;
    @Resource
    private VideoAuditServiceImpl videoAuditService;
    @Resource
    private FollowService followService;
    @Resource
    private FileService fileService;

    private int maxPoolSize = 8;

    protected ThreadPoolExecutor executor;

    public VideoTask audit(VideoTask videoTask,Boolean auditQueueState){
        if(auditQueueState){
            new Thread(()->{
                audit(videoTask);
            }).start();
        }else{
            audit(videoTask);
        }
        return null;
    }

    @Override
    public VideoTask audit(VideoTask videoTask) {
        executor.submit(()->{

            Video video = videoTask.getVideo();
            Video auditedVideo = new Video();
            BeanUtils.copyProperties(video, auditedVideo);

            boolean needAuditVideo = false;
            if (videoTask.getIsAdd() && videoTask.getOldState() == videoTask.getNewState()) {
                needAuditVideo = true;
            } else if (!videoTask.getIsAdd() && videoTask.getOldState() != videoTask.getNewState()) {
                if (!videoTask.getNewState()) {
                    needAuditVideo = true;
                }
            }

            AuditResponse videoAuditResponse = new AuditResponse(AuditStatus.SUCCESS, "正常");
            AuditResponse coverAuditResponse = new AuditResponse(AuditStatus.SUCCESS, "正常");
            AuditResponse titleAuditResponse = new AuditResponse(AuditStatus.SUCCESS, "正常");
            AuditResponse descAuditResponse = new AuditResponse(AuditStatus.SUCCESS, "正常");

            if (needAuditVideo) {
                videoAuditResponse = videoAuditService.audit(QiNiuConfig.CNAME + "/" + fileService.getById(video.getUrl()).getFileKey());
                coverAuditResponse = imageAuditService.audit(QiNiuConfig.CNAME + "/" + fileService.getById(video.getCover()).getFileKey());
                interestPushService.pushSystemTypeStockIn(auditedVideo);
                interestPushService.pushSystemStockIn(auditedVideo);

                feedService.pushOutBoxFeed(video.getUserId(), video.getId(), auditedVideo.getGmtCreated().getTime());
            } else if (videoTask.getNewState()) {
                interestPushService.deleteSystemStockIn(auditedVideo);
                interestPushService.deleteSystemTypeStockIn(auditedVideo);
                Collection<Long> fans = followService.getFans(video.getUserId(), null);
                feedService.deleteOutBoxFeed(video.getUserId(), fans, video.getId());
            }

            Video oldVideo = videoTask.getOldVideo();
            if (!video.getTitle().equals(oldVideo.getTitle())) {
                titleAuditResponse = textAuditService.audit(video.getTitle());
            }
            if (!video.getDescription().equals(oldVideo.getDescription()) && !ObjectUtils.isEmpty(video.getDescription())) {
                descAuditResponse = textAuditService.audit(video.getDescription());
            }

            Integer videoAuditStatus = videoAuditResponse.getAuditStatus();
            Integer coverAuditStatus = coverAuditResponse.getAuditStatus();
            Integer titleAuditStatus = titleAuditResponse.getAuditStatus();
            Integer descAuditStatus = descAuditResponse.getAuditStatus();
            boolean f1 = videoAuditStatus == AuditStatus.SUCCESS;
            boolean f2 = coverAuditStatus == AuditStatus.SUCCESS;
            boolean f3 = titleAuditStatus == AuditStatus.SUCCESS;
            boolean f4 = descAuditStatus == AuditStatus.SUCCESS;

            if (f1 && f2 && f3 && f4) {
                auditedVideo.setMsg("通过");
                auditedVideo.setAuditStatus(AuditStatus.SUCCESS);
            } else {
                auditedVideo.setAuditStatus(AuditStatus.PASS);
                auditedVideo.setMsg("");
                if (!f1) {
                    auditedVideo.setMsg("视频有违规行为: " + videoAuditResponse.getMsg());
                }
                if (!f2) {
                    auditedVideo.setMsg(auditedVideo.getMsg() + "\n封面有违规行为: " + coverAuditResponse.getMsg());
                }
                if (!f3) {
                    auditedVideo.setMsg(auditedVideo.getMsg() + "\n标题有违规行为: " + titleAuditResponse.getMsg());
                }
                if (!f4) {
                    auditedVideo.setMsg(auditedVideo.getMsg() + "\n简介有违规行为: " + descAuditResponse.getMsg());
                }
            }

            videoMapper.updateById(auditedVideo);
        });
        return null;
    }

    public boolean getAuditQueueState(){
        return executor.getTaskCount() < maxPoolSize;
    }
    @Override
    public void afterPropertiesSet() throws Exception {
        executor  = new ThreadPoolExecutor(5, maxPoolSize, 60, TimeUnit.SECONDS, new ArrayBlockingQueue(1000));
    }
}
