package com.example.web.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderVo {

    private String address;

    private String tel;

    private String deliveryType;

    private String payment;

    private String ps;

    private String freeOrderNo;

    private java.math.BigDecimal originalAmount;

    private java.math.BigDecimal expectAmount;
}