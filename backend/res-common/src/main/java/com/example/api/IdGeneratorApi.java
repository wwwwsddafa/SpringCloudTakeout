package com.example.api;

import com.example.web.vo.ResultVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @program: cloud161
 * @description: 用于访问idGenerator服务的api接口
 * @author: zy
 * @create: 2026-08-17 15:05
 */
@FeignClient(name = "idGenerator") // 指向注册中心中的微服务名称
public interface IdGeneratorApi {

    @GetMapping("/hello")
    public ResultVo<String> hello();

    @GetMapping("/next/id")
    public ResultVo next();

    @GetMapping("/next/batch")
    public ResultVo nextBatch(    @RequestParam(value = "size", defaultValue = "10")  int size  );

    @GetMapping("/parse/time")
    public ResultVo parseTime(   @RequestParam(value="id") String id);

    @GetMapping("/parse/workerId")
    public ResultVo parseWorkerId(   @RequestParam(value="id") String id) ;

    @GetMapping("/parse/dataCenterId")
    public ResultVo parseDataCenterId(  @RequestParam(value="id") String id);
}