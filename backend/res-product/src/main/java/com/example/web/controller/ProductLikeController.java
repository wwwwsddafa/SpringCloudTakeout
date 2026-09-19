package com.example.web.controller;

import com.example.sevice.ProductLikeService;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@Slf4j
public class ProductLikeController {

    @Autowired
    private ProductLikeService productLikeService;

    @PostMapping("/like")
    public ResultVo like(@RequestHeader("X-User-Id") String userId,
                         @RequestParam("fid") String fid,
                         @RequestParam("likeType") Integer likeType) {
        return productLikeService.like(userId, fid, likeType);
    }

    @GetMapping("/like/{fid}/status")
    public ResultVo getLikeStatus(@RequestHeader("X-User-Id") String userId,
                                  @PathVariable String fid) {
        return productLikeService.getLikeStatus(userId, fid);
    }

    @GetMapping("/like/{fid}/count")
    public ResultVo getLikeCount(@PathVariable String fid) {
        return productLikeService.getLikeCount(fid);
    }
}