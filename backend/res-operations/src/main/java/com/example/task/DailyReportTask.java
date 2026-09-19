package com.example.task;

import com.example.service.ReportConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DailyReportTask {

    @Autowired
    private ReportConfigService reportConfigService;

    public void execute() {
        reportConfigService.executeScheduledReport();
    }
}