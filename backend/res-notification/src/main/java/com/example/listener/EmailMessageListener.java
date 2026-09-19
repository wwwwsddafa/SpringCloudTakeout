package com.example.listener;

import com.example.config.RabbitConfig;
import com.example.service.EmailService;
import com.example.web.vo.EmailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EmailMessageListener {

    @Autowired
    private EmailService emailService;

    @RabbitListener(queues = RabbitConfig.EMAIL_QUEUE)
    public void handleEmailMessage(EmailMessage message) {
        log.info("收到邮件消息: type={}, userId={}, email={}", message.getType(), message.getUserId(), message.getEmail());
        try {
            emailService.sendEmail(message);
        } catch (Exception e) {
            log.error("处理邮件消息异常: type={}, userId={}", message.getType(), message.getUserId(), e);
        }
    }
}