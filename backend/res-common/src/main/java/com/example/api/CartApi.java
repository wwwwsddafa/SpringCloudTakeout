package com.example.api;

import com.example.web.vo.ResultVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "cart")
public interface CartApi {

    @GetMapping("/cart/list")
    ResultVo listItems(@RequestHeader("X-User-Id") String userId);

    @DeleteMapping("/cart/clear")
    ResultVo clearCart(@RequestHeader("X-User-Id") String userId);
}