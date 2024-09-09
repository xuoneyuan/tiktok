package com.tiktok.essearch.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tiktok.entity.video.Video;
import com.tiktok.entity.vo.VideoVO;
import com.tiktok.essearch.Dao.VideoSearchDao;
import com.tiktok.essearch.dto.VideoDTO;
import com.tiktok.essearch.service.VideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Service
@Slf4j
public abstract class VideoServiceImpl extends ServiceImpl<VideoSearchDao, Video> implements VideoService {

    @Resource
    ElasticsearchClient elasticsearchClient;

    @Override
    public Page<VideoVO> searchVideoPageByDTO(long currentPage, long pageSize, VideoDTO videoDTO) {
        Page<VideoVO> page = new Page<>();
        List<VideoVO> videoVOList = new ArrayList<>();
        try {
            // 创建搜索请求构建器
            SearchRequest.Builder builder = new SearchRequest.Builder().index("tiktok_video_index");

            // 关键字查询
            if (StringUtils.isNotBlank(videoDTO.getKeyword())) {
                builder.query(q -> q.bool(b -> b
                        .should(h -> h.match(f -> f.field("title").query(v -> v.stringValue(videoDTO.getKeyword()))))
                        .should(h -> h.match(f -> f.field("description").query(v -> v.stringValue(videoDTO.getKeyword()))))
                        .should(h -> h.match(f -> f.field("labelNames").query(v -> v.stringValue(videoDTO.getKeyword()))))
                ));
            }


            // 按点赞数或时间排序
            if (videoDTO.getSortType() == 1) {
                builder.sort(o -> o.field(f -> f.field("startCount").order(SortOrder.Desc)));
            } else if (videoDTO.getSortType() == 2) {
                builder.sort(o -> o.field(f -> f.field("gmtCreated").order(SortOrder.Desc)));
            }

            // 分页设置
            builder.from((int) (currentPage - 1) * (int) pageSize);
            builder.size((int) pageSize);

            // 执行搜索请求
            SearchRequest searchRequest = builder.build();
            SearchResponse<VideoVO> searchResponse = elasticsearchClient.search(searchRequest, VideoVO.class);
            TotalHits totalHits = searchResponse.hits().total();
            page.setTotal(totalHits.value());

            List<Hit<VideoVO>> hits = searchResponse.hits().hits();
            for (Hit<VideoVO> hit : hits) {
                VideoVO videoVO = hit.source();
                videoVOList.add(videoVO);
            }
        } catch (Exception e) {
            throw new RuntimeException("Elasticsearch 查询异常", e);
        }
        page.setRecords(videoVOList);
        return page;
    }

    @Override
    public void addVideo(VideoVO videoVO) {
        try {
            CreateResponse createResponse = elasticsearchClient.create(e -> e.index("tiktok_video_index").id(videoVO.getId().toString()).document(videoVO));
            log.info("创建视频记录: {}", createResponse.result());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateVideo(VideoVO videoVO) {
        try {
            UpdateResponse<VideoVO> updateResponse = elasticsearchClient.update(e -> e.index("tiktok_video_index").id(videoVO.getId().toString()).doc(videoVO), VideoVO.class);
            log.info("更新视频记录: {}", updateResponse.result());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteVideo(Long videoId) {
        try {
            DeleteResponse deleteResponse = elasticsearchClient.delete(e -> e.index("tiktok_video_index").id(videoId.toString()));
            log.info("删除视频记录: {}", deleteResponse.result());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}