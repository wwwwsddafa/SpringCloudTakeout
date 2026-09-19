package com.example.api;

import com.example.web.vo.ResultVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "order")
public interface OrderApi {

    @GetMapping("/order/checkPurchase")
    ResultVo checkPurchase(@RequestHeader("X-User-Id") String userId,
                           @RequestParam("fid") String fid);

    @GetMapping("/order/list")
    ResultVo listOrders(@RequestHeader("X-User-Id") String userId);

    @GetMapping("/order/detail/{roid}")
    ResultVo orderDetail(@PathVariable String roid);

    @PutMapping("/order/address/{roid}")
    ResultVo updateAddress(@PathVariable String roid,
                           @RequestParam String address);

    @PostMapping("/order/refund/{roid}")
    ResultVo refundOrder(@PathVariable String roid);

    @PostMapping("/order/cancel/{roid}")
    ResultVo cancelOrder(@PathVariable String roid);
}