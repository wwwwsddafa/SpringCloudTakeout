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
public class DailyStatsVo {

    private String statDate;

    private Long totalPv;

    private Long totalUv;

    private Integer totalOrders;

    private BigDecimal totalRevenue;

    private BigDecimal conversionRate;

    private BigDecimal avgOrderAmount;

    private Integer activeUsers;

    private Integer newUsers;

    private Map<String, Long> deviceDistribution;

    private Map<String, Long> browserDistribution;

    private List<ProductStatsVo> topProducts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductStatsVo {
        private String fid;
        private String fname;
        private Integer pvCount;
        private Integer uvCount;
        private Integer orderCount;
        private BigDecimal salesAmount;
        private Integer likeCount;
        private Integer reviewCount;
        private BigDecimal avgStar;
        private BigDecimal conversionRate;
    }
}