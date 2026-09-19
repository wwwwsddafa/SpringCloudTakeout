package com.example.service;

import com.example.web.vo.DailyStatsVo;
import com.example.web.vo.OpsDashboardVo;
import com.example.web.vo.ProductRankingVo;
import com.example.web.vo.TrendVo;

public interface OpsStatsService {

    OpsDashboardVo getDashboard();

    DailyStatsVo getDailyStats(String date, int topN);

    TrendVo getTrend(int days);

    ProductRankingVo getProductRanking(String date, int topN);
}