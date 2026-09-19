package com.example.service;

import com.example.api.OrderApi;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class LogisticsSimulator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] COMPANIES = {
            "蜂鸟配送", "美团配送", "达达快送", "顺丰同城", "闪送"
    };

    private static final String[] RIDER_NAMES = {
            "李师傅", "王师傅", "张师傅", "赵师傅", "陈师傅", "刘师傅", "周师傅", "吴师傅"
    };

    private static final String[] CONTACT_PHONES = {
            "138****6721", "139****8834", "150****2156", "186****4590", "177****3128", "188****7463"
    };

    private final ConcurrentHashMap<String, LogisticsInfo> logisticsMap = new ConcurrentHashMap<>();

    @Autowired
    private OrderApi orderApi;

    public void setLogistics(String roid, String status, String logisticsCompany,
                             String trackingNumber, String riderName, String riderPhone) {
        LogisticsInfo info = logisticsMap.computeIfAbsent(roid, k -> new LogisticsInfo());
        if (status != null && !status.isEmpty()) {
            info.setStatus(DeliveryStatus.valueOf(status));
        }
        if (logisticsCompany != null && !logisticsCompany.isEmpty()) {
            info.setLogisticsCompany(logisticsCompany);
        }
        if (trackingNumber != null && !trackingNumber.isEmpty()) {
            info.setTrackingNumber(trackingNumber);
        }
        if (riderName != null && !riderName.isEmpty()) {
            info.setRiderName(riderName);
        }
        if (riderPhone != null && !riderPhone.isEmpty()) {
            info.setRiderPhone(riderPhone);
        }
        info.setUpdateTime(LocalDateTime.now());
        log.info("物流状态已设置: roid={}, status={}", roid, info.getStatus());
    }

    public LogisticsInfo getLogistics(String roid) {
        return logisticsMap.get(roid);
    }

    public boolean hasLogistics(String roid) {
        return logisticsMap.containsKey(roid);
    }

    public Map<String, Object> buildLogisticsResponse(String roid) {
        LogisticsInfo info = logisticsMap.get(roid);

        if (info == null) {
            info = buildDefaultLogistics(roid);
        }

        DeliveryStatus currentStatus = info.getStatus();
        Map<String, Object> logistics = new LinkedHashMap<>();
        logistics.put("orderId", roid);
        logistics.put("logisticsCompany", info.getLogisticsCompany());
        logistics.put("trackingNumber", info.getTrackingNumber());
        logistics.put("riderName", info.getRiderName());
        logistics.put("riderPhone", info.getRiderPhone());
        logistics.put("status", currentStatus.getCode());
        logistics.put("statusText", currentStatus.getText());
        logistics.put("statusDescription", currentStatus.getDescription());
        logistics.put("updateTime", info.getUpdateTime() != null
                ? info.getUpdateTime().format(FORMATTER) : null);
        logistics.put("isSimulated", logisticsMap.containsKey(roid));
        logistics.put("timeline", buildTimeline(currentStatus, info.getUpdateTime()));
        logistics.put("availableStatuses", getAvailableStatuses(currentStatus));

        return logistics;
    }

    private LogisticsInfo buildDefaultLogistics(String roid) {
        LogisticsInfo info = new LogisticsInfo();
        long hash = Math.abs(roid.hashCode());
        int companyIdx = (int) (hash % COMPANIES.length);
        int riderIdx = (int) (hash % RIDER_NAMES.length);
        int phoneIdx = (int) (hash % CONTACT_PHONES.length);

        info.setLogisticsCompany(COMPANIES[companyIdx]);
        info.setTrackingNumber("PE" + roid.substring(Math.max(0, roid.length() - 14)));
        info.setRiderName(RIDER_NAMES[riderIdx]);
        info.setRiderPhone(CONTACT_PHONES[phoneIdx]);

        try {
            ResultVo result = orderApi.orderDetail(roid);
            if (result.getCode() == 200) {
                Map<String, Object> orderData = (Map<String, Object>) result.getData();
                Object statusObj = orderData.get("status");
                int orderStatus = statusObj instanceof Number
                        ? ((Number) statusObj).intValue() : 0;

                switch (orderStatus) {
                    case 0:
                        info.setStatus(DeliveryStatus.SUBMITTED);
                        break;
                    case 1:
                        info.setStatus(DeliveryStatus.MERCHANT_ACCEPTED);
                        break;
                    case 2:
                        info.setStatus(DeliveryStatus.DELIVERED);
                        break;
                    case 3:
                    case 4:
                        info.setStatus(DeliveryStatus.CANCELLED);
                        break;
                    default:
                        info.setStatus(DeliveryStatus.SUBMITTED);
                }
            } else {
                info.setStatus(DeliveryStatus.SUBMITTED);
            }
        } catch (Exception e) {
            log.warn("获取订单信息失败，使用默认物流状态: roid={}", roid);
            info.setStatus(DeliveryStatus.SUBMITTED);
        }

        info.setUpdateTime(LocalDateTime.now());
        return info;
    }

    private List<Map<String, String>> buildTimeline(DeliveryStatus currentStatus, LocalDateTime updateTime) {
        List<Map<String, String>> timeline = new ArrayList<>();
        if (updateTime == null) {
            updateTime = LocalDateTime.now();
        }

        LocalDateTime t = updateTime.minusMinutes(30);

        addTimelineNode(timeline, t, "订单已提交", "用户已下单，等待商家确认");
        if (currentStatus == DeliveryStatus.SUBMITTED) return timeline;

        t = t.plusMinutes(2);
        addTimelineNode(timeline, t, "商家已接单", "商家已确认订单，开始备餐");
        if (currentStatus == DeliveryStatus.MERCHANT_ACCEPTED) return timeline;

        t = t.plusMinutes(8);
        addTimelineNode(timeline, t, "商家备餐中", "商家正在精心制作中，请耐心等待");
        if (currentStatus == DeliveryStatus.PREPARING) return timeline;

        t = t.plusMinutes(10);
        addTimelineNode(timeline, t, "骑手已到店", "骑手已到达商家，等待取餐");
        if (currentStatus == DeliveryStatus.RIDER_ARRIVED) return timeline;

        t = t.plusMinutes(3);
        addTimelineNode(timeline, t, "骑手已取餐", "骑手已取到餐品，正在赶往您的地址");
        if (currentStatus == DeliveryStatus.PICKED_UP) return timeline;

        t = t.plusMinutes(12);
        addTimelineNode(timeline, t, "配送中", "骑手正在配送途中，请保持电话畅通");
        if (currentStatus == DeliveryStatus.DELIVERING) return timeline;

        t = t.plusMinutes(10);
        addTimelineNode(timeline, t, "已送达", "餐品已送达，祝您用餐愉快！");

        return timeline;
    }

    private void addTimelineNode(List<Map<String, String>> timeline, LocalDateTime time,
                                  String title, String desc) {
        Map<String, String> node = new LinkedHashMap<>();
        node.put("time", time.format(FORMATTER));
        node.put("title", title);
        node.put("description", desc);
        timeline.add(node);
    }

    private List<Map<String, String>> getAvailableStatuses(DeliveryStatus current) {
        List<Map<String, String>> result = new ArrayList<>();
        boolean found = false;
        for (DeliveryStatus s : DeliveryStatus.values()) {
            if (s == current) {
                found = true;
                continue;
            }
            if (found) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("code", s.getCode());
                item.put("text", s.getText());
                result.add(item);
            }
        }
        return result;
    }

    public static class LogisticsInfo {
        private DeliveryStatus status = DeliveryStatus.SUBMITTED;
        private String logisticsCompany;
        private String trackingNumber;
        private String riderName;
        private String riderPhone;
        private LocalDateTime updateTime;

        public DeliveryStatus getStatus() { return status; }
        public void setStatus(DeliveryStatus status) { this.status = status; }
        public String getLogisticsCompany() { return logisticsCompany; }
        public void setLogisticsCompany(String logisticsCompany) { this.logisticsCompany = logisticsCompany; }
        public String getTrackingNumber() { return trackingNumber; }
        public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
        public String getRiderName() { return riderName; }
        public void setRiderName(String riderName) { this.riderName = riderName; }
        public String getRiderPhone() { return riderPhone; }
        public void setRiderPhone(String riderPhone) { this.riderPhone = riderPhone; }
        public LocalDateTime getUpdateTime() { return updateTime; }
        public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    }

    public enum DeliveryStatus {
        SUBMITTED("订单已提交", "用户已下单，等待商家确认"),
        MERCHANT_ACCEPTED("商家已接单", "商家已确认订单，开始备餐"),
        PREPARING("商家备餐中", "商家正在精心制作，请耐心等待"),
        RIDER_ARRIVED("骑手已到店", "骑手已到达商家取餐"),
        PICKED_UP("骑手已取餐", "骑手已取到餐品，正在配送途中"),
        DELIVERING("配送中", "骑手正在配送途中，请保持电话畅通"),
        DELIVERED("已送达", "餐品已送达，祝您用餐愉快！"),
        CANCELLED("已取消", "订单已取消");

        private final String text;
        private final String description;

        DeliveryStatus(String text, String description) {
            this.text = text;
            this.description = description;
        }

        public String getCode() { return name(); }
        public String getText() { return text; }
        public String getDescription() { return description; }
    }
}