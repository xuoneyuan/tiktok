package com.tiktok.config;

import com.qiniu.util.StringMap;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import com.qiniu.util.Auth;


@Data
@Component
@ConfigurationProperties(prefix = "qiniu.kodo")
public class QiNiuConfig {
    /**
     * 账号
     */
    private String accessKey;
    /**
     * 密钥
     */
    private String secretKey;
    /**
     * bucketName
     */
    private String bucketName;

    public static final String CNAME = "http://s4ep712zo.hn-bkt.clouddn.com";
    public static final String VIDEO_URL = "http://ai.qiniuapi.com/v3/video/censor";
    public static final String IMAGE_URL = "http://ai.qiniuapi.com/v3/image/censor";

    public static final String fops = "avthumb/mp4";

    public Auth buildAuth(){
        String accessKey = this.getAccessKey();
        String sercetKey = this.getSecretKey();
        return Auth.create(accessKey,sercetKey);


    }

    public String uploadToken(String type){
        Auth auth = buildAuth();
        return auth.uploadToken(bucketName, null, 300,new StringMap().put("mimeLimit","image/*").putNotEmpty("persistenOps",fops));

    }

    public String imageUploadToken(){
        Auth auth = buildAuth();
        return auth.uploadToken(bucketName,null,300,new StringMap().put("mimeLimit","image/*"));

    }

    public String getToken(String url,String method,String body,String contentType){
        Auth auth = buildAuth();
        String Token = "qiniu" + auth.signQiniuAuthorization(url,method,body==null?null:body.getBytes(),contentType);
        return Token;
    }
}
