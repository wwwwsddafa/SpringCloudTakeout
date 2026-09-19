package com.example.web.vo;

import com.example.bean.ResProductReview;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewVO extends ResProductReview {

    private String username;

    private String avatar;

    private String userRole;
}