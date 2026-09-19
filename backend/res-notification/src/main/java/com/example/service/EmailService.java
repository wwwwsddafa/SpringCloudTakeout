package com.example.service;

import com.example.web.vo.EmailMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.util.ByteArrayDataSource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
@RefreshScope
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${mail.test.skip:true}")
    private boolean mailTestSkip;

    @Value("${mail.test.username-prefix:testbuyer}")
    private String mailTestUsernamePrefix;

    @Value("${mail.test.email-suffix:}")
    private String mailTestEmailSuffix;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void sendEmail(EmailMessage message) {
        if (shouldSkipTestMail(message)) {
            log.info("测试账号跳过邮件发送: type={}, to={}, user={}",
                    message.getType(), message.getEmail(), message.getUsername());
            return;
        }
        try {
            String subject = getSubject(message.getType());

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.getEmail());
            helper.setSubject(subject);

            if (EmailMessage.TYPE_OPS_REPORT.equals(message.getType())) {
                sendOpsReportEmail(helper, message);
            } else {
                String template = loadTemplate(message.getType());
                String html = renderTemplate(template, message.getUsername(), message.getParams());
                helper.setText(html, true);
            }

            mailSender.send(mimeMessage);
            log.info("邮件发送成功: type={}, to={}, subject={}", message.getType(), message.getEmail(), subject);
        } catch (MessagingException | IOException e) {
            log.error("邮件发送失败: type={}, to={}", message.getType(), message.getEmail(), e);
        }
    }

    private void sendOpsReportEmail(MimeMessageHelper helper, EmailMessage message) throws MessagingException {
        Map<String, Object> params = message.getParams();
        String date = params != null && params.get("date") != null ? params.get("date").toString() : "";

        String html = "<h2>当日运营报告</h2>"
                + "<p>日期: " + date + "</p>"
                + "<p>您好，请查收附件中的当日运营报告：</p>"
                + "<ul>"
                + "<li><b>当日成交情况汇总.pdf</b> - 订单概览、商品排行</li>"
                + "<li><b>当日运营情况汇总.xlsx</b> - 7个维度的详细运营数据</li>"
                + "</ul>"
                + "<p style='color:#999;'>此为系统自动生成报告，请勿回复。</p>";
        helper.setText(html, true);

        if (params != null) {
            String pdfBase64 = (String) params.get("pdfBase64");
            if (pdfBase64 != null && !pdfBase64.isEmpty()) {
                byte[] pdfBytes = Base64.getDecoder().decode(pdfBase64);
                helper.addAttachment("当日成交情况汇总.pdf",
                        new ByteArrayDataSource(pdfBytes, "application/pdf"));
            }

            String excelBase64 = (String) params.get("excelBase64");
            if (excelBase64 != null && !excelBase64.isEmpty()) {
                byte[] excelBytes = Base64.getDecoder().decode(excelBase64);
                helper.addAttachment("当日运营情况汇总.xlsx",
                        new ByteArrayDataSource(excelBytes,
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            }
        }
    }

    private String loadTemplate(String type) throws IOException {
        String templateName;
        switch (type) {
            case EmailMessage.TYPE_REGISTER:
                templateName = "email-register.html";
                break;
            case EmailMessage.TYPE_LOGIN:
                templateName = "email-login.html";
                break;
            case EmailMessage.TYPE_ORDER:
                templateName = "email-order.html";
                break;
            case EmailMessage.TYPE_PAYMENT:
                templateName = "email-payment.html";
                break;
            case EmailMessage.TYPE_OPS_REPORT:
                templateName = "email-ops-report.html";
                break;
            default:
                throw new IllegalArgumentException("未知邮件类型: " + type);
        }
        ClassPathResource resource = new ClassPathResource("templates/" + templateName);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    private String renderTemplate(String template, String username, Map<String, Object> params) {
        String now = LocalDateTime.now().format(FORMATTER);
        String html = template
                .replace("{username}", username != null ? username : "用户")
                .replace("{datetime}", now);

        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                html = html.replace("{" + entry.getKey() + "}",
                        entry.getValue() != null ? entry.getValue().toString() : "");
            }
        }
        return html;
    }

    private String getSubject(String type) {
        switch (type) {
            case EmailMessage.TYPE_REGISTER:
                return "欢迎注册外卖点餐平台";
            case EmailMessage.TYPE_LOGIN:
                return "登录安全提醒";
            case EmailMessage.TYPE_ORDER:
                return "订单已生成通知";
            case EmailMessage.TYPE_PAYMENT:
                return "支付成功通知";
            case EmailMessage.TYPE_OPS_REPORT:
                return "每日运营报告";
            default:
                return "外卖点餐通知";
        }
    }

    private boolean shouldSkipTestMail(EmailMessage message) {
        if (!mailTestSkip) {
            return false;
        }
        String username = message.getUsername();
        if (username != null && mailTestUsernamePrefix != null
                && !mailTestUsernamePrefix.isBlank()
                && username.startsWith(mailTestUsernamePrefix)) {
            return true;
        }
        String email = message.getEmail();
        if (email != null && mailTestEmailSuffix != null && !mailTestEmailSuffix.isBlank()) {
            String lower = email.toLowerCase();
            for (String suf : mailTestEmailSuffix.split(",")) {
                String s = suf.trim().toLowerCase();
                if (!s.isEmpty() && lower.endsWith(s)) {
                    return true;
                }
            }
        }
        return false;
    }
}