package com.example.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/*
*
* 描述命令需要什么参数
*
* */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandParam {
    private String name;
    private String displayName;
    private String type;
    private boolean required;
    private String description;
}