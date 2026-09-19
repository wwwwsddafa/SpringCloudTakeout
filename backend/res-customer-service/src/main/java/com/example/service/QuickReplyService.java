package com.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bean.QuickReply;
import com.example.dao.mapper.QuickReplyMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class QuickReplyService {

    @Autowired
    private QuickReplyMapper quickReplyMapper;

    public QuickReply create(String title, String content, String category,
                             String skillGroup, String creatorId) {
        QuickReply qr = new QuickReply();
        qr.setTemplateId("QR" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        qr.setTitle(title);
        qr.setContent(content);
        qr.setCategory(category);
        qr.setSkillGroup(skillGroup);
        qr.setIsShared(1);
        qr.setUsageCount(0);
        qr.setCreatorId(creatorId);
        qr.setCreateTime(LocalDateTime.now());
        qr.setUpdateTime(LocalDateTime.now());
        quickReplyMapper.insert(qr);
        log.info("快捷回复创建: templateId={}, title={}", qr.getTemplateId(), title);
        return qr;
    }

    public void update(String templateId, String title, String content, String category) {
        QuickReply qr = quickReplyMapper.selectById(templateId);
        if (qr == null) {
            throw new RuntimeException("快捷回复模板不存在: " + templateId);
        }
        if (title != null) qr.setTitle(title);
        if (content != null) qr.setContent(content);
        if (category != null) qr.setCategory(category);
        qr.setUpdateTime(LocalDateTime.now());
        quickReplyMapper.updateById(qr);
    }

    public void delete(String templateId) {
        quickReplyMapper.deleteById(templateId);
    }

    public void incrementUsage(String templateId) {
        QuickReply qr = quickReplyMapper.selectById(templateId);
        if (qr != null) {
            qr.setUsageCount(qr.getUsageCount() != null ? qr.getUsageCount() + 1 : 1);
            quickReplyMapper.updateById(qr);
        }
    }

    public List<QuickReply> listAll() {
        return quickReplyMapper.selectList(
                new LambdaQueryWrapper<QuickReply>()
                        .orderByDesc(QuickReply::getUsageCount));
    }

    public List<QuickReply> listByCategory(String category) {
        return quickReplyMapper.selectList(
                new LambdaQueryWrapper<QuickReply>()
                        .eq(QuickReply::getCategory, category)
                        .orderByDesc(QuickReply::getUsageCount));
    }

    public List<QuickReply> search(String keyword) {
        return quickReplyMapper.selectList(
                new LambdaQueryWrapper<QuickReply>()
                        .and(w -> w.like(QuickReply::getTitle, keyword)
                                .or().like(QuickReply::getContent, keyword))
                        .orderByDesc(QuickReply::getUsageCount));
    }
}