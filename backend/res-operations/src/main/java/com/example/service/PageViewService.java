package com.example.service;

import com.example.web.vo.PageViewVo;
import com.example.web.vo.ResultVo;
import jakarta.servlet.http.HttpServletRequest;

public interface PageViewService {

    ResultVo track(HttpServletRequest request, PageViewVo vo);
}