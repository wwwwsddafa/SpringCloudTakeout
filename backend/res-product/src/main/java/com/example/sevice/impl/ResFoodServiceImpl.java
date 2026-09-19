package com.example.sevice.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.bean.ResFood;
import com.example.constants.RedisConstant;
import com.example.dao.mapper.ResFoodMapper;
import com.example.exceptions.BizException;
import com.example.sevice.ResFoodService;
import com.example.web.vo.FoodSyncMessage;
import com.example.web.vo.PageResult;
import com.example.web.vo.ResfoodVo;
import com.example.web.vo.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ResFoodServiceImpl implements ResFoodService {

    @Autowired
    private ResFoodMapper resFoodMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;    //这是jackson的objectMapper，用于序列化和反序列化  对象<->json字符串

    @Autowired
    private RabbitTemplate rabbitTemplate;


    @Override
    public List<ResfoodVo> listOnSale() {
        //redis中的在售商品的缓存数据(String)  json字符串
        String cached = redisTemplate.opsForValue().get(RedisConstant.FOOD_LIST_KEY);
        if (StringUtils.hasText(cached)) {
            try {
                //利用ObjectMapper反序列化json字符串为List<ResfoodVo>对象
                return objectMapper.readValue(cached, new TypeReference<List<ResfoodVo>>() {
                });
            } catch (Exception e) {
                log.warn("food cache deserialize failed: {}", e.getMessage());
                throw new BizException(ResultCode.FAIL);
            }
        }
        List<ResfoodVo> list = resFoodMapper.selectList(
                        new LambdaQueryWrapper<ResFood>()
                                .eq(ResFood::getStatus, 1)
                                .orderByAsc(ResFood::getFid))
                .stream()    // 这里从数据库中查询出的数据类型为 bean里的 Resfood对象
                .map(ResfoodVo::from)   //在这里取出每个Resfood对象，转换成 vo对象
                .toList();
        try {
            redisTemplate.opsForValue().set(RedisConstant.FOOD_LIST_KEY,   //redis中的键
                    objectMapper.writeValueAsString(list),    // redis中缓存的值( 序列化后的json字符串 )
                    RedisConstant.FOOD_CACHE_TTL, TimeUnit.SECONDS);  //缓存过期时间30分钟
        } catch (Exception e) {
            log.warn("food cache write failed: {}", e.getMessage());
            throw new BizException(ResultCode.FAIL);
        }
        return list;
    }

    @Override
    public ResFood getOnSaleDetail(String fid) {
        ResFood food = resFoodMapper.selectById(fid);
        if (food == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (food.getStatus() == null || food.getStatus() != 1) {
            throw new BizException(ResultCode.PRODUCT_OFF_SHELF);
        }
        enrichLikeCounts(food);
        return food;
    }

    @Override
    public ResFood getProductName(String fid) {
        return resFoodMapper.selectById(fid);
    }

    @Override
    public PageResult<ResFood> adminPage(int page, int size, String keyword) {
        LambdaQueryWrapper<ResFood> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ResFood::getFname, keyword);
        }
        wrapper.orderByAsc(ResFood::getFid);
        Page<ResFood> result = resFoodMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Transactional
    @Override
    public ResFood add(ResFood food) {
        if (food == null || !StringUtils.hasText(food.getFname())) {
            throw new BizException(ResultCode.PRODUCT_NAME_EMPTY);
        }
        if (food.getStatus() == null) {
            food.setStatus(1);
        }
        //food.setFid(null);   // 因为在controller中已经 通过 feign得到了 id.
        resFoodMapper.insert(food);
        evictFoodCache();
        sendFoodSyncMessage(FoodSyncMessage.ACTION_SAVE, food);
        log.info("管理员新增菜品: {}", food.toString());
        return food;
    }

    @Transactional
    @Override
    public ResFood update(ResFood food) {
        if (food == null || food.getFid() == null) {
            throw new BizException(ResultCode.PRODUCT_ID_EMPTY);
        }
        ResFood exist = resFoodMapper.selectById(food.getFid());
        if (exist == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        resFoodMapper.updateById(food);
        evictFoodCache();
        ResFood updated = resFoodMapper.selectById(food.getFid());
        sendFoodSyncMessage(FoodSyncMessage.ACTION_SAVE, updated);
        log.info("管理员修改菜品: ID={}, 修改后的菜品信息={}", food.getFid(), food.toString());
        return updated;
    }

    @Transactional
    @Override
    public void delete(String fid) {
        ResFood exist = resFoodMapper.selectById(fid);
        if (exist == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (exist.getStatus() != null && exist.getStatus() == 1) {
            throw new BizException(ResultCode.PRODUCT_DELETE_NOT_OFF_SHELF);
        }
        ResFood update = new ResFood();
        update.setFid(fid);
        update.setStatus(-1);
        resFoodMapper.updateById(update);
        evictFoodCache();
        sendFoodSyncMessage(FoodSyncMessage.ACTION_DELETE, exist);
        log.info("管理员删除菜品: ID={}, 名称={}", fid, exist.getFname());
    }

    @Transactional
    @Override
    public void changeStatus(String fid, Integer status) {
        ResFood exist = resFoodMapper.selectById(fid);
        if (exist == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        ResFood update = new ResFood();
        update.setFid(fid);
        update.setStatus(status);
        resFoodMapper.updateById(update);
        evictFoodCache();
        ResFood changed = resFoodMapper.selectById(fid);
        sendFoodSyncMessage(FoodSyncMessage.ACTION_SAVE, changed);
        log.info("管理员{}菜品: ID={}", status == 1 ? "上架" : "下架", fid);
    }

    @Override
    public void evictFoodCache() {
        redisTemplate.delete(RedisConstant.FOOD_LIST_KEY);
    }

    @Override
    public void syncAllToSearch() {
        List<ResFood> all = resFoodMapper.selectList(
                new LambdaQueryWrapper<ResFood>().orderByAsc(ResFood::getFid));
        for (ResFood food : all) {
            sendFoodSyncMessage(FoodSyncMessage.ACTION_SAVE, food);
        }
        log.info("ES索引重建完成，共发送 {} 条同步消息", all.size());
    }

    private void sendFoodSyncMessage(String action, ResFood food) {
        try {
            FoodSyncMessage message = FoodSyncMessage.builder()
                    .action(action)
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
            log.info("商品同步消息已发送: action={}, fid={}", action, food.getFid());
        } catch (Exception e) {
            log.error("发送商品同步消息失败: fid={}, error={}", food.getFid(), e.getMessage());
        }
    }

    private void enrichLikeCounts(ResFood food) {
        String likeCountStr = redisTemplate.opsForValue().get("like:count:" + food.getFid());
        String dislikeCountStr = redisTemplate.opsForValue().get("dislike:count:" + food.getFid());
        if (likeCountStr != null) {
            food.setLikeCount(Integer.parseInt(likeCountStr));
        }
        if (dislikeCountStr != null) {
            food.setDislikeCount(Integer.parseInt(dislikeCountStr));
        }
    }
}