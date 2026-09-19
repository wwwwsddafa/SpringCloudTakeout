package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("resorderitem")
public class ResOrderItem {

    @TableId
    private String riid;

    private String roid;

    private String fname;

    private String fid;

    private BigDecimal dealprice;

    private Integer num;
}