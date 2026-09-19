package com.example.web.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVo {

    private String roid;

    private String userid;

    private String uname;

    private String address;

    private String tel;

    private LocalDateTime orderTime;

    private String deliveryType;

    private String payment;

    private String ps;

    private Integer status;

    private String tradeno;

    private LocalDateTime payTime;

    private LocalDateTime cancelTime;

    private List<OrderItemVo> items;

    private java.math.BigDecimal totalAmount;

    private java.math.BigDecimal discountAmount;

    private java.math.BigDecimal payAmount;

    public static String statusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待支付";
            case 1: return "已支付";
            case 2: return "已完成";
            case 3: return "已取消";
            case 4: return "已退单";
            default: return "未知";
        }
    }

    public String getStatusText() {
        return statusText(this.status);
    }
}