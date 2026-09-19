package com.example.sevice.impl;

import com.example.api.CartApi;
import com.example.api.IdGeneratorApi;
import com.example.api.OpsEventApi;
import com.example.api.UserServiceApi;
import com.example.bean.ResOrder;
import com.example.bean.ResOrderItem;
import com.example.dao.mapper.ResOrderItemMapper;
import com.example.dao.mapper.ResOrderMapper;
import com.example.sevice.OrderService;
import com.example.sevice.RedisLockService;
import com.example.sevice.FreeOrderService;
import com.example.sevice.AlipayService;
import com.example.exceptions.BizException;
import com.example.web.vo.CreateOrderVo;
import com.example.web.vo.EmailMessage;
import com.example.web.vo.OrderItemVo;
import com.example.web.vo.OrderVo;
import com.example.web.vo.ResultCode;
import com.example.web.vo.ResultVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private ResOrderMapper orderMapper;

    @Autowired
    private ResOrderItemMapper orderItemMapper;

    @Autowired
    private IdGeneratorApi idGeneratorApi;

    @Autowired
    private CartApi cartApi;

    @Autowired
    private UserServiceApi userServiceApi;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisLockService redisLockService;

    @Autowired
    private FreeOrderService freeOrderService;

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private OpsEventApi opsEventApi;

    @Autowired
    private HttpServletRequest request;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Transactional
    @Override
    public OrderVo createOrder(String userId, CreateOrderVo createOrderVo) {
        if (!redisLockService.tryLock(userId)) {
            throw new BizException(ResultCode.ORDER_DUPLICATE);
        }
        try {
            return doCreateOrder(userId, createOrderVo);
        } finally {
            redisLockService.unlock(userId);
        }
    }

    private OrderVo doCreateOrder(String userId, CreateOrderVo createOrderVo) {
        ResultVo cartResult = cartApi.listItems(userId);
        if (cartResult == null || cartResult.getData() == null) {
            throw new BizException(ResultCode.CART_EMPTY);
        }
        List<LinkedHashMap> cartData = (List<LinkedHashMap>) cartResult.getData();
        if (cartData.isEmpty()) {
            throw new BizException(ResultCode.CART_EMPTY);
        }

        List<OrderItemVo> orderItems = new ArrayList<>();
        BigDecimal serverOriginal = BigDecimal.ZERO;
        for (LinkedHashMap item : cartData) {
            OrderItemVo oi = new OrderItemVo();
            oi.setFid((String) item.get("fid"));
            oi.setFname((String) item.get("fname"));
            Object priceObj = item.get("realprice");
            BigDecimal price = priceObj instanceof BigDecimal
                    ? (BigDecimal) priceObj
                    : BigDecimal.valueOf(((Number) priceObj).doubleValue());
            oi.setDealprice(price);
            Integer num = item.get("num") instanceof Integer
                    ? (Integer) item.get("num")
                    : ((Number) item.get("num")).intValue();
            oi.setNum(num);
            orderItems.add(oi);
            serverOriginal = serverOriginal.add(price.multiply(BigDecimal.valueOf(num)));
        }

        BigDecimal frontendOriginal = createOrderVo.getOriginalAmount();
        BigDecimal frontendExpect = createOrderVo.getExpectAmount();
        if (frontendOriginal == null || frontendExpect == null) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        if (serverOriginal.compareTo(frontendOriginal) != 0) {
            throw new BizException(ResultCode.PRICE_CHANGED);
        }

        BigDecimal serverDiscount = BigDecimal.ZERO;
        BigDecimal serverFinal;
        String freeOrderNo = createOrderVo.getFreeOrderNo();
        if (freeOrderNo != null && !freeOrderNo.isEmpty()) {
            serverDiscount = freeOrderService.validateCoupon(freeOrderNo, userId, serverOriginal);
            serverFinal = serverOriginal.subtract(serverDiscount);
            if (serverFinal.compareTo(frontendExpect) != 0) {
                throw new BizException(ResultCode.AMOUNT_MISMATCH);
            }
        } else {
            serverFinal = serverOriginal;
            if (serverFinal.compareTo(frontendExpect) != 0) {
                throw new BizException(ResultCode.AMOUNT_MISMATCH);
            }
        }

        String roid = generateOrderId();
        String tradeno = "ORD" + System.currentTimeMillis();

        ResOrder order = new ResOrder();
        order.setRoid(roid);
        order.setUserid(userId);
        order.setUname("用户_" + userId.substring(0, Math.min(8, userId.length())));
        order.setAddress(createOrderVo.getAddress());
        order.setTel(createOrderVo.getTel());
        order.setOrderTime(LocalDateTime.now());
        order.setDeliveryType(
                createOrderVo.getDeliveryType() != null ? createOrderVo.getDeliveryType() : "now");
        order.setPayment(
                createOrderVo.getPayment() != null ? createOrderVo.getPayment() : "alipay");
        order.setPs(createOrderVo.getPs());
        order.setStatus(0);
        order.setTradeno(tradeno);
        order.setTotalAmount(serverOriginal);
        order.setDiscountAmount(serverDiscount);
        order.setPayAmount(serverFinal);
        orderMapper.insert(order);

        if (freeOrderNo != null && !freeOrderNo.isEmpty()) {
            freeOrderService.useCoupon(freeOrderNo, userId, roid, serverOriginal);
            log.info("免单券核销成功: freeOrderNo={}, roid={}, 减免={}, 实付={}",
                    freeOrderNo, roid, serverDiscount, serverFinal);
        }

        for (OrderItemVo oi : orderItems) {
            ResOrderItem item = new ResOrderItem();
            item.setRiid(UUID.randomUUID().toString().replace("-", ""));
            item.setRoid(roid);
            item.setFid(oi.getFid());
            item.setFname(oi.getFname());
            item.setDealprice(oi.getDealprice());
            item.setNum(oi.getNum());
            orderItemMapper.insert(item);
        }

        cartApi.clearCart(userId);

        log.info("订单创建成功: roid={}, 原价={}, 减免={}, 实付={}, 商品数={}",
                roid, serverOriginal, serverDiscount, serverFinal, orderItems.size());

        sendOrderEmail(EmailMessage.TYPE_ORDER, userId, order, serverFinal);

        return buildOrderVo(order, orderItems);
    }

    @Override
    public List<OrderVo> listOrders(String userId) {
        List<ResOrder> orders = orderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ResOrder>()
                        .eq(ResOrder::getUserid, userId)
                        .orderByDesc(ResOrder::getOrderTime));
        if (orders.isEmpty()) {
            return List.of();
        }
        List<String> roids = orders.stream()
                .map(ResOrder::getRoid)
                .collect(Collectors.toList());
        List<ResOrderItem> allItems = orderItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ResOrderItem>()
                        .in(ResOrderItem::getRoid, roids));
        java.util.Map<String, List<ResOrderItem>> itemMap = allItems.stream()
                .collect(Collectors.groupingBy(ResOrderItem::getRoid));
        return orders.stream().map(order -> {
            List<ResOrderItem> items = itemMap.getOrDefault(order.getRoid(), List.of());
            List<OrderItemVo> itemVos = items.stream().map(it -> {
                OrderItemVo vo = new OrderItemVo();
                BeanUtils.copyProperties(it, vo);
                return vo;
            }).collect(Collectors.toList());
            return buildOrderVo(order, itemVos);
        }).collect(Collectors.toList());
    }

    @Override
    public List<OrderVo> adminListOrders() {
        List<ResOrder> orders = orderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ResOrder>()
                        .orderByDesc(ResOrder::getOrderTime));
        if (orders.isEmpty()) {
            return List.of();
        }
        List<String> roids = orders.stream()
                .map(ResOrder::getRoid)
                .collect(Collectors.toList());
        List<ResOrderItem> allItems = orderItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ResOrderItem>()
                        .in(ResOrderItem::getRoid, roids));
        java.util.Map<String, List<ResOrderItem>> itemMap = allItems.stream()
                .collect(Collectors.groupingBy(ResOrderItem::getRoid));
        return orders.stream().map(order -> {
            List<ResOrderItem> items = itemMap.getOrDefault(order.getRoid(), List.of());
            List<OrderItemVo> itemVos = items.stream().map(it -> {
                OrderItemVo vo = new OrderItemVo();
                BeanUtils.copyProperties(it, vo);
                return vo;
            }).collect(Collectors.toList());
            return buildOrderVo(order, itemVos);
        }).collect(Collectors.toList());
    }

    @Override
    public OrderVo orderDetail(String roid) {
        ResOrder order = orderMapper.selectById(roid);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        List<ResOrderItem> items = orderItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ResOrderItem>()
                        .eq(ResOrderItem::getRoid, roid));
        List<OrderItemVo> itemVos = items.stream().map(it -> {
            OrderItemVo vo = new OrderItemVo();
            BeanUtils.copyProperties(it, vo);
            return vo;
        }).collect(Collectors.toList());
        return buildOrderVo(order, itemVos);
    }

    @Transactional
    @Override
    public void confirmOrder(String roid) {
        ResOrder order = orderMapper.selectById(roid);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        UpdateWrapper<ResOrder> wrapper = new UpdateWrapper<>();
        wrapper.eq("roid", roid).eq("status", 0);
        wrapper.set("status", 1).set("paytime", LocalDateTime.now());
        int rows = orderMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BizException(ResultCode.ORDER_STATUS_ERROR);
        }
        log.info("订单确认支付: roid={}", roid);

        // 同步运营统计数据
        trackOpsEvent(order);

        // 发送支付成功邮件
        order.setStatus(1);
        order.setPayTime(LocalDateTime.now());
        sendOrderEmail(EmailMessage.TYPE_PAYMENT, order.getUserid(), order, order.getPayAmount());
    }

    private void trackOpsEvent(ResOrder order) {
        try {
            List<ResOrderItem> items = orderItemMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ResOrderItem>()
                            .eq(ResOrderItem::getRoid, order.getRoid()));
            if (items != null) {
                for (ResOrderItem item : items) {
                    opsEventApi.trackPaidOrder(item.getFid(), item.getNum() != null ? item.getNum() : 1);
                }
            }
            BigDecimal amount = order.getPayAmount() != null ? order.getPayAmount() : order.getTotalAmount();
            if (amount == null) {
                amount = BigDecimal.ZERO;
            }
            opsEventApi.trackOrderCompleted(amount, order.getUserid());
        } catch (Exception e) {
            log.error("同步运营统计数据失败: roid={}, error={}", order.getRoid(), e.getMessage());
        }
    }

    @Transactional
    @Override
    public void cancelOrder(String roid) {
        UpdateWrapper<ResOrder> wrapper = new UpdateWrapper<>();
        wrapper.eq("roid", roid).eq("status", 0);
        wrapper.set("status", 3).set("cancel_time", LocalDateTime.now());
        int rows = orderMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BizException(ResultCode.ORDER_CANCEL_NOT_ALLOWED);
        }
        log.info("订单已取消: roid={}", roid);
    }

    @Transactional
    @Override
    public void updateAddress(String roid, String address) {
        ResOrder order = orderMapper.selectById(roid);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() == 3) {
            throw new BizException(ResultCode.ORDER_ADDRESS_NOT_ALLOWED);
        }
        if (order.getStatus() == 4) {
            throw new BizException(ResultCode.ORDER_ADDRESS_NOT_ALLOWED);
        }
        if (order.getStatus() == 2) {
            throw new BizException(ResultCode.ORDER_ADDRESS_NOT_ALLOWED);
        }
        UpdateWrapper<ResOrder> wrapper = new UpdateWrapper<>();
        wrapper.eq("roid", roid);
        wrapper.set("address", address);
        orderMapper.update(null, wrapper);
        log.info("订单地址已修改: roid={}, newAddress={}", roid, address);
    }

    @Transactional
    @Override
    public void refundOrder(String roid) {
        ResOrder order = orderMapper.selectById(roid);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 1 && order.getStatus() != 2) {
            throw new BizException(ResultCode.ORDER_REFUND_NOT_ALLOWED);
        }
        UpdateWrapper<ResOrder> wrapper = new UpdateWrapper<>();
        wrapper.eq("roid", roid);
        wrapper.set("status", 4).set("cancel_time", LocalDateTime.now());
        orderMapper.update(null, wrapper);
        log.info("订单已退单: roid={}", roid);
    }

    @Transactional
    @Override
    public void updateStatus(String roid, Integer status) {
        ResOrder order = orderMapper.selectById(roid);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (status < 0 || status > 4) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        UpdateWrapper<ResOrder> wrapper = new UpdateWrapper<>();
        wrapper.eq("roid", roid);
        wrapper.set("status", status);
        if (status == 3 || status == 4) {
            wrapper.set("cancel_time", LocalDateTime.now());
        }
        orderMapper.update(null, wrapper);
        log.info("管理员更新订单状态: roid={}, oldStatus={}, newStatus={}", roid, order.getStatus(), status);
    }

    private String generateOrderId() {
        try {
            ResultVo result = idGeneratorApi.next();
            if (result != null && result.getData() != null) {
                return result.getData().toString();
            }
        } catch (Exception e) {
            log.warn("ID生成服务调用失败，使用UUID作为订单号: {}", e.getMessage());
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void sendOrderEmail(String type, String userId, ResOrder order, BigDecimal totalPrice) {
        try {
            String email = null;
            String username = null;
            try {
                ResultVo userResult = userServiceApi.getUserInfoById(userId);
                if (userResult != null && userResult.getData() != null) {
                    Map<String, Object> userData = (Map<String, Object>) userResult.getData();
                    email = (String) userData.get("email");
                    username = (String) userData.get("username");
                }
            } catch (Exception e) {
                log.warn("获取用户信息失败: userId={}", userId, e);
            }

            if (email == null) {
                log.warn("用户邮箱为空，跳过邮件发送: userId={}", userId);
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("roid", order.getRoid());
            params.put("totalPrice", totalPrice.toString());
            params.put("address", order.getAddress());
            params.put("payment", order.getPayment());
            if (order.getTradeno() != null) {
                params.put("tradeno", order.getTradeno());
            }
            if (order.getPayTime() != null) {
                params.put("payTime", order.getPayTime().format(FORMATTER));
            }

            EmailMessage message = EmailMessage.builder()
                    .type(type)
                    .userId(userId)
                    .email(email)
                    .username(username != null ? username : order.getUname())
                    .params(params)
                    .build();
            rabbitTemplate.convertAndSend(EmailMessage.EMAIL_EXCHANGE, EmailMessage.EMAIL_ROUTING_KEY, message);
            log.info("订单邮件消息已发送: type={}, roid={}, email={}", type, order.getRoid(), email);
        } catch (Exception e) {
            log.error("发送订单邮件消息失败: type={}, roid={}", type, order.getRoid(), e);
        }
    }

    private OrderVo buildOrderVo(ResOrder order, List<OrderItemVo> items) {
        return OrderVo.builder()
                .roid(order.getRoid())
                .userid(order.getUserid())
                .uname(order.getUname())
                .address(order.getAddress())
                .tel(order.getTel())
                .orderTime(order.getOrderTime())
                .deliveryType(order.getDeliveryType())
                .payment(order.getPayment())
                .ps(order.getPs())
                .status(order.getStatus())
                .tradeno(order.getTradeno())
                .payTime(order.getPayTime())
                .cancelTime(order.getCancelTime())
                .items(items)
                .totalAmount(order.getTotalAmount())
                .discountAmount(order.getDiscountAmount())
                .payAmount(order.getPayAmount())
                .build();
    }

    @Override
    public boolean checkPurchase(String userId, String fid) {
        List<ResOrder> orders = orderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ResOrder>()
                        .eq(ResOrder::getUserid, userId)
                        .in(ResOrder::getStatus, 1, 2));
        if (orders.isEmpty()) {
            return false;
        }
        List<String> roids = orders.stream()
                .map(ResOrder::getRoid)
                .collect(Collectors.toList());
        Long count = orderItemMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ResOrderItem>()
                        .in(ResOrderItem::getRoid, roids)
                        .eq(ResOrderItem::getFid, fid));
        return count != null && count > 0;
    }

    @Override
    public String createAlipayPayForm(String roid) {
        return alipayService.createPayForm(roid);
    }

    @Override
    public void handleAlipayNotify(Map<String, String> params) {
        boolean verified = alipayService.verifyNotify(params);
        if (!verified) {
            throw new BizException(ResultCode.ALIPAY_NOTIFY_VERIFY_FAILED);
        }

        String tradeStatus = params.get("trade_status");
        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String totalAmount = params.get("total_amount");

        log.info("支付宝异步通知: tradeStatus={}, outTradeNo={}, tradeNo={}, amount={}",
                tradeStatus, outTradeNo, tradeNo, totalAmount);

        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            alipayService.handlePaySuccess(outTradeNo, tradeNo, totalAmount);
        }
    }
}