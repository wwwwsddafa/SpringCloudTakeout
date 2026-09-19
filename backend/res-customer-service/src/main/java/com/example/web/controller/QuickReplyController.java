package com.example.web.controller;

import com.example.bean.QuickReply;
import com.example.exceptions.BizException;
import com.example.service.QuickReplyService;
import com.example.web.vo.ResultCode;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/customer-service/quick-reply")
@Slf4j
public class QuickReplyController {

    @Autowired
    private QuickReplyService quickReplyService;

    @PostMapping
    public ResultVo create(@RequestHeader("X-User-Id") String creatorId,
                           @RequestBody Map<String, String> request) {
        String title = request.get("title");
        String content = request.get("content");
        String category = request.get("category");
        String skillGroup = request.get("skillGroup");
        if (title == null || title.isEmpty()) {
            throw new BizException(ResultCode.CS_QUICK_REPLY_TITLE_EMPTY);
        }
        if (content == null || content.isEmpty()) {
            throw new BizException(ResultCode.CS_QUICK_REPLY_CONTENT_EMPTY);
        }
        QuickReply qr = quickReplyService.create(title, content, category, skillGroup, creatorId);
        return ResultVo.success(qr);
    }

    @PutMapping("/{templateId}")
    public ResultVo update(@PathVariable String templateId,
                           @RequestBody Map<String, String> request) {
        quickReplyService.update(templateId,
                request.get("title"),
                request.get("content"),
                request.get("category"));
        return ResultVo.success("更新成功");
    }

    @DeleteMapping("/{templateId}")
    public ResultVo delete(@PathVariable String templateId) {
        quickReplyService.delete(templateId);
        return ResultVo.success("删除成功");
    }

    @PostMapping("/{templateId}/use")
    public ResultVo useTemplate(@PathVariable String templateId) {
        quickReplyService.incrementUsage(templateId);
        return ResultVo.success("使用计数已更新");
    }

    @GetMapping
    public ResultVo listAll(@RequestParam(required = false) String category,
                            @RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            return ResultVo.success(quickReplyService.search(keyword));
        }
        if (category != null && !category.isEmpty()) {
            return ResultVo.success(quickReplyService.listByCategory(category));
        }
        return ResultVo.success(quickReplyService.listAll());
    }
}