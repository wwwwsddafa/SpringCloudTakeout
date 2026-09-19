package com.example.service;

import java.io.ByteArrayOutputStream;

public interface ReportService {

    byte[] generatePdfReport(String date);

    byte[] generateExcelReport(String date);

    ByteArrayOutputStream generatePdfReportStream(String date);

    ByteArrayOutputStream generateExcelReportStream(String date);

    void sendDailyReport(String date, String recipientEmail);
}