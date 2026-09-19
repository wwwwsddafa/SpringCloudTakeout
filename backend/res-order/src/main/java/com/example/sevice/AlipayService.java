package com.example.sevice;

import java.util.Map;

public interface AlipayService {

    String createPayForm(String roid);

    boolean verifyNotify(Map<String, String> params);

    void handlePaySuccess(String outTradeNo, String tradeNo, String totalAmount);
}