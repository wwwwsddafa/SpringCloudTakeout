package com.example.sevice.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.api.OrderApi;
import com.example.bean.ResFood;
import com.example.bean.ResProductLike;
import com.example.dao.mapper.ResFoodMapper;
import com.example.dao.mapper.ResProductLikeMapper;
import com.example.exceptions.BizException;
import com.example.sevice.ProductLikeService;
import com.example.sevice.ResFoodService;
import com.example.web.vo.FoodSyncMessage;
import com.example.web.vo.ResultCode;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ProductLikeServiceImpl implements ProductLikeService {

    private static final String LIKE_COUNT_KEY_PREFIX = "like:count:";
    private static final String DISLIKE_COUNT_KEY_PREFIX = "dislike:count:";
    private static final String PURCHASE_CHECK_PREFIX = "purchase:check:";
    private static final long PURCHASE_CHECK_TTL_MINUTES = 5;
    private static final int REDIS_TTL_HOURS = 48;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private ResProductLikeMapper productLikeMapper;

    @Autowired
    private ResFoodMapper resFoodMapper;

    @Autowired
    private ResFoodService resFoodService;

    @Autowired
    private OrderApi orderApi;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public ResultVo like(String userId, String fid, Integer likeType) {
        if (likeType == null || (likeType != 1 && likeType != -1)) {
            throw new BizException(ResultCode.LIKE_TYPE_INVALID);
        }

        ResFood food = resFoodMapper.selectById(fid);
        if (food == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }

        if (!checkPurchaseCached(userId, fid)) {
            throw new BizException(ResultCode.NOT_PURCHASED);
        }

        ResProductLike existing = productLikeMapper.selectOne(
                new LambdaQueryWrapper<ResProductLike>()
                        .eq(ResProductLike::getUserId, userId)
                        .eq(ResProductLike::getFid, fid));

        if (existing != null) {
            Integer oldType = existing.getLikeType();
            if (oldType != null && oldType.equals(likeType)) {
                existing.setLikeType(0);
                existing.setCreateTime(LocalDateTime.now());
                productLikeMapper.updateById(existing);
                if (likeType == 1) {
                    redisTemplate.opsForValue().decrement(LIKE_COUNT_KEY_PREFIX + fid);
                    updateDailyLikeCount(fid, 1, -1);
                } else if (likeType == -1) {
                    redisTemplate.opsForValue().decrement(DISLIKE_COUNT_KEY_PREFIX + fid);
                    updateDailyLikeCount(fid, -1, -1);
                }
                refreshRedisTtl(fid);
                syncLikeCountsToDb(fid);
                evictAndSync(fid);
                log.info("用户取消评价: userId={}, fid={}, likeType={}", userId, fid, likeType);
                return buildResult(fid, likeType == 1 ? "已取消赞" : "已取消踩");
            }

            if (oldType != null && oldType == 0) {
                existing.setLikeType(likeType);
                existing.setCreateTime(LocalDateTime.now());
                productLikeMapper.updateById(existing);
                if (likeType == 1) {
                    redisTemplate.opsForValue().increment(LIKE_COUNT_KEY_PREFIX + fid);
                    updateDailyLikeCount(fid, 1, 1);
                } else if (likeType == -1) {
                    redisTemplate.opsForValue().increment(DISLIKE_COUNT_KEY_PREFIX + fid);
                    updateDailyLikeCount(fid, -1, 1);
                }
                refreshRedisTtl(fid);
                syncLikeCountsToDb(fid);
                evictAndSync(fid);
                log.info("用户重新评价: userId={}, fid={}, likeType={}", userId, fid, likeType);
                return buildResult(fid, likeType == 1 ? "赞" : "踩");
            }

            existing.setLikeType(likeType);
            existing.setCreateTime(LocalDateTime.now());
            productLikeMapper.updateById(existing);
            if (likeType == 1) {
                redisTemplate.opsForValue().increment(LIKE_COUNT_KEY_PREFIX + fid);
                redisTemplate.opsForValue().decrement(DISLIKE_COUNT_KEY_PREFIX + fid);
                updateDailyLikeCount(fid, 1, 1);
                updateDailyLikeCount(fid, -1, -1);
            } else if (likeType == -1) {
                redisTemplate.opsForValue().increment(DISLIKE_COUNT_KEY_PREFIX + fid);
                redisTemplate.opsForValue().decrement(LIKE_COUNT_KEY_PREFIX + fid);
                updateDailyLikeCount(fid, -1, 1);
                updateDailyLikeCount(fid, 1, -1);
            }
            refreshRedisTtl(fid);
            syncLikeCountsToDb(fid);
            evictAndSync(fid);
            return buildResult(fid, likeType == 1 ? "已切换为赞" : "已切换为踩");
        }

        ResProductLike like = new ResProductLike();
        like.setId(UUID.randomUUID().toString().replace("-", ""));
        like.setFid(fid);
        like.setUserId(userId);
        like.setLikeType(likeType);
        like.setCreateTime(LocalDateTime.now());
        productLikeMapper.insert(like);

        if (likeType == 1) {
            redisTemplate.opsForValue().increment(LIKE_COUNT_KEY_PREFIX + fid);
            updateDailyLikeCount(fid, 1, 1);
        } else if (likeType == -1) {
            redisTemplate.opsForValue().increment(DISLIKE_COUNT_KEY_PREFIX + fid);
            updateDailyLikeCount(fid, -1, 1);
        }

        refreshRedisTtl(fid);
        syncLikeCountsToDb(fid);
        evictAndSync(fid);
        log.info("用户评价商品: userId={}, fid={}, likeType={}", userId, fid, likeType);
        return buildResult(fid, likeType == 1 ? "赞" : "踩");
    }

    @Override
    public ResultVo getLikeStatus(String userId, String fid) {
        ResProductLike like = productLikeMapper.selectOne(
                new LambdaQueryWrapper<ResProductLike>()
                        .eq(ResProductLike::getUserId, userId)
                        .eq(ResProductLike::getFid, fid));
        if (like == null || like.getLikeType() == null || like.getLikeType() == 0) {
            return ResultVo.success(null);
        }
        return ResultVo.success(like.getLikeType());
    }

    @Override
    public ResultVo getLikeCount(String fid) {
        String likeCountStr = redisTemplate.opsForValue().get(LIKE_COUNT_KEY_PREFIX + fid);
        String dislikeCountStr = redisTemplate.opsForValue().get(DISLIKE_COUNT_KEY_PREFIX + fid);
        int likeCount = likeCountStr != null ? Integer.parseInt(likeCountStr) : 0;
        int dislikeCount = dislikeCountStr != null ? Integer.parseInt(dislikeCountStr) : 0;
        if (likeCount == 0 && dislikeCount == 0) {
            ResFood food = resFoodMapper.selectById(fid);
            if (food != null) {
                likeCount = food.getLikeCount() != null ? food.getLikeCount() : 0;
                dislikeCount = food.getDislikeCount() != null ? food.getDislikeCount() : 0;
            }
        }
        return ResultVo.success(new int[]{likeCount, dislikeCount});
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

    private void syncLikeCountsToDb(String fid) {
        String likeCountStr = redisTemplate.opsForValue().get(LIKE_COUNT_KEY_PREFIX + fid);
        String dislikeCountStr = redisTemplate.opsForValue().get(DISLIKE_COUNT_KEY_PREFIX + fid);
        int likeCount = likeCountStr != null ? Integer.parseInt(likeCountStr) : 0;
        int dislikeCount = dislikeCountStr != null ? Integer.parseInt(dislikeCountStr) : 0;
        resFoodMapper.update(null,
                new LambdaUpdateWrapper<ResFood>()
                        .eq(ResFood::getFid, fid)
                        .set(ResFood::getLikeCount, likeCount)
                        .set(ResFood::getDislikeCount, dislikeCount));
        log.debug("点赞数同步到DB: fid={}, likeCount={}, dislikeCount={}", fid, likeCount, dislikeCount);
    }

    private void refreshRedisTtl(String fid) {
        redisTemplate.expire(LIKE_COUNT_KEY_PREFIX + fid, 7, TimeUnit.DAYS);
        redisTemplate.expire(DISLIKE_COUNT_KEY_PREFIX + fid, 7, TimeUnit.DAYS);
    }

    private void evictAndSync(String fid) {
        resFoodService.evictFoodCache();
        ResFood food = resFoodMapper.selectById(fid);
        if (food != null) {
            sendFoodSyncMessage(food);
        }
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void scheduledSyncLikeCounts() {
        log.info("定时任务：开始同步Redis点赞数到DB");
        Set<String> likeKeys = redisTemplate.keys(LIKE_COUNT_KEY_PREFIX + "*");
        if (likeKeys == null || likeKeys.isEmpty()) {
            return;
        }
        for (String key : likeKeys) {
            try {
                String fid = key.substring(LIKE_COUNT_KEY_PREFIX.length());
                syncLikeCountsToDb(fid);
                ResFood food = resFoodMapper.selectById(fid);
                if (food != null) {
                    sendFoodSyncMessage(food);
                }
            } catch (Exception e) {
                log.error("定时同步点赞数失败: key={}, error={}", key, e.getMessage());
            }
        }
        log.info("定时任务：完成同步 {} 个商品的点赞数", likeKeys.size());
    }

    private ResultVo buildResult(String fid, String statusMsg) {
        String likeCountStr = redisTemplate.opsForValue().get(LIKE_COUNT_KEY_PREFIX + fid);
        String dislikeCountStr = redisTemplate.opsForValue().get(DISLIKE_COUNT_KEY_PREFIX + fid);
        int likeCount = likeCountStr != null ? Integer.parseInt(likeCountStr) : 0;
        int dislikeCount = dislikeCountStr != null ? Integer.parseInt(dislikeCountStr) : 0;
        Map<String, Object> result = new HashMap<>();
        result.put("status", statusMsg);
        result.put("likeCount", likeCount);
        result.put("dislikeCount", dislikeCount);
        return ResultVo.success(result);
    }

    // ② 当日口径：写入当日 Redis 点赞/踩计数器（key 对齐 ops:like:daily / ops:dislike:daily）
    private void updateDailyLikeCount(String fid, int likeType, int delta) {
        String today = LocalDate.now().format(DATE_FMT);
        if (likeType == 1) {
            String key = "ops:like:daily:" + today + ":" + fid;
            redisTemplate.opsForValue().increment(key, delta);
            redisTemplate.expire(key, REDIS_TTL_HOURS, TimeUnit.HOURS);
        } else if (likeType == -1) {
            String key = "ops:dislike:daily:" + today + ":" + fid;
            redisTemplate.opsForValue().increment(key, delta);
            redisTemplate.expire(key, REDIS_TTL_HOURS, TimeUnit.HOURS);
        }
    }

    private void sendFoodSyncMessage(ResFood food) {
        try {
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
            log.error("发送商品同步消息失败: fid={}, error={}", food.getFid(), e.getMessage());
        }
    }
}