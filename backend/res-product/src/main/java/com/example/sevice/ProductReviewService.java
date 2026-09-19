package com.example.sevice;

import com.example.web.vo.PageResult;
import com.example.web.vo.ResultVo;
import com.example.web.vo.ReviewVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ProductReviewService {

    ResultVo addReview(String userId, String fid, Integer starRating, String reviewText,
                       String orderId, MultipartFile[] images);

    ResultVo deleteReview(String userId, String reviewId);

    PageResult<ReviewVO> listByFood(String fid, int page, int size);

    Map<String, Object> getFoodRating(String fid);

    List<Map<String, Object>> batchProductStats(Collection<String> fids, String date);

    ResultVo checkUserReview(String userId, String fid);

    PageResult<ReviewVO> listByUser(String userId, int page, int size);
}