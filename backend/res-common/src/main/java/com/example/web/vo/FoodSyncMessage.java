package com.example.web.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodSyncMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String FOOD_EXCHANGE = "food.exchange";
    public static final String FOOD_SYNC_ROUTING_KEY = "food.sync";

    public static final String ACTION_SAVE = "SAVE";
    public static final String ACTION_DELETE = "DELETE";

    private String action;

    private String fid;

    private String fname;

    private BigDecimal normprice;

    private BigDecimal realprice;

    private String detail;

    private String fphoto;

    private String category;

    private Integer status;

    private Integer likeCount;

    private Integer dislikeCount;
}