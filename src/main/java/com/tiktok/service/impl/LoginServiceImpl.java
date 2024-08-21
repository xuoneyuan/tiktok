package com.tiktok.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tiktok.constant.RedisConstant;
import com.tiktok.entity.Captcha;
import com.tiktok.entity.user.User;
import com.tiktok.entity.vo.FindPWVO;
import com.tiktok.entity.vo.RegisterVO;
import com.tiktok.exception.BaseException;
import com.tiktok.service.CaptchaService;
import com.tiktok.service.LoginService;
import com.tiktok.service.UserService;
import com.tiktok.util.RedisCacheUtil;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;

@Service
public class LoginServiceImpl implements LoginService {
    @Resource
    private UserService userService;

    @Resource
    private CaptchaService captchaService;

    @Resource
    private RedisCacheUtil redisCacheUtil;

    @Override
    public User login(User user){
        String password = user.getPassword();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        user = userService.getOne(wrapper.eq(User::getEmail,user.getEmail()));
        if(ObjectUtil.isEmpty(user)){
            throw new BaseException("没有该账号");
        }
        if(!password.equals(user.getPassword())){
            throw new BaseException("密码不一致");
        }
        return user;
    }

    @Override
    public Boolean checkCode(String email, Integer code){
        if(ObjectUtil.isEmpty(email)||ObjectUtil.isEmpty(code)){
            throw new BaseException("参数为空");
        }
        Object object = redisCacheUtil.get(RedisConstant.EMAIL_CODE + email);

        if(!code.toString().equals(object)){
            throw new BaseException("验证码不正确");
        }
        return true;
    }


    @Override
    public void captcha (String uuid, HttpServletResponse response) throws IOException{
        if(ObjectUtil.isEmpty(uuid))
            throw new IllegalArgumentException("uuid不能为空");
        response.setHeader("Cache-Control","no-store, no-cache");
        response.setContentType("image/jpeg");
        BufferedImage captcha = captchaService.getCaptcha(uuid);
        ServletOutputStream outputStream = response.getOutputStream();
        ImageIO.write(captcha,"jpg",outputStream);
        IOUtils.closeQuietly(outputStream);

    }

    @Override
    public Boolean getCode(Captcha captcha) throws Exception{
        return captchaService.validate(captcha);
    }


    @Override
    public Boolean register(RegisterVO registerVO) throws Exception{
        if(userService.register(registerVO)){
            captchaService.removeById(registerVO.getUuid());
            return true;
        }
        return false;
    }

    @Override
    public Boolean findPassword(FindPWVO findPWVO){
        Boolean s = userService.findPassword(findPWVO);
        return s;
    }


}
