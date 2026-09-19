package com.example.sevice;

import com.example.web.vo.CreateOrderVo;
import com.example.web.vo.OrderVo;

import java.util.List;
import java.util.Map;

public interface OrderService {

    OrderVo createOrder(String userId, CreateOrderVo createOrderVo);

    List<OrderVo> listOrders(String userId);

    List<OrderVo> adminListOrders();

    OrderVo orderDetail(String roid);

    void confirmOrder(String roid);

    void cancelOrder(String roid);

    boolean checkPurchase(String userId, String fid);

    void updateAddress(String roid, String address);

    void refundOrder(String roid);

    void updateStatus(String roid, Integer status);

    String createAlipayPayForm(String roid);

    void handleAlipayNotify(Map<String, String> params);
}