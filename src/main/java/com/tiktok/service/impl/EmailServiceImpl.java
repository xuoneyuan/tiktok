package com.tiktok.service.impl;

import com.tiktok.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import javax.annotation.Resource;

@Service
public class EmailServiceImpl implements EmailService {
    @Resource
    private SimpleMailMessage simpleMailMessage;

    @Resource
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    String fromName;

    @Override
    @Async
    public void send(String email, String context) {
        simpleMailMessage.setSubject("tiktok");
        simpleMailMessage.setFrom(fromName);
        simpleMailMessage.setTo(email);
        simpleMailMessage.setText(context);
        javaMailSender.send(simpleMailMessage);
    }
}
