package com.example.task;

import com.example.service.StatsAggregationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HourlyAggregationTask {

    @Autowired
    private StatsAggregationService statsAggregationService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void execute() {
        log.info("定时任务触发: 每小时聚合");
        statsAggregationService.aggregateHourly();
    }
}