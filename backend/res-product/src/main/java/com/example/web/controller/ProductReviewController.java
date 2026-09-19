package com.example.web.controller;

import com.example.bean.ResProductReview;
import com.example.sevice.ProductReviewService;
import com.example.web.vo.PageResult;
import com.example.web.vo.ResultVo;
import com.example.web.vo.ReviewVO;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@Slf4j
public class ProductReviewController {

    @Autowired
    private ProductReviewService productReviewService;

    @PostMapping("/review")
    public ResultVo addReview(@RequestHeader("X-User-Id") String userId,
                              @RequestParam("fid") String fid,
                              @RequestParam("starRating") Integer starRating,
                              @RequestParam(value = "reviewText", required = false) String reviewText,
                              @RequestParam(value = "orderId", required = false) String orderId,
                              @RequestPart(value = "images", required = false) MultipartFile[] images) {
        return productReviewService.addReview(userId, fid, starRating, reviewText, orderId, images);
    }

    @DeleteMapping("/review/{reviewId}")
    public ResultVo deleteReview(@RequestHeader("X-User-Id") String userId,
                                 @PathVariable String reviewId) {
        return productReviewService.deleteReview(userId, reviewId);
    }

    @GetMapping("/review/{fid}")
    public ResultVo listByFood(@PathVariable String fid,
                               @RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "10") int size) {
        PageResult<ReviewVO> result = productReviewService.listByFood(fid, page, size);
        return ResultVo.success(result);
    }

    @GetMapping("/review/{fid}/rating")
    public ResultVo getFoodRating(@PathVariable String fid) {
        Map<String, Object> rating = productReviewService.getFoodRating(fid);
        return ResultVo.success(rating);
    }

    @GetMapping("/review/{fid}/check")
    public ResultVo checkUserReview(@RequestHeader("X-User-Id") String userId,
                                    @PathVariable String fid) {
        return productReviewService.checkUserReview(userId, fid);
    }

    @GetMapping("/review/my")
    public ResultVo listMyReviews(@RequestHeader("X-User-Id") String userId,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        PageResult<ReviewVO> result = productReviewService.listByUser(userId, page, size);
        return ResultVo.success(result);
    }

    @PostMapping("/review/stats/batch")
    public ResultVo batchStats(@RequestBody List<String> fids,
                               @RequestParam("date") String date) {
        return ResultVo.success(productReviewService.batchProductStats(fids, date));
    }
}