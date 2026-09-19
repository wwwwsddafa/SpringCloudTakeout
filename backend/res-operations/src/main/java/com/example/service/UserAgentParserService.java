package com.example.service;

import com.example.web.vo.DeviceInfo;
import jakarta.servlet.http.HttpServletRequest;

public interface UserAgentParserService {

    DeviceInfo parse(HttpServletRequest request);
}