package com.example.sevice.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api.FileUploadApi;
import com.example.api.OrderApi;
import com.example.api.UserServiceApi;
import com.example.bean.ResFood;
import com.example.bean.ResProductReview;
import com.example.dao.mapper.ResFoodMapper;
import com.example.dao.mapper.ResProductReviewMapper;
import com.example.exceptions.BizException;
import com.example.sevice.ProductReviewService;
import com.example.sevice.ResFoodService;
import com.example.web.vo.FoodSyncMessage;
import com.example.web.vo.PageResult;
import com.example.web.vo.ResultCode;
import com.example.web.vo.ResultVo;
import com.example.web.vo.ReviewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductReviewServiceImpl implements ProductReviewService {

    private static final int MAX_REVIEW_TEXT_LENGTH = 500;
    private static final int MAX_REVIEW_IMAGES_COUNT = 9;
    private static final String PURCHASE_CHECK_PREFIX = "purchase:check:";
    private static final long PURCHASE_CHECK_TTL_MINUTES = 5;

    @Autowired
    private ResProductReviewMapper reviewMapper;

    @Autowired
    private ResFoodMapper resFoodMapper;

    @Autowired
    private ResFoodService resFoodService;

    @Autowired
    private OrderApi orderApi;

    @Autowired
    private FileUploadApi fileUploadApi;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserServiceApi userServiceApi;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public ResultVo addReview(String userId, String fid, Integer starRating, String reviewText,
                              String orderId, MultipartFile[] images) {
        if (starRating == null || starRating < 1 || starRating > 5) {
            throw new BizException(ResultCode.REVIEW_STAR_INVALID);
        }
        if (fid == null || fid.isEmpty()) {
            throw new BizException(ResultCode.REVIEW_PRODUCT_ID_EMPTY);
        }
        if (reviewText != null && reviewText.length() > MAX_REVIEW_TEXT_LENGTH) {
            throw new BizException(ResultCode.REVIEW_TEXT_TOO_LONG);
        }

        ResFood food = resFoodMapper.selectById(fid);
        if (food == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }

        if (!checkPurchaseCached(userId, fid)) {
            throw new BizException(ResultCode.NOT_PURCHASED);
        }

        ResProductReview existing = reviewMapper.selectOne(
                new LambdaQueryWrapper<ResProductReview>()
                        .eq(ResProductReview::getUserId, userId)
                        .eq(ResProductReview::getFid, fid)
                        .eq(ResProductReview::getStatus, 1));

        if (existing != null) {
            throw new BizException(ResultCode.REVIEW_ALREADY_EXISTS);
        }

        String imageUrls = null;
        if (images != null && images.length > 0 && images.length <= MAX_REVIEW_IMAGES_COUNT) {
            ResultVo uploadResult = fileUploadApi.uploadReview(images);
            if (uploadResult != null && uploadResult.getCode() == 200) {
                @SuppressWarnings("unchecked")
                List<String> urls = (List<String>) uploadResult.getData();
                if (urls != null && !urls.isEmpty()) {
                    imageUrls = String.join(",", urls);
                }
            }
        }

        ResProductReview review = new ResProductReview();
        review.setReviewId(UUID.randomUUID().toString().replace("-", ""));
        review.setFid(fid);
        review.setUserId(userId);
        review.setOrderId(orderId);
        review.setStarRating(starRating);
        review.setReviewText(reviewText);
        review.setReviewImages(imageUrls);
        review.setStatus(1);
        review.setCreateTime(LocalDateTime.now());
        review.setUpdateTime(LocalDateTime.now());

        reviewMapper.insert(review);

        resFoodService.evictFoodCache();
        syncFoodToEs(review.getFid());
        log.info("用户评价商品: userId={}, fid={}, star={}, images={}", userId, review.getFid(), review.getStarRating(), imageUrls != null);
        return ResultVo.success("评价成功");
    }

    @Override
    @Transactional
    public ResultVo deleteReview(String userId, String reviewId) {
        ResProductReview review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BizException(ResultCode.REVIEW_NOT_FOUND);
        }
        if (!review.getUserId().equals(userId)) {
            throw new BizException(ResultCode.REVIEW_NOT_OWNER);
        }

        review.setStatus(0);
        review.setUpdateTime(LocalDateTime.now());
        reviewMapper.updateById(review);

        resFoodService.evictFoodCache();
        syncFoodToEs(review.getFid());
        log.info("用户删除评价: userId={}, reviewId={}", userId, reviewId);
        return ResultVo.success("删除成功");
    }

    @Override
    public PageResult<ReviewVO> listByFood(String fid, int page, int size) {
        Page<ResProductReview> pageParam = new Page<>(page, size);
        IPage<ResProductReview> result = reviewMapper.selectPage(pageParam,
                new LambdaQueryWrapper<ResProductReview>()
                        .eq(ResProductReview::getFid, fid)
                        .eq(ResProductReview::getStatus, 1)
                        .orderByDesc(ResProductReview::getCreateTime));

        List<ReviewVO> voList = enrichWithUserInfo(result.getRecords());

        PageResult<ReviewVO> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setRecords(voList);
        return pageResult;
    }

    @Override
    public Map<String, Object> getFoodRating(String fid) {
        List<ResProductReview> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<ResProductReview>()
                        .eq(ResProductReview::getFid, fid)
                        .eq(ResProductReview::getStatus, 1));

        Map<String, Object> rating = new HashMap<>();
        rating.put("reviewCount", reviews.size());

        if (reviews.isEmpty()) {
            rating.put("avgStar", 0.0);
            rating.put("distribution", new int[]{0, 0, 0, 0, 0});
            return rating;
        }

        double avg = reviews.stream()
                .mapToInt(ResProductReview::getStarRating)
                .average()
                .orElse(0.0);
        rating.put("avgStar", Math.round(avg * 10.0) / 10.0);

        int[] dist = new int[5];
        for (ResProductReview r : reviews) {
            int star = r.getStarRating();
            if (star >= 1 && star <= 5) {
                dist[star - 1]++;
            }
        }
        rating.put("distribution", dist);

        return rating;
    }

    @Override
    public ResultVo checkUserReview(String userId, String fid) {
        ResProductReview review = reviewMapper.selectOne(
                new LambdaQueryWrapper<ResProductReview>()
                        .eq(ResProductReview::getUserId, userId)
                        .eq(ResProductReview::getFid, fid)
                        .eq(ResProductReview::getStatus, 1));
        if (review == null) {
            return ResultVo.success(null);
        }
        return ResultVo.success(review);
    }

    @Override
    public PageResult<ReviewVO> listByUser(String userId, int page, int size) {
        Page<ResProductReview> pageParam = new Page<>(page, size);
        IPage<ResProductReview> result = reviewMapper.selectPage(pageParam,
                new LambdaQueryWrapper<ResProductReview>()
                        .eq(ResProductReview::getUserId, userId)
                        .eq(ResProductReview::getStatus, 1)
                        .orderByDesc(ResProductReview::getCreateTime));

        List<ReviewVO> voList = enrichWithUserInfo(result.getRecords());

        PageResult<ReviewVO> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setRecords(voList);
        return pageResult;
    }

    private List<ReviewVO> enrichWithUserInfo(List<ResProductReview> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return reviews.stream().map(review -> {
            ReviewVO vo = new ReviewVO();
            vo.setReviewId(review.getReviewId());
            vo.setFid(review.getFid());
            vo.setUserId(review.getUserId());
            vo.setOrderId(review.getOrderId());
            vo.setStarRating(review.getStarRating());
            vo.setReviewText(review.getReviewText());
            vo.setReviewImages(review.getReviewImages());
            vo.setStatus(review.getStatus());
            vo.setCreateTime(review.getCreateTime());
            vo.setUpdateTime(review.getUpdateTime());
            try {
                ResultVo userResult = userServiceApi.getUserInfoById(review.getUserId());
                if (userResult != null && userResult.getCode() == 200 && userResult.getData() != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> userData = (Map<String, Object>) userResult.getData();
                    vo.setUsername((String) userData.getOrDefault("username", "未知用户"));
                    vo.setAvatar((String) userData.getOrDefault("avatar", ""));
                    vo.setUserRole((String) userData.getOrDefault("role", ""));
                }
            } catch (Exception e) {
                log.warn("获取用户信息失败: userId={}, error={}", review.getUserId(), e.getMessage());
                vo.setUsername("用户" + (review.getUserId().length() > 8 ?
                        review.getUserId().substring(0, 8) : review.getUserId()));
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private void syncFoodToEs(String fid) {
        try {
            ResFood food = resFoodMapper.selectById(fid);
            if (food == null) {
                return;
            }
            FoodSyncMessage message = FoodSyncMessage.builder()
                    .action(FoodSyncMessage.ACTION_SAVE)
                    .fid(food.getFid())
                    .fname(food.getFname())
                    .normprice(food.getNormprice())
                    .realprice(food.getRealprice())
                    .detail(food.getDetail())
                    .fphoto(food.getFphoto())
                    .category(food.getCategory())
                    .status(food.getStatus())
                    .likeCount(food.getLikeCount() != null ? food.getLikeCount() : 0)
                    .dislikeCount(food.getDislikeCount() != null ? food.getDislikeCount() : 0)
                    .build();
            rabbitTemplate.convertAndSend(
                    FoodSyncMessage.FOOD_EXCHANGE,
                    FoodSyncMessage.FOOD_SYNC_ROUTING_KEY,
                    message);
        } catch (Exception e) {
            log.error("评论后同步ES失败: fid={}, error={}", fid, e.getMessage());
        }
    }

    private boolean checkPurchaseCached(String userId, String fid) {
        String cacheKey = PURCHASE_CHECK_PREFIX + userId + ":" + fid;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return "1".equals(cached);
        }
        try {
            ResultVo purchaseResult = orderApi.checkPurchase(userId, fid);
            if (purchaseResult == null || purchaseResult.getCode() != 200) {
                log.warn("订单服务检查购买状态异常: userId={}, fid={}", userId, fid);
                return false;
            }
            Boolean purchased = (Boolean) purchaseResult.getData();
            boolean result = purchased != null && purchased;
            redisTemplate.opsForValue().set(cacheKey, result ? "1" : "0",
                    PURCHASE_CHECK_TTL_MINUTES, TimeUnit.MINUTES);
            return result;
        } catch (Exception e) {
            log.error("调用订单服务检查购买状态失败: userId={}, fid={}, error={}", userId, fid, e.getMessage());
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> batchProductStats(Collection<String> fids, String date) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (fids == null || fids.isEmpty()) {
            return result;
        }
        List<String> idList = new ArrayList<>(fids);

        // ② 当日口径：按 date 过滤评价时间
        LocalDate statDate = LocalDate.parse(date);
        LocalDateTime start = statDate.atStartOfDay();
        LocalDateTime end = statDate.atTime(LocalTime.MAX);

        List<ResProductReview> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<ResProductReview>()
                        .in(ResProductReview::getFid, idList)
                        .eq(ResProductReview::getStatus, 1)
                        .ge(ResProductReview::getCreateTime, start)
                        .lt(ResProductReview::getCreateTime, end));
        Map<String, int[]> agg = new HashMap<>();
        for (ResProductReview r : reviews) {
            int[] a = agg.computeIfAbsent(r.getFid(), k -> new int[2]);
            a[0] += 1;
            a[1] += (r.getStarRating() == null ? 0 : r.getStarRating());
        }

        List<ResFood> foods = resFoodMapper.selectList(
                new LambdaQueryWrapper<ResFood>().in(ResFood::getFid, idList));

        for (ResFood f : foods) {
            Map<String, Object> m = new HashMap<>();
            m.put("fid", f.getFid());
            m.put("likeCount", f.getLikeCount() == null ? 0 : f.getLikeCount());
            m.put("dislikeCount", f.getDislikeCount() == null ? 0 : f.getDislikeCount());
            int[] a = agg.get(f.getFid());
            int cnt = (a == null) ? 0 : a[0];
            double avg = (a == null || a[0] == 0) ? 0.0
                    : Math.round((a[1] * 10.0 / a[0])) / 10.0;
            m.put("reviewCount", cnt);
            m.put("avgStar", avg);
            result.add(m);
        }
        return result;
    }
}