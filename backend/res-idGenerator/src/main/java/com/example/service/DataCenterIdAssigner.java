package com.example.service;

import org.springframework.stereotype.Component;

@Component
public class DataCenterIdAssigner {
    /**
     * 获取数据中心的Id
     * @return
     */
    public Long getDataCenterId(){
        //TODO: 上线后从nacos中根据实际情况 获取数据中心的Id，现在都当0
        return 0L;
    }
}