package com.example.api;

import com.example.web.vo.ResultVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "operations")
public interface OpsEventApi {

    @PostMapping("/ops/event/cart-add")
    ResultVo trackCartAdd(@RequestParam("fid") String fid);

    @PostMapping("/ops/event/paid-order")
    ResultVo trackPaidOrder(@RequestParam("fid") String fid,
                            @RequestParam(value = "count", defaultValue = "1") int count);

    @PostMapping("/ops/event/order-completed")
    ResultVo trackOrderCompleted(@RequestParam("amount") BigDecimal amount,
                                 @RequestParam(value = "userId", required = false) String userId);

    @PostMapping("/ops/event/user-register")
    ResultVo trackUserRegister();
}