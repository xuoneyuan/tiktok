package com.tiktok.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qiniu.storage.model.FileInfo;
import com.tiktok.config.LocalCache;
import com.tiktok.config.QiNiuConfig;
import com.tiktok.entity.File;
import com.tiktok.exception.BaseException;
import com.tiktok.mapper.FileMapper;
import com.tiktok.service.FileService;
import com.tiktok.service.QiNiuFileService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Objects;
import java.util.UUID;


@Service
public class FileServiceImpl extends ServiceImpl<FileMapper, File> implements FileService {
    @Resource
    private QiNiuFileService qiNiuFileService;

    @Override
    public Long save(String fileKey, Long userId){
        FileInfo fileInfo = qiNiuFileService.getFileInfo(fileKey);
        if(fileInfo == null){
            throw new IllegalArgumentException("参数错误");
        }
        File file = new File();
        String mimeType = fileInfo.mimeType;
        file.setFileKey(fileKey);
        file.setFormat(mimeType);
        file.setUserId(userId);
        file.setSize(fileInfo.fsize);
        file.setType(mimeType.contains("video")?"video":"picture");
        save(file);
        return file.getId();
    }

    @Override
    public Long generatePhoto(Long fileId, Long userId){
        File file = getById(fileId);
        String fileKey = file.getFileKey();
        File file1 = new File();
        file1.setFileKey(fileKey);
        file1.setFormat("image/*");
        file1.setType("图片");
        file1.setUserId(userId);
        save(file1);
        return file1.getId();
    }

    @Override
    public File getFileTrustUrl(Long fileId){
        File file = getById(fileId);
        if(Objects.isNull(file)){
            throw new BaseException("未找到该文件");
        }
        String s = UUID.randomUUID().toString();
        LocalCache.put(s,true);
        String url = QiNiuConfig.CNAME + "/" + file.getFileKey();

        if(url.contains("?")){
            url = url + "&uuid=" + s;
        }else {
            url = url + "?uuid=" + s;
        }
        file.setFileKey(url);
        return file;
    }

}
