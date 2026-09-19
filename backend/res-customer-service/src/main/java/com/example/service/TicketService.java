package com.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.bean.Ticket;
import com.example.dao.mapper.TicketMapper;
import com.example.ticket.ResolutionType;
import com.example.ticket.TicketCategory;
import com.example.ticket.TicketPriority;
import com.example.ticket.TicketStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class TicketService {

    @Autowired
    private TicketMapper ticketMapper;

    public Ticket createTicket(String sessionId, String userId, String userName,
                               String category, String priority, String title,
                               String description, String source) {
        Ticket ticket = new Ticket();
        ticket.setTicketId("TK" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        ticket.setSessionId(sessionId);
        ticket.setUserId(userId);
        ticket.setUserName(userName);
        ticket.setCategory(category != null ? category : TicketCategory.OTHER.name());
        ticket.setPriority(priority != null ? priority : TicketPriority.MEDIUM.name());
        ticket.setStatus(TicketStatus.PENDING.name());
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setSource(source);
        ticket.setCreateTime(LocalDateTime.now());

        if (TicketPriority.URGENT.name().equals(priority)) {
            ticket.setSlaDeadline(LocalDateTime.now().plusMinutes(15));
        } else if (TicketPriority.HIGH.name().equals(priority)) {
            ticket.setSlaDeadline(LocalDateTime.now().plusHours(1));
        } else {
            ticket.setSlaDeadline(LocalDateTime.now().plusHours(4));
        }

        ticketMapper.insert(ticket);
        log.info("工单创建: ticketId={}, userId={}, category={}, priority={}",
                ticket.getTicketId(), userId, category, priority);
        return ticket;
    }

    public void assignTicket(String ticketId, String agentId) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("工单不存在: " + ticketId);
        }
        ticket.setAgentId(agentId);
        ticket.setStatus(TicketStatus.PROCESSING.name());
        ticket.setAssignTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        log.info("工单分配: ticketId={}, agentId={}", ticketId, agentId);
    }

    public void updateStatus(String ticketId, TicketStatus status) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("工单不存在: " + ticketId);
        }
        ticket.setStatus(status.name());
        if (status == TicketStatus.RESOLVED) {
            ticket.setResolveTime(LocalDateTime.now());
        } else if (status == TicketStatus.CLOSED) {
            ticket.setCloseTime(LocalDateTime.now());
        }
        ticketMapper.updateById(ticket);
        log.info("工单状态变更: ticketId={}, status={}", ticketId, status);
    }

    public void resolveTicket(String ticketId, String resolution, ResolutionType resolutionType) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("工单不存在: " + ticketId);
        }
        ticket.setStatus(TicketStatus.RESOLVED.name());
        ticket.setResolution(resolution);
        ticket.setResolutionType(resolutionType.name());
        ticket.setResolveTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        log.info("工单解决: ticketId={}, resolutionType={}", ticketId, resolutionType);
    }

    public void rateTicket(String ticketId, int rating, String comment) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("工单不存在: " + ticketId);
        }
        ticket.setRating(rating);
        ticket.setRatingComment(comment);
        ticketMapper.updateById(ticket);
        log.info("工单评价: ticketId={}, rating={}", ticketId, rating);
    }

    public void updateCategory(String ticketId, String category, String priority) {
        LambdaUpdateWrapper<Ticket> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Ticket::getTicketId, ticketId);
        if (category != null) {
            wrapper.set(Ticket::getCategory, category);
        }
        if (priority != null) {
            wrapper.set(Ticket::getPriority, priority);
        }
        ticketMapper.update(wrapper);
    }

    public void updateTags(String ticketId, String tags) {
        LambdaUpdateWrapper<Ticket> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Ticket::getTicketId, ticketId)
                .set(Ticket::getTags, tags);
        ticketMapper.update(wrapper);
    }

    public Ticket getTicket(String ticketId) {
        return ticketMapper.selectById(ticketId);
    }

    public Ticket getTicketBySessionId(String sessionId) {
        return ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getSessionId, sessionId));
    }

    public List<Ticket> getTicketsByAgent(String agentId) {
        return ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getAgentId, agentId)
                        .orderByDesc(Ticket::getCreateTime));
    }

    public List<Ticket> getTicketsByUser(String userId) {
        return ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getUserId, userId)
                        .orderByDesc(Ticket::getCreateTime));
    }

    public List<Ticket> getPendingTickets() {
        return ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getStatus, TicketStatus.PENDING.name())
                        .orderByAsc(Ticket::getPriority)
                        .orderByAsc(Ticket::getCreateTime));
    }

    public List<Ticket> getOverdueTickets() {
        return ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .in(Ticket::getStatus, TicketStatus.PENDING.name(), TicketStatus.PROCESSING.name())
                        .lt(Ticket::getSlaDeadline, LocalDateTime.now())
                        .orderByAsc(Ticket::getSlaDeadline));
    }
}