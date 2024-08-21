package com.tiktok.service.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.config.LocalCache;
import com.tiktok.config.QiNiuConfig;
import com.tiktok.constant.AuditMsgMap;
import com.tiktok.constant.AuditStatus;
import com.tiktok.entity.Setting;
import com.tiktok.entity.json.*;
import com.tiktok.entity.response.AuditResponse;
import com.tiktok.service.AuditService;
import com.tiktok.service.SettingService;
import com.tiktok.util.R;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.net.ProtocolException;
import java.util.List;
import java.util.UUID;

public abstract class AuditServiceImpl<T,R> implements AuditService<T, R> {
    @Resource
    protected QiNiuConfig qiNiuConfig;
    @Resource
    protected SettingService settingService;

    protected ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);

    static final String contentType = "application/json";

    protected AuditResponse audit (List<ScoreJson> scoreJsonList, BodyJson bodyJson){
        AuditResponse auditResponse = new AuditResponse();
        for(ScoreJson scoreJson : scoreJsonList){
            auditResponse = audit(scoreJsonList,bodyJson);

            if(auditResponse.getFlag()){
                auditResponse.setAuditStatus(scoreJson.getAuditStatus());
                return auditResponse;
            }
        }
        ScenesJson scenesJson = bodyJson.getResult().getResult().getScenes();

        if(endCheck(scenesJson)){
            auditResponse.setAuditStatus(AuditStatus.SUCCESS);
        }else{
            auditResponse.setAuditStatus(AuditStatus.PASS);
            auditResponse.setMsg("内容不合法");
        }
        return auditResponse;
    }

    private AuditResponse getInfo(List<CutsJson>types,Double minPolitician,String key){
        AuditResponse auditResponse = new AuditResponse();
        auditResponse.setFlag(false);
        String info = null;
        for (CutsJson type : types) {
            for (DetailsJson detail : type.getDetails()) {
                if(detail.getScore()>minPolitician){
                    if(!detail.getLabel().equals(key)){
                        info = AuditMsgMap.getInfo(detail.getLabel());
                        auditResponse.setMsg(info);
                        auditResponse.setOffset(type.getOffset());

                    }
                    auditResponse.setFlag(true);
                }
            }
        }
        if(auditResponse.getFlag()&& ObjectUtils.isEmpty(auditResponse.getMsg())){
            auditResponse.setMsg("该视频违反相关准则");
        }
        return auditResponse;
    }

    private AuditResponse audit(ScoreJson scoreJson,BodyJson bodyJson){

        AuditResponse auditResponse = new AuditResponse();
        auditResponse.setFlag(true);
        auditResponse.setAuditStatus(scoreJson.getAuditStatus());
        Double minPolitician = scoreJson.getMinPolitician();
        Double maxPolitician = scoreJson.getMaxPolitician();
        Double minPulp = scoreJson.getMinPulp();
        Double maxPulp = scoreJson.getMaxPulp();
        Double minTerror = scoreJson.getMinTerror();
        Double maxTerror = scoreJson.getMaxTerror();
        if(!ObjectUtils.isEmpty(bodyJson.getPolitician())){
            if(bodyJson.checkViolation(bodyJson.getPolitician(),minPolitician,maxPolitician)){
                AuditResponse response = getInfo(bodyJson.getPolitician(), minPolitician, "group");
                if(response.getFlag()){

                    auditResponse.setOffset(response.getOffset());
                    return auditResponse;
                }
            }
        }
        if (!ObjectUtils.isEmpty(bodyJson.getPulp())) {
            if (bodyJson.checkViolation(bodyJson.getPulp(),minPulp,maxPulp)) {
                AuditResponse response = getInfo(bodyJson.getPulp(), minPulp, "normal");
                auditResponse.setMsg(response.getMsg());
                // 如果违规则提前返回
                if (response.getFlag()) {
                    auditResponse.setOffset(response.getOffset());
                    return auditResponse;
                }
            }
        }
        if (!ObjectUtils.isEmpty(bodyJson.getTerror())) {
            if (bodyJson.checkViolation(bodyJson.getTerror(),minTerror,maxTerror)) {
                AuditResponse response = getInfo(bodyJson.getTerror(), minTerror, "normal");
                auditResponse.setMsg(response.getMsg());
                if (response.getFlag()) {
                    auditResponse.setOffset(response.getOffset());
                    return auditResponse;
                }
            }
        }
        auditResponse.setMsg("正常");
        auditResponse.setFlag(false);
        return auditResponse;

    }

    private boolean endCheck(ScenesJson scenesJson){
        TypeJson terror = scenesJson.getTerror();
        TypeJson pulp = scenesJson.getPulp();
        TypeJson politician = scenesJson.getPolitician();
        if(terror.getSuggestion().equals("block")||pulp.getSuggestion().equals("block")||politician.getSuggestion().equals("block")){
            return false;
        }
        return true;
    }


    protected Boolean isNeedAudit(){
        Setting setting = settingService.list().get(0);
        return setting.getAuditOpen();
    }

    protected String appendUUID(String url){
        Setting setting = settingService.list().get(0);
        if (setting.getAuth()) {
            final String uuid = UUID.randomUUID().toString();
            LocalCache.put(uuid,true);
            if (url.contains("?")){
                url = url+"&uuid="+uuid;
            }else {
                url = url+"?uuid="+uuid;
            }
            return url;
        }
        return url;

    }
}
