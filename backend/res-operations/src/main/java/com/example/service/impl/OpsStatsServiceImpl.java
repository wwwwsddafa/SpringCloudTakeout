package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.api.ProductApi;
import com.example.bean.*;
import com.example.dao.mapper.*;
import com.example.service.OpsStatsService;
import com.example.web.vo.DailyStatsVo;
import com.example.web.vo.OpsDashboardVo;
import com.example.web.vo.ProductRankingVo;
import com.example.web.vo.ResultVo;
import com.example.web.vo.TrendVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OpsStatsServiceImpl implements OpsStatsService {

    @Autowired
    private DailyOpsSummaryMapper dailyOpsSummaryMapper;

    @Autowired
    private DailyPvStatsMapper dailyPvStatsMapper;

    @Autowired
    private DailyOrderStatsMapper dailyOrderStatsMapper;

    @Autowired
    private DailyProductStatsMapper dailyProductStatsMapper;

    @Autowired
    private ProductApi productApi;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public OpsDashboardVo getDashboard() {
        LocalDate today = LocalDate.now();
        ResDailyOpsSummary summary = querySummary(today);
        List<ResDailyPvStats> pvStats = queryPvStats(today);

        if (summary == null) {
            return OpsDashboardVo.builder().statDate(today.format(DATE_FMT)).build();
        }

        Map<String, Long> pvByPageType = new HashMap<>();
        if (pvStats != null) {
            for (ResDailyPvStats s : pvStats) {
                pvByPageType.put(s.getPageType(), s.getPvCount());
            }
        }

        List<OpsDashboardVo.HourlyData> hourlyPv = parseHourlyJson(summary.getHourlyPvJson());
        List<OpsDashboardVo.HourlyData> hourlyOrders = parseHourlyJson(summary.getHourlyOrderJson());
        Map<String, Long> deviceDist = parseSimpleJson(summary.getDeviceDistJson());
        Map<String, Long> browserDist = parseSimpleJson(summary.getBrowserDistJson());

        return OpsDashboardVo.builder()
                .statDate(today.format(DATE_FMT))
                .totalPv(summary.getTotalPv())
                .totalUv(summary.getTotalUv())
                .totalOrders(summary.getTotalOrders())
                .totalRevenue(summary.getTotalRevenue())
                .conversionRate(summary.getConversionRate())
                .avgOrderAmount(summary.getAvgOrderAmount())
                .activeUsers(summary.getActiveUsers())
                .newUsers(summary.getNewUsers())
                .totalLikes(summary.getTotalLikes())
                .totalReviews(summary.getTotalReviews())
                .pvByPageType(pvByPageType)
                .hourlyPv(hourlyPv)
                .hourlyOrders(hourlyOrders)
                .deviceDistribution(deviceDist)
                .browserDistribution(browserDist)
                .build();
    }

    @Override
    public DailyStatsVo getDailyStats(String date, int topN) {
        LocalDate statDate = LocalDate.parse(date, DATE_FMT);
        ResDailyOpsSummary summary = querySummary(statDate);

        if (summary == null) {
            return DailyStatsVo.builder().statDate(date).build();
        }

        if (topN <= 0) {
            topN = 50;
        }

        List<ResDailyProductStats> productStats = queryProductStats(statDate);
        List<DailyStatsVo.ProductStatsVo> topProducts = new ArrayList<>();
        if (productStats != null) {
            topProducts = productStats.stream()
                    .sorted((a, b) -> {
                        BigDecimal sa = a.getSalesAmount() != null ? a.getSalesAmount() : BigDecimal.ZERO;
                        BigDecimal sb = b.getSalesAmount() != null ? b.getSalesAmount() : BigDecimal.ZERO;
                        return sb.compareTo(sa);
                    })
                    .limit(topN)
                    .map(p -> {
                        String fname = p.getFname();
                        if (fname == null || fname.isEmpty()) {
                            fname = fetchProductNameByFid(p.getFid());
                        }
                        return DailyStatsVo.ProductStatsVo.builder()
                                .fid(p.getFid())
                                .fname(fname)
                                .pvCount(p.getPvCount())
                                .uvCount(p.getUvCount())
                                .orderCount(p.getOrderCount())
                                .salesAmount(p.getSalesAmount())
                                .likeCount(p.getLikeCount())
                                .reviewCount(p.getReviewCount())
                                .avgStar(p.getAvgStar())
                                .conversionRate(p.getConversionRate())
                                .build();
                    })
                    .collect(Collectors.toList());
        }

        return DailyStatsVo.builder()
                .statDate(date)
                .totalPv(summary.getTotalPv())
                .totalUv(summary.getTotalUv())
                .totalOrders(summary.getTotalOrders())
                .totalRevenue(summary.getTotalRevenue())
                .conversionRate(summary.getConversionRate())
                .avgOrderAmount(summary.getAvgOrderAmount())
                .activeUsers(summary.getActiveUsers())
                .newUsers(summary.getNewUsers())
                .deviceDistribution(parseSimpleJson(summary.getDeviceDistJson()))
                .browserDistribution(parseSimpleJson(summary.getBrowserDistJson()))
                .topProducts(topProducts)
                .build();
    }

    @Override
    public TrendVo getTrend(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        List<TrendVo.TrendPoint> pvTrend = new ArrayList<>();
        List<TrendVo.TrendPoint> uvTrend = new ArrayList<>();
        List<TrendVo.TrendPoint> orderTrend = new ArrayList<>();
        List<TrendVo.TrendPoint> revenueTrend = new ArrayList<>();
        List<TrendVo.DeviceTrendPoint> deviceTrend = new ArrayList<>();

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            String dateStr = d.format(DATE_FMT);
            ResDailyOpsSummary summary = querySummary(d);

            pvTrend.add(TrendVo.TrendPoint.builder()
                    .date(dateStr)
                    .value(summary != null ? BigDecimal.valueOf(summary.getTotalPv()) : BigDecimal.ZERO)
                    .build());
            uvTrend.add(TrendVo.TrendPoint.builder()
                    .date(dateStr)
                    .value(summary != null ? BigDecimal.valueOf(summary.getTotalUv()) : BigDecimal.ZERO)
                    .build());
            orderTrend.add(TrendVo.TrendPoint.builder()
                    .date(dateStr)
                    .value(summary != null ? BigDecimal.valueOf(summary.getTotalOrders()) : BigDecimal.ZERO)
                    .build());
            revenueTrend.add(TrendVo.TrendPoint.builder()
                    .date(dateStr)
                    .value(summary != null ? summary.getTotalRevenue() : BigDecimal.ZERO)
                    .build());

            Map<String, Long> deviceDist = summary != null
                    ? parseSimpleJson(summary.getDeviceDistJson())
                    : new HashMap<>();
            deviceTrend.add(TrendVo.DeviceTrendPoint.builder()
                    .date(dateStr)
                    .mobile(deviceDist.getOrDefault("mobile", 0L))
                    .desktop(deviceDist.getOrDefault("desktop", 0L))
                    .tablet(deviceDist.getOrDefault("tablet", 0L))
                    .build());
        }

        return TrendVo.builder()
                .pvTrend(pvTrend)
                .uvTrend(uvTrend)
                .orderTrend(orderTrend)
                .revenueTrend(revenueTrend)
                .deviceTrend(deviceTrend)
                .build();
    }

    @Override
    public ProductRankingVo getProductRanking(String date, int topN) {
        LocalDate statDate = LocalDate.parse(date, DATE_FMT);
        List<ResDailyProductStats> productStats = queryProductStats(statDate);

        if (productStats == null || productStats.isEmpty()) {
            return ProductRankingVo.builder().statDate(date).build();
        }

        if (topN <= 0) {
            topN = 50;
        }

        List<ProductRankingVo.RankItem> topByOrderCount = buildRankList(
                productStats, (a, b) -> Integer.compare(
                        nvl(b.getOrderCount()), nvl(a.getOrderCount())),
                p -> p.getOrderCount() != null ? BigDecimal.valueOf(p.getOrderCount()) : BigDecimal.ZERO,
                topN);

        List<ProductRankingVo.RankItem> topByReviewCount = buildRankList(
                productStats, (a, b) -> Integer.compare(
                        nvl(b.getReviewCount()), nvl(a.getReviewCount())),
                p -> p.getReviewCount() != null ? BigDecimal.valueOf(p.getReviewCount()) : BigDecimal.ZERO,
                topN);

        List<ProductRankingVo.RankItem> topByAvgStar = buildRankList(
                productStats, (a, b) -> {
                    BigDecimal sa = a.getAvgStar() != null ? a.getAvgStar() : BigDecimal.ZERO;
                    BigDecimal sb = b.getAvgStar() != null ? b.getAvgStar() : BigDecimal.ZERO;
                    return sb.compareTo(sa);
                },
                p -> p.getAvgStar() != null ? p.getAvgStar() : BigDecimal.ZERO,
                topN);

        List<ProductRankingVo.RankItem> topByPvCount = buildRankList(
                productStats, (a, b) -> Integer.compare(
                        nvl(b.getPvCount()), nvl(a.getPvCount())),
                p -> p.getPvCount() != null ? BigDecimal.valueOf(p.getPvCount()) : BigDecimal.ZERO,
                topN);

        List<ProductRankingVo.RankItem> topByCartCount = buildRankList(
                productStats, (a, b) -> Integer.compare(
                        nvl(b.getCartCount()), nvl(a.getCartCount())),
                p -> p.getCartCount() != null ? BigDecimal.valueOf(p.getCartCount()) : BigDecimal.ZERO,
                topN);

        List<ProductRankingVo.RankItem> topByPaidOrderCount = buildRankList(
                productStats, (a, b) -> Integer.compare(
                        nvl(b.getPaidOrderCount()), nvl(a.getPaidOrderCount())),
                p -> p.getPaidOrderCount() != null ? BigDecimal.valueOf(p.getPaidOrderCount()) : BigDecimal.ZERO,
                topN);

        return ProductRankingVo.builder()
                .statDate(date)
                .topByOrderCount(topByOrderCount)
                .topByReviewCount(topByReviewCount)
                .topByAvgStar(topByAvgStar)
                .topByPvCount(topByPvCount)
                .topByCartCount(topByCartCount)
                .topByPaidOrderCount(topByPaidOrderCount)
                .build();
    }

    private List<ProductRankingVo.RankItem> buildRankList(List<ResDailyProductStats> list,
                                                           java.util.Comparator<ResDailyProductStats> comparator,
                                                           Function<ResDailyProductStats, BigDecimal> valueFn,
                                                           int topN) {
        return list.stream()
                .sorted(comparator)
                .limit(topN)
                .map(p -> {
                    String fname = p.getFname();
                    if (fname == null || fname.isEmpty()) {
                        fname = fetchProductNameByFid(p.getFid());
                    }
                    return ProductRankingVo.RankItem.builder()
                            .fid(p.getFid())
                            .fname(fname)
                            .category(p.getCategory())
                            .value(valueFn.apply(p))
                            .pvCount(p.getPvCount())
                            .orderCount(p.getOrderCount())
                            .paidOrderCount(p.getPaidOrderCount())
                            .cartCount(p.getCartCount())
                            .reviewCount(p.getReviewCount())
                            .avgStar(p.getAvgStar())
                            .salesAmount(p.getSalesAmount())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private int nvl(Integer val) {
        return val != null ? val : 0;
    }

    private String fetchProductNameByFid(String fid) {
        try {
            ResultVo result = productApi.getProductName(fid);
            if (result != null && result.getCode() == 200 && result.getData() != null) {
                Map<String, Object> data = (Map<String, Object>) result.getData();
                Object fname = data.get("fname");
                if (fname != null) {
                    return fname.toString();
                }
            }
        } catch (Exception e) {
            log.warn("查询时获取商品名称失败: fid={}, error={}", fid, e.getMessage());
        }
        return "商品-" + fid;
    }

    private ResDailyOpsSummary querySummary(LocalDate date) {
        LambdaQueryWrapper<ResDailyOpsSummary> query = new LambdaQueryWrapper<>();
        query.eq(ResDailyOpsSummary::getStatDate, date);
        return dailyOpsSummaryMapper.selectOne(query);
    }

    private List<ResDailyPvStats> queryPvStats(LocalDate date) {
        LambdaQueryWrapper<ResDailyPvStats> query = new LambdaQueryWrapper<>();
        query.eq(ResDailyPvStats::getStatDate, date);
        return dailyPvStatsMapper.selectList(query);
    }

    private List<ResDailyProductStats> queryProductStats(LocalDate date) {
        LambdaQueryWrapper<ResDailyProductStats> query = new LambdaQueryWrapper<>();
        query.eq(ResDailyProductStats::getStatDate, date);
        return dailyProductStatsMapper.selectList(query);
    }

    private List<OpsDashboardVo.HourlyData> parseHourlyJson(String json) {
        List<OpsDashboardVo.HourlyData> result = new ArrayList<>();
        if (json == null || json.isEmpty() || "[]".equals(json)) {
            return result;
        }
        try {
            json = json.replace("[", "").replace("]", "").replace("\"", "");
            if (json.isEmpty()) return result;
            String[] parts = json.split(",");
            for (String part : parts) {
                String[] kv = part.split(":");
                if (kv.length == 2) {
                    result.add(OpsDashboardVo.HourlyData.builder()
                            .hour(Integer.parseInt(kv[0].trim()))
                            .value(Long.parseLong(kv[1].trim()))
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("解析小时数据 JSON 失败: {}", json, e);
        }
        return result;
    }

    private Map<String, Long> parseSimpleJson(String json) {
        Map<String, Long> result = new HashMap<>();
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return result;
        }
        try {
            json = json.replace("{", "").replace("}", "").replace("\"", "");
            if (json.isEmpty()) return result;
            String[] parts = json.split(",");
            for (String part : parts) {
                String[] kv = part.split(":");
                if (kv.length == 2) {
                    result.put(kv[0].trim(), Long.parseLong(kv[1].trim()));
                }
            }
        } catch (Exception e) {
            log.warn("解析 JSON 失败: {}", json, e);
        }
        return result;
    }
}