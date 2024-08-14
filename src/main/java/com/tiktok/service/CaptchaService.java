package com.tiktok.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tiktok.entity.Captcha;

import java.awt.image.BufferedImage;

public interface CaptchaService extends IService<Captcha> {
    BufferedImage getCaptcha(String uuId);

    boolean validate(Captcha captcha) throws Exception;
}
