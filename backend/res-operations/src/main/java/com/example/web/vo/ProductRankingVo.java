package com.example.web.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 菜品排行榜响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRankingVo {

    private String statDate;

    /** 下单次数排行（已下单，非购物车） */
    private List<RankItem> topByOrderCount;

    /** 评价数量排行 */
    private List<RankItem> topByReviewCount;

    /** 口碑排行（按平均星级） */
    private List<RankItem> topByAvgStar;

    /** 浏览量排行 */
    private List<RankItem> topByPvCount;

    /** 购物车添加排行 */
    private List<RankItem> topByCartCount;

    /** 已付款订单数排行 */
    private List<RankItem> topByPaidOrderCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankItem {
        /** 商品ID */
        private String fid;
        /** 商品名称 */
        private String fname;
        /** 商品分类 */
        private String category;
        /** 排名值 */
        private BigDecimal value;
        /** 浏览量 */
        private Integer pvCount;
        /** 下单数 */
        private Integer orderCount;
        /** 已付款订单数 */
        private Integer paidOrderCount;
        /** 购物车添加数 */
        private Integer cartCount;
        /** 评价数 */
        private Integer reviewCount;
        /** 平均星级 */
        private BigDecimal avgStar;
        /** 销售额 */
        private BigDecimal salesAmount;
    }
}