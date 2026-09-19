package com.example.web.vo;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.example.bean.ResFood;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @program: cloud161
 * @description:
 * @author: zy
 * @create: 2026-08-17 11:24
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResfoodVo {
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

    private LocalDateTime createTime;
    private LocalDateTime updateTime;




    public static ResfoodVo from(ResFood food) {
        if (food == null) {
            return null;
        }
        return ResfoodVo.builder()
                .fid(food.getFid())
                .fname(food.getFname())
                .normprice(food.getNormprice())
                .realprice(food.getRealprice())
                .detail(food.getDetail())
                .fphoto(food.getFphoto())
                .category(food.getCategory())
                .status(food.getStatus())
                .likeCount(food.getLikeCount())
                .dislikeCount(food.getDislikeCount())
                .build();
    }
}