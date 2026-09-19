package com.example.web.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendVo {

    private List<TrendPoint> pvTrend;

    private List<TrendPoint> uvTrend;

    private List<TrendPoint> orderTrend;

    private List<TrendPoint> revenueTrend;

    private List<DeviceTrendPoint> deviceTrend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String date;
        private BigDecimal value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceTrendPoint {
        private String date;
        private Long mobile;
        private Long desktop;
        private Long tablet;
    }
}