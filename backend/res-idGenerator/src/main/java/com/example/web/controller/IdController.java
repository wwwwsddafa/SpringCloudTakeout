package com.example.web.controller;



import com.example.config.SnowFlakeConfig;
import com.example.exception.NetException;
import com.example.service.SnowFlakeIdGenerator;
import com.example.web.vo.ResultCode;
import com.example.web.vo.ResultVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@Slf4j
@Tag(name = "idGenerator服务", description = "分布式唯一id号生成服务")
public class IdController {
    @Value("${idGenerator.dateFormatter}")
    private String dateFormatter;
    private final SnowFlakeIdGenerator snowFlakeIdGenerator;

    public IdController(SnowFlakeIdGenerator snowFlakeIdGenerator) {
        this.snowFlakeIdGenerator = snowFlakeIdGenerator;
    }

    @GetMapping("/hello")
    @Operation(summary = "测试", description = "返回hello")
    public ResultVo<String> hello(){
        log.info("请求来了");
        return ResultVo.success("hello");
    }

    @GetMapping("/next/id")
    @Operation(summary = "生成一个分布式唯一id号", description = "格式:[1bit|41bit时间戳|10bit机器id(5 datacenter+5 worker)|12bit序列号]")
    public ResultVo next() {
        long id = snowFlakeIdGenerator.nextId();
        log.info("生成的id号"+id);

        return ResultVo.success(String.valueOf(id));
    }

    @GetMapping("/next/batch")
    @Operation(summary = "生成多个分布式唯一id号", description = "格式:[1bit|41bit时间戳|10bit机器id(5 datacenter+5 worker)|12bit序列号]")
    public ResultVo nextBatch ( @RequestParam(value = "size", defaultValue = "10") int size){
        List<String> ids= snowFlakeIdGenerator
                .nextIdBatch(size)
                 .stream()
                .map(String::valueOf)// 3. 每个 Long 转成 String
                .toList();
               return ResultVo.success(ids);

    }

    @GetMapping("/parse/time")
    @Operation(summary = "解析时间戳", description = "根据id号解析时间戳")
    public ResultVo parseTime(@RequestParam(value="id") String id){
        //字符串id转long
        long timestamp = snowFlakeIdGenerator.parseIdToTime(Long.parseLong(id));

        //把毫秒时间戳 → 上海时区格式化时间字符串
        String dateTime = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern(dateFormatter));

        return ResultVo.success(dateTime);
    }


    @GetMapping("/parse/workerId")
    @Operation(summary = "解析workerId", description = "根据id号解析workerId")
    public ResultVo parseWorkerId(@RequestParam(value="id") long id) {
        long workerId = this.snowFlakeIdGenerator.parseIdToWorkerId(id);
        return ResultVo.success(workerId);
    }

    @GetMapping("/parse/dataCenterId")
    @Operation(summary = "解析数据中心ID", description = "根据id号解析数据中心ID")
    public ResultVo parseDataCenterId(@RequestParam(value="id") long id){
        long dataCenterId=this.snowFlakeIdGenerator.parseIdToDatacenterId(id);
        return ResultVo.success( dataCenterId );
    }


}