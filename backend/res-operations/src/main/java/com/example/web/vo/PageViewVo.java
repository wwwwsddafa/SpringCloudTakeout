package com.example.web.vo;

import lombok.Data;

@Data
public class PageViewVo {

    private String pageUrl;

    private String pageType;

    private String fid;

    private String referer;

    private Integer staySeconds;
}