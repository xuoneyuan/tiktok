package com.tiktok.controller;

import com.tiktok.entity.user.User;
import com.tiktok.entity.vo.FindPWVO;
import com.tiktok.entity.vo.RegisterVO;
import com.tiktok.service.LoginService;
import com.tiktok.util.JwtUtils;
import com.tiktok.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;

@RestController
@RequestMapping("/tiktok/login")
public class LoginController {

    @Resource
    private LoginService loginService;

    /**
     * 登录
     * @param user
     * @return
     */
    @PostMapping
    public R login(@RequestBody @Validated User user){
        user = loginService.login(user);
        //登陆成功，生成token
        String token = JwtUtils.getJwtToken(user.getId(),user.getNickName());
        HashMap<Object, Object> map = new HashMap<>();
        map.put("token",token);
        map.put("name",user.getNickName());
        map.put("user",user);
        return R.ok().data(map);
    }

    /**
     * 获取图形验证码
     * @param response
     * @param uuid
     * @throws IOException
     */
    @GetMapping("/captcha.jpg/{uuid}")
    public void captcha(HttpServletResponse response, @PathVariable String uuid) throws IOException {
        loginService.captcha(uuid,response);
    }

    /**
     * 邮箱验证码
     * @param email
     * @param code
     * @return
     */
    @PostMapping("/check")
    public R check(String email, Integer code){
        loginService.checkCode(email,code);
        return R.ok().message("success");
    }

    /**
     * 注册
     * @param registerVO
     * @return
     * @throws Exception
     */
    @PostMapping("/register")
    public R register(@RequestBody @Validated RegisterVO registerVO) throws Exception {
        if(!loginService.register(registerVO)){
            return R.error().message("fail");
        }
        return R.ok().message("success");
    }

    /**
     * 找回密码
     * @param findPWVO
     * @param response
     * @return
     */
    @PostMapping("/findPassword")
    public R findPassword(@RequestBody @Validated FindPWVO findPWVO, HttpServletResponse response){
        Boolean s = loginService.findPassword(findPWVO);
        return R.ok().message(s ? "success" : "fail");
    }



}
