package com.tiktok.service.impl;

import com.qiniu.http.Client;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.util.StringMap;
import com.tiktok.constant.AuditStatus;
import com.tiktok.entity.Setting;
import com.tiktok.entity.json.BodyJson;
import com.tiktok.entity.json.ScoreJson;
import com.tiktok.entity.json.SettingScoreJson;
import com.tiktok.entity.response.AuditResponse;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class VideoAuditServiceImpl extends AuditServiceImpl<String, AuditResponse> {

    static String videoUrl = "http://ai.qiniuapi.com/v3/video/censor";
    static String videoBody = "{\n" +
            "    \"data\": {\n" +
            "        \"uri\": \"${url}\",\n" +
            "        \"id\": \"video_censor_test\"\n" +
            "    },\n" +
            "    \"params\": {\n" +
            "        \"scenes\": [\n" +
            "            \"pulp\",\n" +
            "            \"terror\",\n" +
            "            \"politician\"\n" +
            "        ],\n" +
            "        \"cut_param\": {\n" +
            "            \"interval_msecs\": 5000\n" +
            "        }\n" +
            "    }\n" +
            "}";


    @Override
    public AuditResponse audit(String url) {
        AuditResponse auditResponse = new AuditResponse();
        auditResponse.setAuditStatus(AuditStatus.SUCCESS);

        if (!isNeedAudit()) {
            return auditResponse;
        }
        url = appendUUID(url);

        String body = videoBody.replace("${url}", url);
        String method = "POST";
        // 获取token
        String token = qiNiuConfig.getToken(videoUrl, method, body, contentType);
        StringMap header = new StringMap();
        header.put("Host", "ai.qiniuapi.com");
        header.put("Authorization", token);
        header.put("Content-Type", contentType);
        Configuration cfg = new Configuration(Region.region2());
        Client client = new Client(cfg);
        try {
            Response response = client.post(videoUrl, body.getBytes(), header, contentType);
            Map map = objectMapper.readValue(response.getInfo().split(" \n")[2], Map.class);
            Object job = map.get("job");
            url = "http://ai.qiniuapi.com/v3/jobs/video/" + job.toString();
            method = "GET";
            header = new StringMap();
            header.put("Host", "ai.qiniuapi.com");
            header.put("Authorization", qiNiuConfig.getToken(url, method, null, null));
            while (true) {
                Response response1 = client.get(url, header);
                BodyJson bodyJson = objectMapper.readValue(response1.getInfo().split(" \n")[2], BodyJson.class);
                if (bodyJson.getStatus().equals("FINISHED")) {
                    // 1.从系统配置表获取 pulp politician terror比例
                    Setting setting = settingService.getById(1);
                    SettingScoreJson settingScoreRule = objectMapper.readValue(setting.getAuditPolicy(), SettingScoreJson.class);
                    List<ScoreJson> auditRule = Arrays.asList(settingScoreRule.getManualScore(), settingScoreRule.getPassScore(), settingScoreRule.getSuccessScore());
                    auditResponse = audit(auditRule, bodyJson);
                    return auditResponse;
                }
                Thread.sleep(2000L);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return auditResponse;
    }
}
