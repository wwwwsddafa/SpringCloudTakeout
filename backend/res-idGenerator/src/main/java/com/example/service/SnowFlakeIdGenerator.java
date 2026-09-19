package com.example.service;

import cn.hutool.core.lang.Snowflake;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class SnowFlakeIdGenerator {
    private final DataCenterIdAssigner dataCenterIdAssigner;
    private final WorkerIdAssigner workerIdAssigner;

    private final long sequence; //序列号
    private final Snowflake snowflake;

    @Autowired
    public SnowFlakeIdGenerator(DataCenterIdAssigner dataCenterIdAssigner, WorkerIdAssigner workerIdAssigner){
        this.dataCenterIdAssigner = dataCenterIdAssigner;
        this.workerIdAssigner = workerIdAssigner;
        this.sequence = 0;
        // 问题在这里！构造器内部调用 Assigner 获取id new Snowflake
        this.snowflake = new Snowflake(

                workerIdAssigner.getWorkerId(), dataCenterIdAssigner.getDataCenterId()
        );
    }

    /*
    * 获取唯一的id号
    * */
public synchronized  Long nextId(){
    return snowflake.nextId();
}

       private final int batchMaxSize=1000; //批量获取id的最大数量

    /**
     * 批量获取id
     * @param size
     * @return
     */
    public List<Long> nextIdBatch(int size){
        if( size>batchMaxSize){
            size=batchMaxSize;
        }
        List<Long> idList=new ArrayList<>();
        for( int i=0;i<size;i++){
            idList.add(nextId());
        }
        return idList;
    }


    /**
     * 解析时间戳
     */
    public long parseIdToTime(long id){
        return snowflake.getGenerateDateTime(id);
    }

    /**
     * 解析数据中心ID
     */
    public long parseIdToDatacenterId(long id) {
        return snowflake.getDataCenterId(id );
    }

    /**
     * 解析workerId
     */
    public long parseIdToWorkerId(long id) {
        return snowflake.getWorkerId( id );
    }
}