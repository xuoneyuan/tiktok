package com.tiktok.service.impl;


import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tiktok.constant.RedisConstant;
import com.tiktok.entity.Captcha;
import com.tiktok.exception.BaseException;
import com.tiktok.mapper.CaptchaMapper;
import com.tiktok.service.CaptchaService;
import com.tiktok.service.EmailService;
import com.tiktok.util.RedisCacheUtil;
import org.springframework.stereotype.Service;
import com.google.code.kaptcha.Producer;
import javax.annotation.Resource;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class CaptchaSerivceImpl extends ServiceImpl<CaptchaMapper, Captcha> implements CaptchaService {

    @Resource
    private Producer producer;
    @Resource
    private EmailService emailService;
    @Resource
    private RedisCacheUtil redisCacheUtil;

    @Override
    public BufferedImage getCaptcha(String uuid){
        String code = this.producer.createText();
        Captcha captcha = new Captcha();
        captcha.setUuid(uuid);
        captcha.setCode(code);
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(5);
        Date expireDate = Date.from(expireTime.atZone(ZoneId.systemDefault()).toInstant());
        captcha.setExpireTime(expireDate);

        this.save(captcha);
        return producer.createImage(code);

    }

    @Override
    public boolean validate(Captcha captcha) throws Exception{
        String email = captcha.getEmail();
        String code = captcha.getCode();
        captcha = this.getOne(new LambdaQueryWrapper<Captcha>().eq(Captcha::getUuid,captcha.getUuid()));
        if(captcha==null)
            throw new BaseException("uuid为空");

        if(!captcha.getCode().equals(code)){
            throw new BaseException("code错误");
        }
        if(captcha.getExpireTime().getTime()<=System.currentTimeMillis()){
            throw new BaseException("uuid过期");
        }
        if(!code.equals(captcha.getCode())){
            return false;
        }
        String emailCode = getSixCode();
        redisCacheUtil.set(RedisConstant.EMAIL_CODE + email, emailCode, RedisConstant.EMAIL_CODE_TIME);
        emailService.send(emailCode, "注册验证码" + code + "5分钟内有效");
        return true;
    }

    public static String getSixCode(){
        StringBuilder stringBuilder = new StringBuilder();
        for(int i=0;i<6;i++){
            int code = (int) (Math.random()*10);
            stringBuilder.append(code);
        }
        return stringBuilder.toString();
    }
}
