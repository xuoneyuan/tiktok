package com.tiktok.service.impl;

import com.google.code.kaptcha.util.Config;
import com.google.gson.Gson;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Region;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.util.Auth;
import com.tiktok.config.QiNiuConfig;
import com.tiktok.entity.File;
import com.tiktok.service.QiNiuFileService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;



@Service
public class QiNiuFileServiceImpl implements QiNiuFileService {

    @Resource
    private QiNiuConfig qiNiuConfig;

    @Override
    public String getToken(){
        return qiNiuConfig.videoUploadToken();
    }


    @Override
    @Async
    public FileInfo getFileInfo(String url) {
        Configuration configuration = new Configuration(Region.region0());
        Auth auth = qiNiuConfig.buildAuth();
        String bucketName = qiNiuConfig.getBucketName();
        BucketManager bucketManager = new BucketManager(auth, configuration);

        try {
            FileInfo fileInfo = bucketManager.stat(bucketName, url);
            return fileInfo;
        } catch (QiniuException ex) {
            System.out.println(ex.response.toString());
        }
        return null;
    }



}
