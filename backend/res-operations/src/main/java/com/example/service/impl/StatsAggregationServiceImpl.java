package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.api.ProductApi;
import com.example.bean.*;
import com.example.constants.RedisKeys;
import com.example.dao.mapper.*;
import com.example.service.StatsAggregationService;
import com.example.web.vo.ResultVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class StatsAggregationServiceImpl implements StatsAggregationService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DailyPvStatsMapper dailyPvStatsMapper;

    @Autowired
    private DailyOrderStatsMapper dailyOrderStatsMapper;

    @Autowired
    private DailyProductStatsMapper dailyProductStatsMapper;

    @Autowired
    private DailyOpsSummaryMapper dailyOpsSummaryMapper;

    @Autowired
    private ProductApi productApi;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String[] PAGE_TYPES = {"HOME", "PRODUCT_DETAIL", "SEARCH", "CART", "ORDER", "OTHER"};

    @Override
    public void aggregateHourly() {
        aggregateOn(LocalDate.now());
    }

    @Override
    public void aggregateOn(LocalDate statDate) {
        String today = statDate.format(DATE_FMT);
        log.info("开始执行聚合任务: date={}", today);
        try {
            aggregatePvStats(today, statDate);
            aggregateOrderStats(today, statDate);
            aggregateProductStats(today, statDate);
            aggregateOpsSummary(today, statDate);
            log.info("聚合任务完成: date={}", today);
        } catch (Exception e) {
            log.error("聚合任务异常: date={}", today, e);
        }
    }

    private void aggregatePvStats(String today, LocalDate statDate) {
        for (String pageType : PAGE_TYPES) {
            String pvKey = RedisKeys.pvDaily(today, pageType);
            String uvKey = RedisKeys.uvDailyPageType(today, pageType);
            Long pv = getLong(pvKey);
            Long uv = redisTemplate.opsForHyperLogLog().size(uvKey);

            if (pv == null || pv == 0) {
                continue;
            }

            ResDailyPvStats stats = new ResDailyPvStats();
            stats.setId(UUID.randomUUID().toString());
            stats.setStatDate(statDate);
            stats.setPageType(pageType);
            stats.setFid(null);
            stats.setPvCount(pv);
            stats.setUvCount(uv != null ? uv : 0L);
            stats.setAvgStaySeconds(0);
            stats.setDevicePc(0L);
            stats.setDeviceMobile(0L);
            stats.setDeviceTablet(0L);
            stats.setCreateTime(LocalDateTime.now());

            LambdaQueryWrapper<ResDailyPvStats> query = new LambdaQueryWrapper<>();
            query.eq(ResDailyPvStats::getStatDate, statDate)
                    .eq(ResDailyPvStats::getPageType, pageType)
                    .isNull(ResDailyPvStats::getFid);
            ResDailyPvStats exist = dailyPvStatsMapper.selectOne(query);
            if (exist != null) {
                stats.setId(exist.getId());
                dailyPvStatsMapper.updateById(stats);
            } else {
                dailyPvStatsMapper.insert(stats);
            }
        }
    }

    private void aggregateOrderStats(String today, LocalDate statDate) {
        String totalKey = RedisKeys.orderDailyTotal(today);
        String amountKey = RedisKeys.orderDailyAmount(today);
        String paidKey = RedisKeys.orderDailyPaid(today);
        String paidAmountKey = RedisKeys.orderDailyPaidAmount(today);

        Long totalOrders = getLong(totalKey);
        if (totalOrders == null || totalOrders == 0) {
            return;
        }

        ResDailyOrderStats stats = new ResDailyOrderStats();
        stats.setId(UUID.randomUUID().toString());
        stats.setStatDate(statDate);
        stats.setTotalOrders(totalOrders.intValue());
        stats.setTotalAmount(getDecimal(amountKey));
        stats.setPaidOrders(getInt(paidKey));
        stats.setPaidAmount(getDecimal(paidAmountKey));
        stats.setCancelledOrders(0);
        stats.setPendingOrders(0);
        stats.setRefundedOrders(0);

        if (totalOrders != null && totalOrders > 0) {
            BigDecimal paidAmt = getDecimal(paidAmountKey);
            stats.setAvgOrderAmount(paidAmt != null
                    ? paidAmt.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
        } else {
            stats.setAvgOrderAmount(BigDecimal.ZERO);
        }

        stats.setMaxOrderAmount(BigDecimal.ZERO);
        stats.setAlipayOrders(0);
        stats.setWechatOrders(0);
        stats.setFreeOrderCount(0);
        stats.setFreeOrderAmount(BigDecimal.ZERO);
        stats.setCreateTime(LocalDateTime.now());

        LambdaQueryWrapper<ResDailyOrderStats> query = new LambdaQueryWrapper<>();
        query.eq(ResDailyOrderStats::getStatDate, statDate);
        ResDailyOrderStats exist = dailyOrderStatsMapper.selectOne(query);
        if (exist != null) {
            stats.setId(exist.getId());
            dailyOrderStatsMapper.updateById(stats);
        } else {
            dailyOrderStatsMapper.insert(stats);
        }
    }

    private void aggregateProductStats(String today, LocalDate statDate) {
        Set<String> pvKeys = redisTemplate.keys("ops:pv:daily:" + today + ":PRODUCT_DETAIL:*");
        Set<String> paidKeys = redisTemplate.keys(RedisKeys.orderDailyPaidProductPrefix(today) + ":*");

        Set<String> allFids = new HashSet<>();
        if (pvKeys != null) {
            for (String key : pvKeys) {
                allFids.add(key.substring(key.lastIndexOf(":") + 1));
            }
        }
        if (paidKeys != null) {
            for (String key : paidKeys) {
                allFids.add(key.substring(key.lastIndexOf(":") + 1));
            }
        }

        if (allFids.isEmpty()) {
            return;
        }

        Map<String, Map<String, Object>> ratingMap = new HashMap<>();
        try {
            ResultVo rr = productApi.getProductsRatingStats(new ArrayList<>(allFids), today);
            if (rr != null && rr.getCode() == 200 && rr.getData() instanceof List) {
                for (Object o : (List<?>) rr.getData()) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    ratingMap.put(String.valueOf(m.get("fid")), m);
                }
            }
        } catch (Exception e) {
            log.warn("批量获取评价/点赞统计失败: {}", e.getMessage());
        }

        for (String fid : allFids) {
            String pvFidKey = "ops:pv:daily:" + today + ":PRODUCT_DETAIL:" + fid;
            Long pv = getLong(pvFidKey);
            String uvKey = RedisKeys.uvDailyProduct(today, fid);
            Long uv = redisTemplate.opsForHyperLogLog().size(uvKey);

            String cartKey = RedisKeys.cartDailyProduct(today, fid);
            Long cartCount = getLong(cartKey);
            String paidProductKey = RedisKeys.orderDailyPaidProduct(today, fid);
            Long paidOrderCount = getLong(paidProductKey);
            String paidAmountKey = RedisKeys.orderDailyPaidAmountProduct(today, fid);
            BigDecimal paidAmount = getDecimal(paidAmountKey);

            ResDailyProductStats stats = new ResDailyProductStats();
            stats.setId(UUID.randomUUID().toString());
            stats.setStatDate(statDate);
            stats.setFid(fid);
            stats.setPvCount(pv != null ? pv.intValue() : 0);
            stats.setUvCount(uv != null ? uv.intValue() : 0);
            stats.setOrderCount(paidOrderCount != null ? paidOrderCount.intValue() : 0);
            stats.setOrderQuantity(0);
            stats.setSalesAmount(paidAmount != null ? paidAmount : BigDecimal.ZERO);

            // ② 当日口径：点赞/踩从 Redis daily counter 读
            Long likeCnt = getLong(RedisKeys.likeDailyProduct(today, fid));
            Long dislikeCnt = getLong(RedisKeys.dislikeDailyProduct(today, fid));
            stats.setLikeCount(likeCnt != null ? likeCnt.intValue() : 0);
            stats.setDislikeCount(dislikeCnt != null ? dislikeCnt.intValue() : 0);
            // 当日口径：评价从 batchProductStats 返回
            Map<String, Object> rating = ratingMap.get(fid);
            stats.setReviewCount(rating == null ? 0 : toInt(rating.get("reviewCount")));
            stats.setAvgStar(rating == null || rating.get("avgStar") == null
                    ? BigDecimal.ZERO : new BigDecimal(rating.get("avgStar").toString()));

            if (pv != null && pv > 0 && paidOrderCount != null) {
                BigDecimal rate = BigDecimal.valueOf(paidOrderCount * 100.0 / pv)
                        .setScale(2, RoundingMode.HALF_UP);
                stats.setConversionRate(rate.min(new BigDecimal("100.00")));
            } else {
                stats.setConversionRate(BigDecimal.ZERO);
            }
            stats.setCartCount(cartCount != null ? cartCount.intValue() : 0);
            stats.setPaidOrderCount(paidOrderCount != null ? paidOrderCount.intValue() : 0);
            stats.setCreateTime(LocalDateTime.now());

            LambdaQueryWrapper<ResDailyProductStats> query = new LambdaQueryWrapper<>();
            query.eq(ResDailyProductStats::getStatDate, statDate)
                    .eq(ResDailyProductStats::getFid, fid);
            ResDailyProductStats exist = dailyProductStatsMapper.selectOne(query);
            if (exist != null) {
                stats.setFname(exist.getFname());
                stats.setCategory(exist.getCategory());
                if (stats.getFname() == null) {
                    fetchProductName(stats);
                }
                stats.setId(exist.getId());
                dailyProductStatsMapper.updateById(stats);
            } else {
                fetchProductName(stats);
                dailyProductStatsMapper.insert(stats);
            }
        }
    }

    private void aggregateOpsSummary(String today, LocalDate statDate) {
        String totalPvKey = RedisKeys.pvDailyTotal(today);
        String uvKey = RedisKeys.uvDaily(today);
        String sessionKey = RedisKeys.sessionDaily(today);
        String totalOrderKey = RedisKeys.orderDailyTotal(today);
        String paidAmountKey = RedisKeys.orderDailyPaidAmount(today);

        Long totalPv = getLong(totalPvKey);
        if (totalPv == null || totalPv == 0) {
            return;
        }

        Long totalUv = redisTemplate.opsForHyperLogLog().size(uvKey);
        Long sessions = redisTemplate.opsForHyperLogLog().size(sessionKey);
        Long totalOrders = getLong(totalOrderKey);
        BigDecimal totalRevenue = getDecimal(paidAmountKey);

        ResDailyOpsSummary summary = new ResDailyOpsSummary();
        summary.setId(UUID.randomUUID().toString());
        summary.setStatDate(statDate);
        summary.setTotalPv(totalPv);
        summary.setTotalUv(totalUv != null ? totalUv : 0L);
        summary.setNewUsers(getInt(RedisKeys.userDailyNew(today)));
        summary.setActiveUsers(totalUv != null ? totalUv.intValue() : 0);
        summary.setTotalOrders(totalOrders != null ? totalOrders.intValue() : 0);
        summary.setTotalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO);

        // ③ 转化率改为 下单用户数(UV_ORDER_DAILY HLL) ÷ 访客数(UV HOME)
        int orderUv = getIntHll(RedisKeys.uvOrderDaily(today));
        long denominator = totalUv != null ? totalUv : 0L;
        if (denominator > 0 && orderUv > 0) {
            BigDecimal rate = BigDecimal.valueOf(orderUv)
                    .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            summary.setConversionRate(rate.min(new BigDecimal("100.00")));
        } else {
            summary.setConversionRate(BigDecimal.ZERO);
        }
        
        if (totalOrders != null && totalOrders > 0 && totalRevenue != null) {
            summary.setAvgOrderAmount(totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP));
        } else {
            summary.setAvgOrderAmount(BigDecimal.ZERO);
        }

        summary.setTotalLikes(0);
        summary.setTotalReviews(0);

        LambdaQueryWrapper<ResDailyProductStats> pq = new LambdaQueryWrapper<>();
        pq.eq(ResDailyProductStats::getStatDate, statDate);
        List<ResDailyProductStats> todayProducts = dailyProductStatsMapper.selectList(pq);
        if (todayProducts != null) {
            int sumLikes = todayProducts.stream()
                    .mapToInt(p -> p.getLikeCount() != null ? p.getLikeCount() : 0).sum();
            int sumReviews = todayProducts.stream()
                    .mapToInt(p -> p.getReviewCount() != null ? p.getReviewCount() : 0).sum();
            summary.setTotalLikes(sumLikes);
            summary.setTotalReviews(sumReviews);
        }

        summary.setTopProductIds("[]");
        summary.setTopSearchKeywords("[]");

        StringBuilder pvSb = new StringBuilder();
        StringBuilder odSb = new StringBuilder();
        for (int h = 0; h < 24; h++) {
            Long ph = getLong(RedisKeys.pvHourly(today, h));
            Long oh = getLong(RedisKeys.orderHourly(today, h));
            if (h > 0) { pvSb.append(","); odSb.append(","); }
            pvSb.append(h).append(":").append(ph != null ? ph : 0L);
            odSb.append(h).append(":").append(oh != null ? oh : 0L);
        }
        summary.setHourlyPvJson(pvSb.toString());
        summary.setHourlyOrderJson(odSb.toString());

        Map<String, Long> deviceMap = new HashMap<>();
        Set<String> deviceKeys = redisTemplate.keys(RedisKeys.deviceDailyPrefix(today) + ":*");
        if (deviceKeys != null) {
            for (String dk : deviceKeys) {
                String dt = dk.substring(dk.lastIndexOf(":") + 1);
                Long cnt = getLong(dk);
                if (cnt != null && cnt > 0) {
                    deviceMap.put(dt, cnt);
                }
            }
        }
        summary.setDeviceDistJson(toJson(deviceMap));
        summary.setBrowserDistJson("{}");
        summary.setCreateTime(LocalDateTime.now());

        LambdaQueryWrapper<ResDailyOpsSummary> query = new LambdaQueryWrapper<>();
        query.eq(ResDailyOpsSummary::getStatDate, statDate);
        ResDailyOpsSummary exist = dailyOpsSummaryMapper.selectOne(query);
        if (exist != null) {
            summary.setId(exist.getId());
            dailyOpsSummaryMapper.updateById(summary);
        } else {
            dailyOpsSummaryMapper.insert(summary);
        }
    }

    private Long getLong(String key) {
        Object val = redisTemplate.opsForValue().get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getInt(String key) {
        Long val = getLong(key);
        return val != null ? val.intValue() : 0;
    }

    private int getIntHll(String key) {
        Long val = redisTemplate.opsForHyperLogLog().size(key);
        return val != null ? val.intValue() : 0;
    }

    private BigDecimal getDecimal(String key) {
        Object val = redisTemplate.opsForValue().get(key);
        if (val == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return (int) Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void fetchProductName(ResDailyProductStats stats) {
        for (int i = 0; i < 3; i++) {
            try {
                ResultVo result = productApi.getProductName(stats.getFid());
                if (result != null && result.getCode() == 200 && result.getData() != null) {
                    Map<String, Object> data = (Map<String, Object>) result.getData();
                    Object fname = data.get("fname");
                    Object category = data.get("category");
                    if (fname != null) {
                        stats.setFname(fname.toString());
                    }
                    if (category != null) {
                        stats.setCategory(category.toString());
                    }
                    return;
                } else {
                    log.warn("获取商品名称返回异常: fid={}, result={}", stats.getFid(), result);
                }
            } catch (Exception e) {
                log.error("获取商品名称失败(第{}次): fid={}, error={}", i + 1, stats.getFid(), e.getMessage());
                if (i < 2) {
                    try {
                        Thread.sleep(1000L * (i + 1));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("获取商品名称最终失败: fid={}, 请检查product服务是否正常运行", stats.getFid());
    }

    private String toJson(Map<String, Long> map) {
        try {
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            log.error("序列化JSON失败: {}", e.getMessage());
            return "{}";
        }
    }
}