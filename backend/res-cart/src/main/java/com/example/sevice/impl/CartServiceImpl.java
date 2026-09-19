package com.example.sevice.impl;

import com.example.exceptions.BizException;
import com.example.sevice.CartService;
import com.example.web.vo.CartItemVo;
import com.example.web.vo.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CartServiceImpl implements CartService {

    private static final String CART_KEY_PREFIX = "cart:";
    private static final long CART_TTL_DAYS = 7;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void addItem(String userId, CartItemVo item) {
        String cartKey = CART_KEY_PREFIX + userId;
        String existingJson = (String) redisTemplate.opsForHash().get(cartKey, item.getFid());
        if (StringUtils.hasText(existingJson)) {
            try {
                CartItemVo existing = objectMapper.readValue(existingJson, CartItemVo.class);
                item.setNum(existing.getNum() + item.getNum());
            } catch (JsonProcessingException e) {
                log.warn("购物车项反序列化失败: {}", e.getMessage());
            }
        }
        try {
            String json = objectMapper.writeValueAsString(item);
            redisTemplate.opsForHash().put(cartKey, item.getFid(), json);
            redisTemplate.expire(cartKey, CART_TTL_DAYS, TimeUnit.DAYS);
            log.info("添加购物车: userId={}, fid={}, num={}", userId, item.getFid(), item.getNum());
        } catch (JsonProcessingException e) {
            log.error("购物车项序列化失败", e);
            throw new BizException(ResultCode.CART_ADD_FAIL);
        }
    }

    @Override
    public List<CartItemVo> listItems(String userId) {
        String cartKey = CART_KEY_PREFIX + userId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey);
        List<CartItemVo> items = new ArrayList<>();
        for (Object value : entries.values()) {
            try {
                CartItemVo item = objectMapper.readValue((String) value, CartItemVo.class);
                items.add(item);
            } catch (JsonProcessingException e) {
                log.warn("购物车项反序列化失败: {}", e.getMessage());
            }
        }
        return items;
    }

    @Override
    public void updateNum(String userId, String fid, Integer num) {
        String cartKey = CART_KEY_PREFIX + userId;
        String existingJson = (String) redisTemplate.opsForHash().get(cartKey, fid);
        if (!StringUtils.hasText(existingJson)) {
            throw new BizException(ResultCode.CART_ITEM_NOT_FOUND);
        }
        try {
            CartItemVo item = objectMapper.readValue(existingJson, CartItemVo.class);
            if (num <= 0) {
                redisTemplate.opsForHash().delete(cartKey, fid);
                log.info("移除购物车项: userId={}, fid={}", userId, fid);
            } else {
                item.setNum(num);
                String json = objectMapper.writeValueAsString(item);
                redisTemplate.opsForHash().put(cartKey, fid, json);
                log.info("更新购物车数量: userId={}, fid={}, num={}", userId, fid, num);
            }
        } catch (JsonProcessingException e) {
            log.error("购物车项处理失败", e);
            throw new BizException(ResultCode.CART_UPDATE_FAIL);
        }
    }

    @Override
    public void removeItem(String userId, String fid) {
        String cartKey = CART_KEY_PREFIX + userId;
        redisTemplate.opsForHash().delete(cartKey, fid);
        log.info("删除购物车项: userId={}, fid={}", userId, fid);
    }

    @Override
    public void clearCart(String userId) {
        String cartKey = CART_KEY_PREFIX + userId;
        redisTemplate.delete(cartKey);
        log.info("清空购物车: userId={}", userId);
    }
}