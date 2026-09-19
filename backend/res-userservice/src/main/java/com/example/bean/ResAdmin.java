package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("resadmin")
public class ResAdmin {

    @TableId
    private String raid;

    private String raname;

    private String rapwd;
}