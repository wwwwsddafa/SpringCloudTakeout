package com.example.task;

import com.example.service.ReportConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReportScheduleInitializer {

    @Autowired
    private ReportConfigService reportConfigService;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("初始化运营报告定时任务...");
        reportConfigService.initSchedule();
    }
}