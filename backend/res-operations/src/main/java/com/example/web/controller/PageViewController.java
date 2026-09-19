package com.example.web.controller;

import com.example.service.PageViewService;
import com.example.web.vo.PageViewVo;
import com.example.web.vo.ResultVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class PageViewController {

    @Autowired
    private PageViewService pageViewService;

    @PostMapping("/ops/pv/track")
    public ResultVo track(HttpServletRequest request, @RequestBody PageViewVo vo) {
        return pageViewService.track(request, vo);
    }
}