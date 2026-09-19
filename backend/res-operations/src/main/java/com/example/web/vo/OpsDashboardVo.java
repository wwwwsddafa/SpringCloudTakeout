package com.example.web.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsDashboardVo {

    private String statDate;

    private Long totalPv;

    private Long totalUv;

    private Integer totalOrders;

    private BigDecimal totalRevenue;

    private BigDecimal conversionRate;

    private BigDecimal avgOrderAmount;

    private Integer activeUsers;

    private Integer newUsers;

    private Integer totalLikes;

    private Integer totalReviews;

    private Map<String, Long> pvByPageType;

    private List<HourlyData> hourlyPv;

    private List<HourlyData> hourlyOrders;

    private Map<String, Long> deviceDistribution;

    private Map<String, Long> browserDistribution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyData {
        private Integer hour;
        private Long value;
    }
}