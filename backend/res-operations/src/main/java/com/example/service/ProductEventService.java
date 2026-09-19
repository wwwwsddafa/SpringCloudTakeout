package com.example.service;

import com.example.web.vo.ResultVo;

import java.math.BigDecimal;

public interface ProductEventService {

    ResultVo trackPv(String deviceType);

    ResultVo trackCartAdd(String fid);

    ResultVo trackPaidOrder(String fid, int count);

    ResultVo trackOrderCompleted(BigDecimal amount, String userId);

    ResultVo trackUserRegister();
}