package com.example.web.controller;

import com.example.service.OpsStatsService;
import com.example.service.ProductEventService;
import com.example.service.ReportConfigService;
import com.example.service.ReportService;
import com.example.service.StatsAggregationService;
import com.example.web.vo.DailyStatsVo;
import com.example.web.vo.OpsDashboardVo;
import com.example.web.vo.ProductRankingVo;
import com.example.web.vo.ResultVo;
import com.example.web.vo.TrendVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
public class OpsStatsController {

    @Autowired
    private OpsStatsService opsStatsService;

    @Autowired
    private ProductEventService productEventService;

    @Autowired
    private StatsAggregationService statsAggregationService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReportConfigService reportConfigService;

    @GetMapping("/ops/stats/dashboard")
    public ResultVo<OpsDashboardVo> dashboard() {
        return ResultVo.success(opsStatsService.getDashboard());
    }

    @GetMapping("/ops/stats/daily")
    public ResultVo<DailyStatsVo> dailyStats(@RequestParam("date") String date,
                                              @RequestParam(value = "topN", defaultValue = "50") int topN) {
        return ResultVo.success(opsStatsService.getDailyStats(date, topN));
    }

    @GetMapping("/ops/stats/trend")
    public ResultVo<TrendVo> trend(@RequestParam(value = "days", defaultValue = "7") int days) {
        return ResultVo.success(opsStatsService.getTrend(days));
    }

    @GetMapping("/ops/stats/product-ranking")
    public ResultVo<ProductRankingVo> productRanking(
            @RequestParam("date") String date,
            @RequestParam(value = "topN", defaultValue = "50") int topN) {
        return ResultVo.success(opsStatsService.getProductRanking(date, topN));
    }

    @PostMapping("/ops/event/pv")
    public ResultVo trackPv(@RequestParam(value = "deviceType", defaultValue = "desktop") String deviceType) {
        return productEventService.trackPv(deviceType);
    }

    @PostMapping("/ops/event/cart-add")
    public ResultVo cartAdd(@RequestParam("fid") String fid) {
        return productEventService.trackCartAdd(fid);
    }

    @PostMapping("/ops/event/paid-order")
    public ResultVo paidOrder(@RequestParam("fid") String fid,
                              @RequestParam(value = "count", defaultValue = "1") int count) {
        return productEventService.trackPaidOrder(fid, count);
    }

    @PostMapping("/ops/event/order-completed")
    public ResultVo orderCompleted(@RequestParam("amount") BigDecimal amount,
                                   @RequestParam(value = "userId", required = false) String userId) {
        return productEventService.trackOrderCompleted(amount, userId);
    }

    @PostMapping("/ops/event/user-register")
    public ResultVo userRegister() {
        return productEventService.trackUserRegister();
    }

    @PostMapping("/ops/stats/aggregate")
    public ResultVo triggerAggregate(@RequestParam(value = "date", required = false) String date) {
        LocalDate d = (date == null) ? LocalDate.now() : LocalDate.parse(date);
        log.info("手动触发聚合任务: date={}", d);
        statsAggregationService.aggregateOn(d);
        return ResultVo.success("聚合完成: " + d);
    }

    @PostMapping("/ops/report/send")
    public ResultVo sendReport(@RequestParam("date") String date) {
        log.info("手动触发运营报告发送: date={}", date);
        String adminEmail = reportConfigService.getAdminEmailFromRedis();
        reportService.sendDailyReport(date, adminEmail);
        return ResultVo.success("运营报告已发送至 " + adminEmail);
    }

    @GetMapping("/ops/report/send")
    public ResultVo sendReportGet(@RequestParam("date") String date) {
        log.info("手动触发运营报告发送(浏览器安全测试): date={}", date);
        String adminEmail = reportConfigService.getAdminEmailFromRedis();
        reportService.sendDailyReport(date, adminEmail);
        return ResultVo.success("运营报告已发送至 " + adminEmail);
    }

    @GetMapping("/ops/report/config")
    public ResultVo<Map<String, String>> getReportConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("cron", reportConfigService.getCronFromRedis());
        config.put("adminEmail", reportConfigService.getAdminEmailFromRedis());
        return ResultVo.success(config);
    }

    @PostMapping("/ops/report/config")
    public ResultVo updateReportConfig(
            @RequestParam(value = "cron", required = false) String cron,
            @RequestParam(value = "adminEmail", required = false) String adminEmail) {
        if (cron != null && !cron.isBlank()) {
            reportConfigService.setCron(cron);
        }
        if (adminEmail != null && !adminEmail.isBlank()) {
            reportConfigService.setAdminEmail(adminEmail);
        }
        return ResultVo.success("配置已更新");
    }
}