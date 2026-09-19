package com.example.sevice.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.api.OpsEventApi;
import com.example.bean.ResOrder;
import com.example.bean.ResOrderItem;
import com.example.config.AlipayProperties;
import com.example.dao.mapper.ResOrderItemMapper;
import com.example.dao.mapper.ResOrderMapper;
import com.example.exceptions.BizException;
import com.example.sevice.AlipayService;
import com.example.web.vo.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AlipayServiceImpl implements AlipayService {

    @Autowired
    private AlipayProperties alipayProperties;

    @Autowired
    private ResOrderMapper orderMapper;

    @Autowired
    private ResOrderItemMapper orderItemMapper;

    @Autowired
    private OpsEventApi opsEventApi;

    private AlipayClient alipayClient;

    private AlipayClient getAlipayClient() {
        if (alipayClient == null) {
            synchronized (this) {
                if (alipayClient == null) {
                    alipayClient = new DefaultAlipayClient(
                            alipayProperties.getGatewayUrl(),
                            alipayProperties.getAppId(),
                            alipayProperties.getPrivateKey(),
                            alipayProperties.getFormat(),
                            alipayProperties.getCharset(),
                            alipayProperties.getAlipayPublicKey(),
                            alipayProperties.getSignType());
                }
            }
        }
        return alipayClient;
    }

    @Override
    public String createPayForm(String roid) {
        ResOrder order = orderMapper.selectById(roid);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 0) {
            throw new BizException(ResultCode.ORDER_STATUS_ERROR);
        }

        String outTradeNo = order.getTradeno();
        BigDecimal payAmount = order.getPayAmount() != null ? order.getPayAmount() : order.getTotalAmount();

        // 沙箱不支持0元支付，最低0.01元
        if (payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            payAmount = new BigDecimal("0.01");
        }

        String subject = "外卖订单-" + outTradeNo;

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(alipayProperties.getNotifyUrl());
        request.setReturnUrl(alipayProperties.getReturnUrl() + "/" + roid);
        log.info("支付宝支付请求: roid={}, outTradeNo={}, amount={}, notifyUrl={}, returnUrl={}",
                roid, outTradeNo, payAmount, alipayProperties.getNotifyUrl(), request.getReturnUrl());

        String bizContent = "{"
                + "\"out_trade_no\":\"" + outTradeNo + "\","
                + "\"total_amount\":\"" + payAmount.toString() + "\","
                + "\"subject\":\"" + subject + "\","
                + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\""
                + "}";
        request.setBizContent(bizContent);

        try {
            String form = getAlipayClient().pageExecute(request).getBody();
            log.info("支付宝支付表单生成成功: roid={}, outTradeNo={}, amount={}", roid, outTradeNo, payAmount);
            return form;
        } catch (AlipayApiException e) {
            log.error("支付宝支付表单生成失败: roid={}, errCode={}, errMsg={}",
                    roid, e.getErrCode(), e.getErrMsg(), e);
            throw new BizException(ResultCode.ALIPAY_CREATE_PAY_FAILED);
        }
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        try {
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayProperties.getAlipayPublicKey(),
                    alipayProperties.getCharset(),
                    alipayProperties.getSignType());
            if (!signVerified) {
                log.warn("支付宝异步通知签名验证失败: params={}", params);
                return false;
            }
            log.info("支付宝异步通知签名验证成功: outTradeNo={}, tradeNo={}",
                    params.get("out_trade_no"), params.get("trade_no"));
            return true;
        } catch (AlipayApiException e) {
            log.error("支付宝异步通知签名验证异常", e);
            return false;
        }
    }

    @Transactional
    @Override
    public void handlePaySuccess(String outTradeNo, String tradeNo, String totalAmount) {
        ResOrder order = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ResOrder>()
                        .eq(ResOrder::getTradeno, outTradeNo));
        if (order == null) {
            log.error("支付宝支付成功但订单不存在: outTradeNo={}", outTradeNo);
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }

        if (order.getStatus() != 0) {
            log.warn("订单状态已变更，跳过重复支付处理: roid={}, status={}", order.getRoid(), order.getStatus());
            return;
        }

        UpdateWrapper<ResOrder> wrapper = new UpdateWrapper<>();
        wrapper.eq("roid", order.getRoid()).eq("status", 0);
        wrapper.set("status", 1)
                .set("paytime", LocalDateTime.now())
                .set("payment", "alipay");
        int rows = orderMapper.update(null, wrapper);
        if (rows > 0) {
            log.info("支付宝支付成功，订单状态已更新: roid={}, outTradeNo={}, tradeNo={}, amount={}",
                    order.getRoid(), outTradeNo, tradeNo, totalAmount);
            trackOpsEvent(order, totalAmount);
        }
    }

    private void trackOpsEvent(ResOrder order, String totalAmount) {
        try {
            List<ResOrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<ResOrderItem>().eq(ResOrderItem::getRoid, order.getRoid()));
            if (items != null) {
                for (ResOrderItem item : items) {
                    opsEventApi.trackPaidOrder(item.getFid(), item.getNum() != null ? item.getNum() : 1);
                }
            }
            BigDecimal amount = new BigDecimal(totalAmount);
            opsEventApi.trackOrderCompleted(amount, order.getUserid());
        } catch (Exception e) {
            log.error("同步运营统计数据失败: roid={}, error={}", order.getRoid(), e.getMessage());
        }
    }
}