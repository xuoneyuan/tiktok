package com.tiktok.service.impl;

import com.qiniu.http.Client;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.util.StringMap;
import com.tiktok.config.QiNiuConfig;
import com.tiktok.constant.AuditStatus;
import com.tiktok.entity.Setting;
import com.tiktok.entity.json.*;
import com.tiktok.entity.response.AuditResponse;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class ImageAuditServiceImpl extends AuditServiceImpl<String, AuditResponse>  {
    static String imageUlr = "http://ai.qiniuapi.com/v3/image/censor";
    static String imageBody = "{\n" +
            "    \"data\": {\n" +
            "        \"uri\": \"${url}\"\n" +
            "    },\n" +
            "    \"params\": {\n" +
            "        \"scenes\": [\n" +
            "            \"pulp\",\n" +
            "            \"terror\",\n" +
            "            \"politician\"\n" +
            "        ]\n" +
            "    }\n" +
            "}";;


    @Override
    public AuditResponse audit(String url) {
        AuditResponse auditResponse = new AuditResponse();
        auditResponse.setAuditStatus(AuditStatus.SUCCESS);
        if (!isNeedAudit()) {
            return auditResponse;
        }
        try {
            if(!url.contains(QiNiuConfig.CNAME)) {
                String encodedFileName = URLEncoder.encode(url, "utf-8").replace("+", "%20");
                url = String.format("%s/%s", QiNiuConfig.CNAME, encodedFileName);
            }
            url = appendUUID(url);

            String body = imageBody.replace("${url}", url);
            String method = "POST";
            // 获取token
            String token = qiNiuConfig.getToken(imageUlr, method, body, contentType);
            StringMap header = new StringMap();
            header.put("Host", "ai.qiniuapi.com");
            header.put("Authorization", token);
            header.put("Content-Type", contentType);
            Configuration cfg = new Configuration(Region.region2());
            Client client = new Client(cfg);
            Response response = client.post(imageUlr, body.getBytes(), header, contentType);

            Map map = objectMapper.readValue(response.getInfo().split(" \n")[2], Map.class);
            ResultChildJson result = objectMapper.convertValue(map.get("result"), ResultChildJson.class);
            BodyJson bodyJson = new BodyJson();
            ResultJson resultJson = new ResultJson();
            resultJson.setResult(result);
            bodyJson.setResult(resultJson);

            Setting setting = settingService.getById(1);
            SettingScoreJson settingScoreRule = objectMapper.readValue(setting.getAuditPolicy(), SettingScoreJson.class);

            List<ScoreJson> auditRule = Arrays.asList(settingScoreRule.getManualScore(), settingScoreRule.getPassScore(), settingScoreRule.getSuccessScore());
            // 审核
            auditResponse = audit(auditRule, bodyJson);
            return auditResponse;
        } catch (Exception e) {
            auditResponse.setAuditStatus(AuditStatus.SUCCESS);
            e.printStackTrace();
        }
        return auditResponse;
    }
}
