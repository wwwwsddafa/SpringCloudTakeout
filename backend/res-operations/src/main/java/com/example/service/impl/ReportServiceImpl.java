package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bean.*;
import com.example.dao.mapper.*;
import com.example.service.OpsStatsService;
import com.example.service.ReportService;
import com.example.web.vo.EmailMessage;
import com.example.web.vo.ProductRankingVo;
import com.example.web.vo.TrendVo;
import com.example.web.vo.OpsDashboardVo;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired private DailyOpsSummaryMapper dailyOpsSummaryMapper;
    @Autowired private DailyOrderStatsMapper dailyOrderStatsMapper;
    @Autowired private DailyProductStatsMapper dailyProductStatsMapper;
    @Autowired private DailyPvStatsMapper dailyPvStatsMapper;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private OpsStatsService opsStatsService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter CN_FMT = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    // ========== PDF 中文字体（懒加载） ==========
    private static BaseFont CN_BASE_FONT;
    private static Font CN_TITLE_FONT;
    private static Font CN_HEAD_FONT;
    private static Font CN_NORMAL_FONT;
    private static Font CN_SMALL_FONT;

    private static BaseFont getCnBaseFont() {
        if (CN_BASE_FONT == null) {
            try {
                CN_BASE_FONT = BaseFont.createFont("fonts/simhei.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                try {
                    CN_BASE_FONT = BaseFont.createFont("C:/Windows/Fonts/simhei.ttf",
                            BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                } catch (Exception e2) {
                    try {
                        CN_BASE_FONT = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
                    } catch (Exception e3) {
                        throw new RuntimeException("无中文字体可用，PDF 中文将无法显示", e3);
                    }
                }
            }
        }
        return CN_BASE_FONT;
    }

    private static Font cnFont(float size) {
        return new Font(getCnBaseFont(), size);
    }

    @Override
    public byte[] generatePdfReport(String date) {
        return generatePdfReportStream(date).toByteArray();
    }

    @Override
    public byte[] generateExcelReport(String date) {
        return generateExcelReportStream(date).toByteArray();
    }

    // ============================================================
    //  PDF 生成（3 页）
    // ============================================================
    @Override
    public ByteArrayOutputStream generatePdfReportStream(String date) {
        LocalDate statDate = LocalDate.parse(date, DATE_FMT);
        ResDailyOpsSummary summary = querySummary(statDate);
        ResDailyOrderStats orderStats = queryOrderStats(statDate);
        List<ResDailyProductStats> productStats = queryProductStats(statDate);
        List<ResDailyProductStats> top10 = productStats == null ? Collections.emptyList() :
                productStats.stream()
                        .sorted(Comparator.comparing(ResDailyProductStats::getSalesAmount,
                                Comparator.nullsFirst(BigDecimal::compareTo).reversed()))
                        .limit(10).collect(Collectors.toList());
        List<ResDailyProductStats> topReview = productStats == null ? Collections.emptyList() :
                productStats.stream()
                        .sorted(Comparator.comparing(ResDailyProductStats::getReviewCount,
                                Comparator.nullsFirst(Integer::compareTo).reversed()))
                        .limit(10).collect(Collectors.toList());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // ---------- P1：核心指标 ----------
            addPdfTitle(doc, "当日成交情况汇总", statDate);

            PdfPTable t1 = new PdfPTable(4);
            t1.setWidthPercentage(100);
            t1.setSpacingAfter(10);
            float[] cw1 = {1.2f, 1.8f, 1.2f, 1.8f};
            t1.setWidths(cw1);

            addMetric(t1, "全站 PV", str(summary != null ? summary.getTotalPv() : 0));
            addMetric(t1, "独立访客", str(summary != null ? summary.getTotalUv() : 0));
            addMetric(t1, "活跃用户", str(summary != null ? summary.getActiveUsers() : 0));
            addMetric(t1, "新增用户", str(summary != null ? summary.getNewUsers() : 0));
            addMetric(t1, "总订单数", str(summary != null ? summary.getTotalOrders() : 0));
            addMetric(t1, "营业额(已支付)", formatMoney(summary != null ? summary.getTotalRevenue() : BigDecimal.ZERO));
            addMetric(t1, "转化率", formatPercent(summary != null ? summary.getConversionRate() : BigDecimal.ZERO));
            addMetric(t1, "客单价", formatMoney(summary != null ? summary.getAvgOrderAmount() : BigDecimal.ZERO));

            // 订单结构
            if (orderStats != null) {
                addMetric(t1, "已支付订单", str(orderStats.getPaidOrders()));
                addMetric(t1, "已支付金额", formatMoney(orderStats.getPaidAmount()));
                addMetric(t1, "已取消订单", str(orderStats.getCancelledOrders()));
                addMetric(t1, "待支付订单", str(orderStats.getPendingOrders()));
            }
            doc.add(t1);

            doc.add(new Paragraph(" "));

            // ---------- P1 底部：下单总金额 ----------
            PdfPTable t1b = new PdfPTable(2);
            t1b.setWidthPercentage(100);
            t1b.setSpacingAfter(15);
            addPdfRow(t1b, "下单总金额(含未付)", formatMoney(orderStats != null ? orderStats.getTotalAmount() : BigDecimal.ZERO));
            if (orderStats != null && orderStats.getMaxOrderAmount().compareTo(BigDecimal.ZERO) > 0) {
                addPdfRow(t1b, "最大单笔", formatMoney(orderStats.getMaxOrderAmount()));
            }
            addPdfRow(t1b, "点赞总数", str(summary != null ? summary.getTotalLikes() : 0));
            addPdfRow(t1b, "评价总数", str(summary != null ? summary.getTotalReviews() : 0));
            doc.add(t1b);

            // ---------- P2：商品双榜 ----------
            doc.newPage();
            Paragraph p2Title = new Paragraph("商品销售额 Top10", cnFont(16));
            p2Title.setAlignment(Element.ALIGN_CENTER);
            p2Title.setSpacingAfter(15);
            doc.add(p2Title);

            PdfPTable t2 = new PdfPTable(5);
            t2.setWidthPercentage(100);
            t2.setSpacingAfter(20);
            float[] cw2 = {0.8f, 4f, 2f, 2f, 3f};
            t2.setWidths(cw2);
            t2.addCell(cnCell("排名", true));
            t2.addCell(cnCell("商品名称", true));
            t2.addCell(cnCell("下单数", true));
            t2.addCell(cnCell("销售额", true));
            t2.addCell(cnCell("转化率", true));
            int rank = 1;
            for (ResDailyProductStats p : top10) {
                t2.addCell(cnCell(String.valueOf(rank++), false));
                t2.addCell(cnCell(p.getFname() != null ? p.getFname() : p.getFid(), false));
                t2.addCell(cnCell(str(p.getOrderCount()), false));
                t2.addCell(cnCell(formatMoney(p.getSalesAmount()), false));
                t2.addCell(cnCell(formatPercent(p.getConversionRate()), false));
            }
            doc.add(t2);

            Paragraph reviewTitle = new Paragraph("评价口碑 Top10", cnFont(16));
            reviewTitle.setAlignment(Element.ALIGN_CENTER);
            reviewTitle.setSpacingBefore(10);
            reviewTitle.setSpacingAfter(10);
            doc.add(reviewTitle);

            PdfPTable t3 = new PdfPTable(5);
            t3.setWidthPercentage(100);
            t3.setSpacingAfter(15);
            float[] cw3 = {0.8f, 4f, 2f, 2f, 2f};
            t3.setWidths(cw3);
            t3.addCell(cnCell("排名", true));
            t3.addCell(cnCell("商品名称", true));
            t3.addCell(cnCell("评价数", true));
            t3.addCell(cnCell("评分", true));
            t3.addCell(cnCell("下单数", true));
            rank = 1;
            for (ResDailyProductStats p : topReview) {
                t3.addCell(cnCell(String.valueOf(rank++), false));
                t3.addCell(cnCell(p.getFname() != null ? p.getFname() : p.getFid(), false));
                t3.addCell(cnCell(str(p.getReviewCount()), false));
                t3.addCell(cnCell(p.getAvgStar() != null ? p.getAvgStar().setScale(1, RoundingMode.HALF_UP) + " 分" : "0.0 分", false));
                t3.addCell(cnCell(str(p.getOrderCount()), false));
            }
            doc.add(t3);

            // ---------- P3：7 日趋势 ----------
            doc.newPage();
            Paragraph p3Title = new Paragraph("7 日运营趋势", cnFont(16));
            p3Title.setAlignment(Element.ALIGN_CENTER);
            p3Title.setSpacingAfter(15);
            doc.add(p3Title);

            try {
                TrendVo trend = opsStatsService.getTrend(7);
                if (trend != null) {
                    PdfPTable t4 = new PdfPTable(4);
                    t4.setWidthPercentage(100);
                    t4.setSpacingAfter(15);
                    float[] cw4 = {2f, 1.5f, 1.5f, 2f};
                    t4.setWidths(cw4);
                    t4.addCell(cnCell("日期", true));
                    t4.addCell(cnCell("PV", true));
                    t4.addCell(cnCell("UV", true));
                    t4.addCell(cnCell("营业额", true));
                    List<TrendVo.TrendPoint> pvs = trend.getPvTrend();
                    List<TrendVo.TrendPoint> uvs = trend.getUvTrend();
                    List<TrendVo.TrendPoint> revs = trend.getRevenueTrend();
                    int n = pvs != null ? pvs.size() : 0;
                    for (int i = 0; i < n && i < 7; i++) {
                        t4.addCell(cnCell(pvs.get(i).getDate(), false));
                        t4.addCell(cnCell(formatLong(pvs.get(i).getValue()), false));
                        t4.addCell(cnCell(formatLong(uvs != null && i < uvs.size() ? uvs.get(i).getValue() : null), false));
                        t4.addCell(cnCell(formatMoney(revs != null && i < revs.size() ? revs.get(i).getValue() : BigDecimal.ZERO), false));
                    }
                    doc.add(t4);
                }
            } catch (Exception e) {
                log.warn("7 日趋势获取失败: {}", e.getMessage());
            }

            // ---------- P3 底部：设备分布 ----------
            if (summary != null && summary.getDeviceDistJson() != null) {
                doc.add(new Paragraph("设备分布", cnFont(14)));
                Map<String, Long> dm = parseDeviceJson(summary.getDeviceDistJson());
                if (!dm.isEmpty()) {
                    long total = dm.values().stream().mapToLong(Long::longValue).sum();
                    PdfPTable t5 = new PdfPTable(3);
                    t5.setWidthPercentage(60);
                    t5.setSpacingBefore(10);
                    float[] cw5 = {2f, 1.5f, 1.5f};
                    t5.setWidths(cw5);
                    t5.addCell(cnCell("设备", true));
                    t5.addCell(cnCell("访问量", true));
                    t5.addCell(cnCell("占比", true));
                    for (Map.Entry<String, Long> e : deviceOrdered(dm)) {
                        t5.addCell(cnCell(deviceCn(e.getKey()), false));
                        t5.addCell(cnCell(str(e.getValue()), false));
                        t5.addCell(cnCell(String.format("%.1f%%", total > 0 ? e.getValue() * 100.0 / total : 0), false));
                    }
                    doc.add(t5);
                }
            }

            doc.add(new Paragraph(" "));
            Paragraph footer = new Paragraph("此为系统自动生成报告，请勿回复。", cnFont(9));
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
        } catch (Exception e) {
            log.error("PDF 生成失败", e);
        }
        return out;
    }

    // ============================================================
    //  XLSX 生成（9 sheets）
    // ============================================================
    @Override
    public ByteArrayOutputStream generateExcelReportStream(String date) {
        LocalDate statDate = LocalDate.parse(date, DATE_FMT);
        ResDailyOpsSummary summary = querySummary(statDate);
        ResDailyOrderStats orderStats = queryOrderStats(statDate);
        List<ResDailyProductStats> productStats = queryProductStats(statDate);
        List<ResDailyPvStats> pvStats = queryPvStats(statDate);
        ResDailyOpsSummary yesterdaySummary = querySummary(statDate.minusDays(1));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle hs = createHeaderStyle(wb);
            CellStyle ds = createDataStyle(wb);
            CellStyle ts = createTitleStyle(wb);
            CellStyle ns = numStyle(wb, "#,##0");
            CellStyle ms = numStyle(wb, "¥#,##0.00");
            CellStyle ps = numStyle(wb, "0.00\"%\"");
            CellStyle ss = numStyle(wb, "0.0\" 分\"");

            createOverviewSheet2(wb, summary, yesterdaySummary, hs, ds, ts, ns, ms, ps, statDate);
            createTrafficSheet2(wb, pvStats, hs, ds, ts, ns, ps, statDate);
            createDeviceSheet2(wb, summary, hs, ds, ts, ns, ps, statDate);
            createProductSheet2(wb, productStats, hs, ds, ts, ns, ms, ps, ss, statDate);
            createRankingSheet2(wb, hs, ds, ts, ns, ms, statDate);
            createOrderSheet2(wb, orderStats, hs, ds, ts, ns, ms, statDate);
            createUserSheet2(wb, summary, hs, ds, ts, ns, ps, statDate);
            createHourlySheet2(wb, summary, hs, ds, ts, ns, statDate);
            createTrendSheet2(wb, hs, ds, ts, ns, ms, statDate);

            wb.write(out);
        } catch (Exception e) {
            log.error("Excel 生成失败", e);
        }
        return out;
    }

    // ============================================================
    //  邮件发送（含摘要参数）
    // ============================================================
    @Override
    public void sendDailyReport(String date, String recipientEmail) {
        try {
            log.info("生成运营报告: date={}, recipient={}", date, recipientEmail);
            ByteArrayOutputStream pdfStream = generatePdfReportStream(date);
            ByteArrayOutputStream excelStream = generateExcelReportStream(date);

            String pdfBase64 = Base64.getEncoder().encodeToString(pdfStream.toByteArray());
            String excelBase64 = Base64.getEncoder().encodeToString(excelStream.toByteArray());

            // 摘要参数（用于邮件正文 HTML 卡片）
            LocalDate statDate = LocalDate.parse(date, DATE_FMT);
            ResDailyOpsSummary summary = querySummary(statDate);
            List<ResDailyProductStats> productStats = queryProductStats(statDate);
            List<Map<String, Object>> top5 = new ArrayList<>();
            if (productStats != null) {
                productStats.stream()
                        .sorted(Comparator.comparing(ResDailyProductStats::getSalesAmount,
                                Comparator.nullsFirst(BigDecimal::compareTo).reversed()))
                        .limit(5).forEach(p -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("name", p.getFname() != null ? p.getFname() : p.getFid());
                            m.put("amount", p.getSalesAmount() != null ? p.getSalesAmount() : BigDecimal.ZERO);
                            top5.add(m);
                        });
            }

            Map<String, Object> params = new HashMap<>();
            params.put("date", date);
            params.put("pdfBase64", pdfBase64);
            params.put("excelBase64", excelBase64);
            params.put("pv", summary != null ? summary.getTotalPv() : 0L);
            params.put("uv", summary != null ? summary.getTotalUv() : 0L);
            params.put("orders", summary != null ? summary.getTotalOrders() : 0);
            params.put("revenue", summary != null && summary.getTotalRevenue() != null ? summary.getTotalRevenue().toPlainString() : "0.00");
            params.put("conversionRate", summary != null && summary.getConversionRate() != null ? summary.getConversionRate().setScale(2).toPlainString() : "0.00");
            params.put("avgOrderAmount", summary != null && summary.getAvgOrderAmount() != null ? summary.getAvgOrderAmount().setScale(2).toPlainString() : "0.00");
            params.put("activeUsers", summary != null ? summary.getActiveUsers() : 0);
            params.put("newUsers", summary != null ? summary.getNewUsers() : 0);
            params.put("top5", top5);

            EmailMessage message = EmailMessage.builder()
                    .type(EmailMessage.TYPE_OPS_REPORT)
                    .email(recipientEmail)
                    .username("管理员")
                    .params(params)
                    .build();

            rabbitTemplate.convertAndSend(
                    EmailMessage.EMAIL_EXCHANGE,
                    EmailMessage.EMAIL_ROUTING_KEY,
                    message);

            log.info("运营报告已发送到 RabbitMQ: date={}, recipient={}", date, recipientEmail);
        } catch (Exception e) {
            log.error("运营报告生成或发送失败: date={}, recipient={}", date, recipientEmail, e);
            throw new RuntimeException("运营报告发送失败: " + e.getMessage(), e);
        }
    }

    // ============================================================
    //  Sheet：运营概览（含环比）
    // ============================================================
    private void createOverviewSheet2(XSSFWorkbook wb, ResDailyOpsSummary s, ResDailyOpsSummary ys,
                                       CellStyle hs, CellStyle ds, CellStyle ts, CellStyle ns, CellStyle ms, CellStyle ps,
                                       LocalDate date) {
        Sheet sheet = wb.createSheet("1.运营概览");
        addSheetTitle(sheet, "运营概览", date, ts);
        Row hr = sheet.createRow(1);
        String[] h = {"指标", "今日", "昨日", "环比"};
        for (int i = 0; i < h.length; i++) createCell(hr, i, h[i], hs);

        Object[][] data = {
                {"全站 PV", dv(s, ResDailyOpsSummary::getTotalPv), dv(ys, ResDailyOpsSummary::getTotalPv), null},
                {"全站 UV", dv(s, ResDailyOpsSummary::getTotalUv), dv(ys, ResDailyOpsSummary::getTotalUv), null},
                {"总订单数", di(s, ResDailyOpsSummary::getTotalOrders), di(ys, ResDailyOpsSummary::getTotalOrders), null},
                {"营业额(已支付)", d(s, ResDailyOpsSummary::getTotalRevenue), d(ys, ResDailyOpsSummary::getTotalRevenue), null},
                {"转化率", d(s, ResDailyOpsSummary::getConversionRate), d(ys, ResDailyOpsSummary::getConversionRate), null},
                {"客单价", d(s, ResDailyOpsSummary::getAvgOrderAmount), d(ys, ResDailyOpsSummary::getAvgOrderAmount), null},
                {"活跃用户", di(s, ResDailyOpsSummary::getActiveUsers), di(ys, ResDailyOpsSummary::getActiveUsers), null},
                {"新增用户", di(s, ResDailyOpsSummary::getNewUsers), di(ys, ResDailyOpsSummary::getNewUsers), null},
                {"点赞总数", di(s, ResDailyOpsSummary::getTotalLikes), di(ys, ResDailyOpsSummary::getTotalLikes), null},
                {"评价总数", di(s, ResDailyOpsSummary::getTotalReviews), di(ys, ResDailyOpsSummary::getTotalReviews), null},
        };
        for (int i = 0; i < data.length; i++) {
            Row r = sheet.createRow(i + 2);
            createCell(r, 0, (String) data[i][0], hs);
            Object tv = data[i][1], yv = data[i][2];
            if (tv instanceof Long) {
                createCell(r, 1, ((Long) tv).doubleValue(), ns);
                createCell(r, 2, yv != null ? ((Long) yv).doubleValue() : 0, ns);
            } else if (tv instanceof Integer) {
                createCell(r, 1, ((Integer) tv).doubleValue(), ns);
                createCell(r, 2, yv != null ? ((Integer) yv).doubleValue() : 0, ns);
            } else if (tv instanceof BigDecimal) {
                createCell(r, 1, ((BigDecimal) tv).doubleValue(), ms);
                createCell(r, 2, yv != null ? ((BigDecimal) yv).doubleValue() : 0, ms);
            }
            // 环比
            double t = tv instanceof Number ? ((Number) tv).doubleValue() : 0;
            double y = yv instanceof Number ? ((Number) yv).doubleValue() : 0;
            String pct = y != 0 ? String.format("%+.2f%%", (t - y) / y * 100) : (t == 0 ? "0.00%" : "+∞");
            createCell(r, 3, pct, ds);
        }
        sheet.setColumnWidth(0, 4000);
        for (int i = 1; i <= 3; i++) sheet.setColumnWidth(i, 5000);
    }

    // ============================================================
    //  Sheet：流量分析
    // ============================================================
    private void createTrafficSheet2(XSSFWorkbook wb, List<ResDailyPvStats> pvStats,
                                      CellStyle hs, CellStyle ds, CellStyle ts, CellStyle ns, CellStyle ps,
                                      LocalDate date) {
        Sheet sheet = wb.createSheet("2.流量分析");
        addSheetTitle(sheet, "流量分析", date, ts);
        Row hr = sheet.createRow(1);
        String[] h = {"页面名称", "PV", "UV", "PV占比"};
        for (int i = 0; i < h.length; i++) createCell(hr, i, h[i], hs);

        long totalPv = pvStats != null ? pvStats.stream().mapToLong(s -> s.getPvCount() != null ? s.getPvCount() : 0).sum() : 0;
        int rowIdx = 2;
        if (pvStats != null) {
            for (ResDailyPvStats s : pvStats) {
                Row r = sheet.createRow(rowIdx++);
                createCell(r, 0, pageTypeCn(s.getPageType()), ds);
                createCell(r, 1, dbl(s.getPvCount()), ns);
                createCell(r, 2, dbl(s.getUvCount()), ns);
                double pct = totalPv > 0 ? (s.getPvCount() != null ? s.getPvCount() * 100.0 / totalPv : 0) : 0;
                createCell(r, 3, pct, ps);
            }
        }
        sheet.setColumnWidth(0, 5000);
        for (int i = 1; i <= 3; i++) sheet.setColumnWidth(i, 4000);
    }

    // ============================================================
    //  Sheet：设备分布
    // ============================================================
    private void createDeviceSheet2(XSSFWorkbook wb, ResDailyOpsSummary summary,
                                     CellStyle hs, CellStyle ds, CellStyle ts, CellStyle ns, CellStyle ps,
                                     LocalDate date) {
        Sheet sheet = wb.createSheet("3.设备分布");
        addSheetTitle(sheet, "设备分布", date, ts);
        Row hr = sheet.createRow(1);
        String[] h = {"设备类型", "访问量", "占比"};
        for (int i = 0; i < h.length; i++) createCell(hr, i, h[i], hs);

        Map<String, Long> dm = summary != null ? parseDeviceJson(summary.getDeviceDistJson()) : Collections.emptyMap();
        long total = dm.values().stream().mapToLong(Long::longValue).sum();
        int rowIdx = 2;
        for (Map.Entry<String, Long> e : deviceOrdered(dm)) {
            Row r = sheet.createRow(rowIdx++);
            createCell(r, 0, deviceCn(e.getKey()), ds);
            createCell(r, 1, dbl(e.getValue()), ns);
            createCell(r, 2, total > 0 ? e.getValue() * 100.0 / total : 0, ps);
        }
        sheet.setColumnWidth(0, 4000);
        sheet.setColumnWidth(1, 4000);
        sheet.setColumnWidth(2, 4000);
    }

    // ============================================================
    //  Sheet：商品排行（Top50）
    // ============================================================
    private void createProductSheet2(XSSFWorkbook wb, List<ResDailyProductStats> productStats,
                                      CellStyle hs, CellStyle ds, CellStyle ts, CellStyle ns, CellStyle ms, CellStyle ps, CellStyle ss,
                                      LocalDate date) {
        Sheet sheet = wb.createSheet("4.商品排行");
        addSheetTitle(sheet, "商品排行 Top50", date, ts);
        Row hr = sheet.createRow(1);
        String[] h = {"排名", "商品名称", "分类", "PV", "UV", "下单次数", "已付款", "销售额", "点赞", "评价数", "评分", "转化率"};
        for (int i = 0; i < h.length; i++) createCell(hr, i, h[i], hs);

        int rowIdx = 2, rank = 1;
        if (productStats != null) {
            productStats.sort(Comparator.comparing(ResDailyProductStats::getSalesAmount,
                    Comparator.nullsFirst(BigDecimal::compareTo).reversed()));
            for (ResDailyProductStats p : productStats) {
                if (rank > 50) break;
                Row r = sheet.createRow(rowIdx++);
                createCell(r, 0, (double) (rank++), ns);
                createCell(r, 1, p.getFname() != null ? p.getFname() : p.getFid(), ds);
                createCell(r, 2, p.getCategory() != null ? p.getCategory() : "", ds);
                createCell(r, 3, dbl(p.getPvCount()), ns);
                createCell(r, 4, dbl(p.getUvCount()), ns);
                createCell(r, 5, dbl(p.getOrderCount()), ns);
                createCell(r, 6, dbl(p.getPaidOrderCount()), ns);
                createCell(r, 7, bd(p.getSalesAmount()).doubleValue(), ms);
                createCell(r, 8, dbl(p.getLikeCount()), ns);
                createCell(r, 9, dbl(p.getReviewCount()), ns);
                createCell(r, 10, bd(p.getAvgStar()).doubleValue(), ss);
                createCell(r, 11, bd(p.getConversionRate()).doubleValue(), ps);
            }
        }
        sheet.setColumnWidth(1, 6000);
        sheet.setColumnWidth(2, 4000);
        for (int i = 0; i < h.length; i++) if (i != 1 && i != 2) sheet.setColumnWidth(i, 3500);
    }

    // ============================================================
    //  Sheet：商品榜单（5 榜 × Top10）
    // ============================================================
    private void createRankingSheet2(XSSFWorkbook wb,
                                      CellStyle hs, CellStyle ds, CellStyle ts, CellStyle ns, CellStyle ms,
                                      LocalDate date) {
        Sheet sheet = wb.createSheet("5.商品榜单");
        addSheetTitle(sheet, "商品榜单", date, ts);
        // 评分专用样式（保留1位小数）
        CellStyle starStyle = wb.createCellStyle();
        starStyle.setDataFormat(wb.createDataFormat().getFormat("0.0"));
        ProductRankingVo vo;
        try {
            vo = opsStatsService.getProductRanking(date.format(DATE_FMT), 10);
        } catch (Exception e) {
            log.warn("商品榜单获取失败: {}", e.getMessage());
            return;
        }
        if (vo == null) return;
        int rowIdx = 2;
        rowIdx = writeRankBlock(sheet, rowIdx, "下单次数排行", vo.getTopByOrderCount(), new String[]{"下单数", "已付款", "销售额"}, ns, ms, hs, ds, starStyle);
        rowIdx = writeRankBlock(sheet, rowIdx + 1, "浏览量排行", vo.getTopByPvCount(), new String[]{"PV", "已付款", "销售额"}, ns, ms, hs, ds, starStyle);
        rowIdx = writeRankBlock(sheet, rowIdx + 1, "评价最多排行", vo.getTopByReviewCount(), new String[]{"评价数", "评分", "销售额"}, ns, ms, hs, ds, starStyle);
        rowIdx = writeRankBlock(sheet, rowIdx + 1, "口碑最好排行", vo.getTopByAvgStar(), new String[]{"评分", "评价数", "销售额"}, ns, ms, hs, ds, starStyle);
        writeRankBlock(sheet, rowIdx + 1, "已付款订单排行", vo.getTopByPaidOrderCount(), new String[]{"已付款", "下单数", "销售额"}, ns, ms, hs, ds, starStyle);
    }

    private int writeRankBlock(Sheet sheet, int startRow, String title,
                                List<ProductRankingVo.RankItem> items, String[] valueHeaders,
                                CellStyle ns, CellStyle ms, CellStyle hs, CellStyle ds,
                                CellStyle starStyle) {
        if (items == null || items.isEmpty()) return startRow;
        Row tr = sheet.createRow(startRow);
        createCell(tr, 0, title, hs);
        startRow++;
        Row sr = sheet.createRow(startRow);
        createCell(sr, 0, "排名", hs);
        createCell(sr, 1, "商品名称", hs);
        for (int i = 0; i < valueHeaders.length; i++) createCell(sr, i + 2, valueHeaders[i], hs);
        startRow++;
        int rk = 1;
        for (ProductRankingVo.RankItem item : items) {
            Row r = sheet.createRow(startRow++);
            createCell(r, 0, (double) rk++, ns);
            createCell(r, 1, item.getFname() != null ? item.getFname() : item.getFid(), ds);
            BigDecimal v = item.getValue();
            if (v != null) {
                // 如果第1个值是评分则用 starStyle
                if (valueHeaders[0].contains("评分")) {
                    createCell(r, 2, v.doubleValue(), starStyle);
                } else {
                    createCell(r, 2, v.doubleValue(), ns);
                }
            } else {
                createCell(r, 2, 0, ns);
            }
            createCell(r, 3, dbl(item.getPaidOrderCount()), ns);
            createCell(r, 4, bd(item.getSalesAmount()).doubleValue(), ms);
        }
        return startRow;
    }

    // ============================================================
    //  Sheet：订单分析
    // ============================================================
    private void createOrderSheet2(XSSFWorkbook wb, ResDailyOrderStats s,
                                    CellStyle hs, CellStyle ds, CellStyle ts, CellStyle ns, CellStyle ms,
                                    LocalDate date) {
        Sheet sheet = wb.createSheet("6.订单分析");
        addSheetTitle(sheet, "订单分析", date, ts);
        Row hr = sheet.createRow(1);
        String[] h = {"指标", "数值"};
        for (int i = 0; i < h.length; i++) createCell(hr, i, h[i], hs);

        Object[][] data = {
                {"总订单数", di(s, ResDailyOrderStats::getTotalOrders)},
                {"总金额", d(s, ResDailyOrderStats::getTotalAmount)},
                {"已支付订单", di(s, ResDailyOrderStats::getPaidOrders)},
                {"已支付金额", d(s, ResDailyOrderStats::getPaidAmount)},
                {"已取消订单", di(s, ResDailyOrderStats::getCancelledOrders)},
                {"待支付订单", di(s, ResDailyOrderStats::getPendingOrders)},
                {"已退款订单", di(s, ResDailyOrderStats::getRefundedOrders)},
                {"客单价", d(s, ResDailyOrderStats::getAvgOrderAmount)},
        };
        for (int i = 0; i < data.length; i++) {
            Row r = sheet.createRow(i + 2);
            createCell(r, 0, (String) data[i][0], hs);
            Object v = data[i][1];
            if (v instanceof Integer) createCell(r, 1, ((Integer) v).doubleValue(), ns);
            else if (v instanceof BigDecimal) createCell(r, 1, ((BigDecimal) v).doubleValue(), ms);
            else createCell(r, 1, 0, ns);
        }
        sheet.setColumnWidth(0, 4000);
        sheet.setColumnWidth(1, 5000);
    }

    // ============================================================
    //  Sheet：用户分析
    // ============================================================
    private void createUserSheet2(XSSFWorkbook wb, ResDailyOpsSummary s,
                                   CellStyle hs, CellStyle ds, CellStyle ts, CellStyle ns, CellStyle ps,
                                   LocalDate date) {
        Sheet sheet = wb.createSheet("7.用户分析");
        addSheetTitle(sheet, "用户分析", date, ts);
        Row hr = sheet.createRow(1);
        String[] h = {"指标", "数值"};
        for (int i = 0; i < h.length; i++) createCell(hr, i, h[i], hs);

        Object[][] data = {
                {"新增注册用户", di(s, ResDailyOpsSummary::getNewUsers)},
                {"活跃用户", di(s, ResDailyOpsSummary::getActiveUsers)},
                {"独立访客", dv(s, ResDailyOpsSummary::getTotalUv)},
                {"转化率", d(s, ResDailyOpsSummary::getConversionRate)},
                {"客单价", d(s, ResDailyOpsSummary::getAvgOrderAmount)},
        };
        for (int i = 0; i < data.length; i++) {
            Row r = sheet.createRow(i + 2);
            createCell(r, 0, (String) data[i][0], hs);
            Object v = data[i][1];
            if (v instanceof Long) createCell(r, 1, ((Long) v).doubleValue(), ns);
            else if (v instanceof Integer) createCell(r, 1, ((Integer) v).doubleValue(), ns);
            else if (v instanceof BigDecimal) createCell(r, 1, ((BigDecimal) v).doubleValue(), ps);
            else createCell(r, 1, 0, ns);
        }
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 4000);
    }

    // ============================================================
    //  Sheet：时段趋势
    // ============================================================
    private void createHourlySheet2(XSSFWorkbook wb, ResDailyOpsSummary s,
                                     CellStyle hs, CellStyle ds, CellStyle ts, CellStyle ns,
                                     LocalDate date) {
        Sheet sheet = wb.createSheet("8.时段趋势");
        addSheetTitle(sheet, "时段趋势", date, ts);
        Row hr = sheet.createRow(1);
        String[] h = {"小时", "PV", "订单数"};
        for (int i = 0; i < h.length; i++) createCell(hr, i, h[i], hs);

        Map<Integer, Long> pvMap = parseHourly(s != null ? s.getHourlyPvJson() : null);
        Map<Integer, Long> odMap = parseHourly(s != null ? s.getHourlyOrderJson() : null);
        for (int h2 = 0; h2 < 24; h2++) {
            Row r = sheet.createRow(h2 + 2);
            createCell(r, 0, String.format("%02d:00", h2), ds);
            createCell(r, 1, pvMap.getOrDefault(h2, 0L).doubleValue(), ns);
            createCell(r, 2, odMap.getOrDefault(h2, 0L).doubleValue(), ns);
        }
        sheet.setColumnWidth(0, 3000);
        sheet.setColumnWidth(1, 4000);
        sheet.setColumnWidth(2, 4000);
    }

    // ============================================================
    //  Sheet：7 日趋势
    // ============================================================
    private void createTrendSheet2(XSSFWorkbook wb,
                                    CellStyle hs, CellStyle ds, CellStyle ts, CellStyle ns, CellStyle ms,
                                    LocalDate date) {
        Sheet sheet = wb.createSheet("9.7日趋势");
        addSheetTitle(sheet, "7日趋势", date, ts);
        Row hr = sheet.createRow(1);
        String[] h = {"日期", "PV", "UV", "订单", "营业额", "电脑端", "手机端", "平板端"};
        for (int i = 0; i < h.length; i++) createCell(hr, i, h[i], hs);

        try {
            TrendVo trend = opsStatsService.getTrend(7);
            if (trend == null) return;
            List<TrendVo.TrendPoint> pvs = trend.getPvTrend();
            List<TrendVo.TrendPoint> uvs = trend.getUvTrend();
            List<TrendVo.TrendPoint> ods = trend.getOrderTrend();
            List<TrendVo.TrendPoint> revs = trend.getRevenueTrend();
            List<TrendVo.DeviceTrendPoint> devs = trend.getDeviceTrend();
            int n = pvs != null ? pvs.size() : 0;
            for (int i = 0; i < n && i < 7; i++) {
                Row r = sheet.createRow(i + 2);
                createCell(r, 0, pvs.get(i).getDate(), ds);
                createCell(r, 1, pvs.get(i).getValue().doubleValue(), ns);
                createCell(r, 2, uvs != null && i < uvs.size() ? uvs.get(i).getValue().doubleValue() : 0, ns);
                createCell(r, 3, ods != null && i < ods.size() ? ods.get(i).getValue().doubleValue() : 0, ns);
                createCell(r, 4, revs != null && i < revs.size() ? revs.get(i).getValue().doubleValue() : 0, ms);
                TrendVo.DeviceTrendPoint dp = devs != null && i < devs.size() ? devs.get(i) : null;
                createCell(r, 5, dp != null ? dp.getDesktop().doubleValue() : 0, ns);
                createCell(r, 6, dp != null ? dp.getMobile().doubleValue() : 0, ns);
                createCell(r, 7, dp != null ? dp.getTablet().doubleValue() : 0, ns);
            }
        } catch (Exception e) {
            log.warn("7日趋势获取失败: {}", e.getMessage());
        }
        for (int i = 0; i < h.length; i++) sheet.setColumnWidth(i, 4000);
    }

    // ============================================================
    //  工具方法
    // ============================================================
    private void addSheetTitle(Sheet sheet, String title, LocalDate date, CellStyle ts) {
        Row r = sheet.createRow(0);
        Cell c = r.createCell(0);
        c.setCellValue(title + "    统计日期：" + date.format(DATE_FMT));
        c.setCellStyle(ts);
        sheet.createFreezePane(0, 2);
    }

    private void addPdfTitle(Document doc, String title, LocalDate date) throws DocumentException {
        Paragraph p = new Paragraph(title, cnFont(18));
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingAfter(10);
        doc.add(p);
        Paragraph d = new Paragraph(date.format(CN_FMT), cnFont(10));
        d.setAlignment(Element.ALIGN_CENTER);
        d.setSpacingAfter(15);
        doc.add(d);
    }

    private void addMetric(PdfPTable table, String label, String value) {
        table.addCell(cnCell(label, true));
        table.addCell(cnCell(value, false));
    }

    private void addPdfRow(PdfPTable table, String label, String value) {
        table.addCell(cnCell(label, true));
        table.addCell(cnCell(value, false));
    }

    private PdfPCell cnCell(String text, boolean bold) {
        PdfPCell cell = new PdfPCell(new Phrase(text, cnFont(bold ? 10 : 9)));
        cell.setPadding(4);
        return cell;
    }

    private Map<Integer, Long> parseHourly(String json) {
        Map<Integer, Long> map = new HashMap<>();
        if (json == null || json.isEmpty() || "[]".equals(json)) return map;
        for (String part : json.replace("[", "").replace("]", "").replace("\"", "").split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2) {
                try { map.put(Integer.parseInt(kv[0].trim()), Long.parseLong(kv[1].trim())); }
                catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    private Map<String, Long> parseDeviceJson(String json) {
        Map<String, Long> map = new HashMap<>();
        if (json == null || json.isEmpty() || "{}".equals(json)) return map;
        try {
            String inner = json.replace("{", "").replace("}", "").replace("\"", "");
            if (!inner.isEmpty()) {
                for (String part : inner.split(",")) {
                    String[] kv = part.split(":");
                    if (kv.length == 2) {
                        try { map.put(kv[0].trim(), Long.parseLong(kv[1].trim())); }
                        catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    private List<Map.Entry<String, Long>> deviceOrdered(Map<String, Long> dm) {
        List<Map.Entry<String, Long>> list = new ArrayList<>(dm.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return list;
    }

    private String deviceCn(String en) {
        switch (en) {
            case "desktop": return "电脑端";
            case "mobile": return "手机端";
            case "tablet": return "平板端";
            default: return en;
        }
    }

    private String pageTypeCn(String en) {
        if (en == null) return "其他";
        switch (en) {
            case "HOME": return "首页";
            case "PRODUCT_DETAIL": return "商品详情";
            case "SEARCH": return "搜索页";
            case "CART": return "购物车";
            case "ORDER": return "下单结算";
            default: return "其他";
        }
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createCell(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        org.apache.poi.ss.usermodel.Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        setBorder(s);
        return s;
    }

    private CellStyle createDataStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        setBorder(s);
        return s;
    }

    private CellStyle createTitleStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        s.setFont(f);
        return s;
    }

    private CellStyle numStyle(XSSFWorkbook wb, String fmt) {
        CellStyle s = wb.createCellStyle();
        setBorder(s);
        s.setDataFormat(wb.createDataFormat().getFormat(fmt));
        return s;
    }

    private void setBorder(CellStyle s) {
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    private double dbl(Integer v) { return v != null ? v.doubleValue() : 0; }
    private double dbl(Long v) { return v != null ? v.doubleValue() : 0; }
    private BigDecimal bd(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private Long dv(ResDailyOpsSummary s, java.util.function.Function<ResDailyOpsSummary, Long> getter) {
        return s != null ? getter.apply(s) : 0L;
    }

    private Integer di(ResDailyOpsSummary s, java.util.function.Function<ResDailyOpsSummary, Integer> getter) {
        return s != null ? getter.apply(s) : 0;
    }

    private Integer di(ResDailyOrderStats s, java.util.function.Function<ResDailyOrderStats, Integer> getter) {
        return s != null ? getter.apply(s) : 0;
    }

    private BigDecimal d(ResDailyOpsSummary s, java.util.function.Function<ResDailyOpsSummary, BigDecimal> getter) {
        return s != null && getter.apply(s) != null ? getter.apply(s) : BigDecimal.ZERO;
    }

    private BigDecimal d(ResDailyOrderStats s, java.util.function.Function<ResDailyOrderStats, BigDecimal> getter) {
        return s != null && getter.apply(s) != null ? getter.apply(s) : BigDecimal.ZERO;
    }

    private String str(Object val) { return val != null ? val.toString() : "0"; }
    private String formatMoney(BigDecimal val) { return val != null ? String.format("¥%.2f", val) : "¥0.00"; }
    private String formatPercent(BigDecimal val) { return val != null ? String.format("%.2f%%", val) : "0.00%"; }
    private String formatLong(BigDecimal val) { return val != null ? String.format("%.0f", val) : "0"; }

    private ResDailyOpsSummary querySummary(LocalDate date) {
        LambdaQueryWrapper<ResDailyOpsSummary> q = new LambdaQueryWrapper<>();
        q.eq(ResDailyOpsSummary::getStatDate, date);
        return dailyOpsSummaryMapper.selectOne(q);
    }

    private ResDailyOrderStats queryOrderStats(LocalDate date) {
        LambdaQueryWrapper<ResDailyOrderStats> q = new LambdaQueryWrapper<>();
        q.eq(ResDailyOrderStats::getStatDate, date);
        return dailyOrderStatsMapper.selectOne(q);
    }

    private List<ResDailyProductStats> queryProductStats(LocalDate date) {
        LambdaQueryWrapper<ResDailyProductStats> q = new LambdaQueryWrapper<>();
        q.eq(ResDailyProductStats::getStatDate, date);
        return dailyProductStatsMapper.selectList(q);
    }

    private List<ResDailyPvStats> queryPvStats(LocalDate date) {
        LambdaQueryWrapper<ResDailyPvStats> q = new LambdaQueryWrapper<>();
        q.eq(ResDailyPvStats::getStatDate, date);
        return dailyPvStatsMapper.selectList(q);
    }
}