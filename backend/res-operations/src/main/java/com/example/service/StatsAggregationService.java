package com.example.service;

import java.time.LocalDate;

public interface StatsAggregationService {

    /** 聚合"今天"（定时任务用）。 */
    void aggregateHourly();

    /** 聚合指定日期（用于历史重算）。 */
    void aggregateOn(LocalDate statDate);
}