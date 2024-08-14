package com.tiktok.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import org.codehaus.plexus.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtils {

    public static final long EXPIRE = 10000000 * 60 * 60 * 24;//token过期时间
    public static final String APP_SECRET = "";//密钥

    //生成token字符串
    public static String getJwtToken(Long id, String nickname){
        // 创建 payload 部分
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", id);
        payload.put("nickname", nickname);
        payload.put("exp", new Date(System.currentTimeMillis() + EXPIRE)); // 过期时间

        // 生成 token
        String token = JWTUtil.createToken(payload, APP_SECRET.getBytes());
        return token;
    }

    public static boolean checkToken(String jwtToken){
        if(StringUtils.isEmpty(jwtToken))return false;
        try{
            JWT jwt = JWTUtil.parseToken(jwtToken).setKey(APP_SECRET.getBytes());
            return jwt.verify();
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    public static boolean checkToken(HttpServletRequest request) {
        try {
            String jwtToken = request.getHeader("token");
            if (StrUtil.isEmpty(jwtToken)) return false;
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static Long getUserId(HttpServletRequest request) {
        String jwtToken = request.getHeader("token");
        if (StrUtil.isEmpty(jwtToken)) return null;

        // 使用 Hutool 解析 JWT
        JWT jwt = JWTUtil.parseToken(jwtToken).setKey(APP_SECRET.getBytes());

        // 验证签名是否有效
        if (!jwt.verify()) {
            return null;
        }

        // 从 token 中获取用户 ID
        Object idObj = jwt.getPayload("id");
        if (idObj != null) {
            return Long.valueOf(idObj.toString());
        }

        return null;
    }
}
