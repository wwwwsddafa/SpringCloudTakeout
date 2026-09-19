package com.example.web.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemVo {

    private String fid;

    private String fname;

    private BigDecimal realprice;

    private String fphoto;

    private Integer num;
}