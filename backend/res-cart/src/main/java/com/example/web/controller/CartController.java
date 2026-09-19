package com.example.web.controller;

import com.example.sevice.CartService;
import com.example.web.vo.CartItemVo;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cart")
@Slf4j
public class CartController {

    @Autowired
    private CartService cartService;

    @PostMapping("/add")
    public ResultVo addItem(@RequestHeader("X-User-Id") String userId,
                            @RequestBody CartItemVo item) {
        cartService.addItem(userId, item);
        return ResultVo.success("添加成功");
    }

    @GetMapping("/list")
    public ResultVo listItems(@RequestHeader("X-User-Id") String userId) {
        List<CartItemVo> items = cartService.listItems(userId);
        return ResultVo.success(items);
    }

    @PostMapping("/update")
    public ResultVo updateNum(@RequestHeader("X-User-Id") String userId,
                              @RequestParam String fid,
                              @RequestParam Integer num) {
        cartService.updateNum(userId, fid, num);
        return ResultVo.success("更新成功");
    }

    @DeleteMapping("/remove/{fid}")
    public ResultVo removeItem(@RequestHeader("X-User-Id") String userId,
                               @PathVariable String fid) {
        cartService.removeItem(userId, fid);
        return ResultVo.success("删除成功");
    }

    @DeleteMapping("/clear")
    public ResultVo clearCart(@RequestHeader("X-User-Id") String userId) {
        cartService.clearCart(userId);
        return ResultVo.success("清空成功");
    }
}