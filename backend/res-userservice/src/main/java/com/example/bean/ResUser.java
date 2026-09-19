package com.example.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resuser")
public class ResUser {

    @TableId
    private String userid;

    private String username;

    private String pwd;

    private String email;
    @TableField("open_id")
    private String openId;

    private String avatar;

    @TableField("create_time")
    private LocalDateTime createTime;
}