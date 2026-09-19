package com.example.web.controller;

import com.example.document.FoodDocument;
import com.example.sevice.SearchService;
import com.example.web.vo.PageResult;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search")
@Slf4j
public class SearchController {

    @Autowired
    private SearchService searchService;

    @GetMapping("/food")
    public ResultVo search(@RequestParam(required = false) String keyword,
                           @RequestParam(required = false) String category,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "10") int size,
                           @RequestParam(required = false) String sort,
                           @RequestParam(defaultValue = "hybrid") String searchMode) {
        log.info("收到搜索请求: /search/food keyword={}, category={}, page={}, size={}, sort={}, mode={}",
                keyword, category, page, size, sort, searchMode);
        PageResult<FoodDocument> result = searchService.search(keyword, category, page, size, sort, searchMode);
        log.info("搜索完成: total={}, records={}", result.getTotal(), result.getRecords().size());
        return ResultVo.success(result);
    }

    @GetMapping("/suggest")
    public ResultVo suggest(@RequestParam String keyword) {
        log.info("收到联想请求: /search/suggest keyword={}", keyword);
        List<String> suggestions = searchService.suggest(keyword);
        log.info("联想完成: size={}, suggestions={}", suggestions.size(), suggestions);
        return ResultVo.success(suggestions);
    }
}