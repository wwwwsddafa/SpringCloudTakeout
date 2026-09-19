package com.example.service;

import com.example.web.vo.EmailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

@Service
@Slf4j
public class ReportConfigService {

    @Autowired
    private ReportService reportService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TaskScheduler taskScheduler;

    @Value("${ops.report.default-cron:0 0 12 * * ?}")
    private String defaultCron;

    @Value("${ops.report.default-admin-email:admin@example.com}")
    private String defaultAdminEmail;

    private static final String REDIS_KEY_CRON = "ops:config:report:cron";
    private static final String REDIS_KEY_ADMIN_EMAIL = "ops:config:report:admin-email";

    private ScheduledFuture<?> scheduledFuture;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void initSchedule() {
        String cron = getCronFromRedis();
        log.info("初始化运营报告定时任务: cron={}, adminEmail={}", cron, getAdminEmailFromRedis());
        reschedule(cron);
    }

    public synchronized void reschedule(String cronExpression) {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            log.info("已取消旧的运营报告定时任务");
        }
        scheduledFuture = taskScheduler.schedule(this::executeScheduledReport,
                new CronTrigger(cronExpression));
        log.info("运营报告定时任务已更新: cron={}", cronExpression);
    }

    public void executeScheduledReport() {
        String today = LocalDate.now().format(DATE_FMT);
        String adminEmail = getAdminEmailFromRedis();
        log.info("定时任务触发: 发送运营报告 date={}, to={}", today, adminEmail);
        try {
            reportService.sendDailyReport(today, adminEmail);
        } catch (Exception e) {
            log.error("定时运营报告发送失败: date={}", today, e);
        }
    }

    public String getCronFromRedis() {
        String cron = stringRedisTemplate.opsForValue().get(REDIS_KEY_CRON);
        return (cron != null && !cron.isBlank()) ? cron : defaultCron;
    }

    public void setCron(String cronExpression) {
        stringRedisTemplate.opsForValue().set(REDIS_KEY_CRON, cronExpression);
        reschedule(cronExpression);
        log.info("运营报告 Cron 已保存并生效: {}", cronExpression);
    }

    public String getAdminEmailFromRedis() {
        String email = stringRedisTemplate.opsForValue().get(REDIS_KEY_ADMIN_EMAIL);
        return (email != null && !email.isBlank()) ? email : defaultAdminEmail;
    }

    public void setAdminEmail(String email) {
        stringRedisTemplate.opsForValue().set(REDIS_KEY_ADMIN_EMAIL, email);
        log.info("运营报告管理员邮箱已保存: {}", email);
    }
}