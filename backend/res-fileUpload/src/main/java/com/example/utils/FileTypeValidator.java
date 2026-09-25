package com.example.utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @description: 上传文件类型的校验器
 * <p>
 * 三层防御体系：
 *   第一层：MIME类型 + 扩展名白名单（快速过滤）
 *   第二层：文件魔术字节校验（防伪造，核心安全保障）
 *   第三层：扩展名与魔术字节交叉验证（防 .docx 改名为 .xlsx 绕过）
 * <p>
 * 面试话术：
 *   「文件上传校验不止依赖客户端上报的 Content-Type 和扩展名（都可以伪造），
 *     核心防线是读取文件头的魔术字节来判定真实类型。
 *     比如攻击者把 webshell 后缀改为 .jpg，但文件头不是 FF D8 FF，
 *     在校验层就被拦截了。」
 *
 * @author: zy
 * @create: 2025-08-19 15:07
 */
public class FileTypeValidator {

    /**
     * 允许上传的文件的MIME类型（HTTP协议中定义的文件类型）
     */
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/jpg",
            "application/pdf",
            "application/msword", // .doc
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/vnd.ms-excel", // .xls
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" // .xlsx
    ));

    /**
     * 允许上传的文件的后缀名
     */
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif",
            "pdf",
            "doc", "docx",
            "xls", "xlsx"
    ));

    /**
     * 扩展名 → 文件头魔术字节映射
     * 使用 LinkedHashMap 保持插入顺序，便于调试
     *
     * 魔术字节参考：
     *   JPEG:  FF D8 FF
     *   PNG:   89 50 4E 47 0D 0A 1A 0A
     *   GIF:   47 49 46 38 (GIF87a / GIF89a)
     *   PDF:   25 50 44 46 (%PDF)
     *   DOC/XLS:  D0 CF 11 E0 A1 B1 1A E1 (OLE2 复合文档)
     *   DOCX/XLSX: 50 4B 03 04 (ZIP 压缩包，Office Open XML)
     */
    private static final Map<String, byte[]> MAGIC_BYTES_MAP = new LinkedHashMap<>();

    static {
        MAGIC_BYTES_MAP.put("jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        MAGIC_BYTES_MAP.put("jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        MAGIC_BYTES_MAP.put("png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        MAGIC_BYTES_MAP.put("gif", new byte[]{0x47, 0x49, 0x46, 0x38});
        MAGIC_BYTES_MAP.put("pdf", new byte[]{0x25, 0x50, 0x44, 0x46});
        // OLE2 格式（.doc 和 .xls 共用）
        MAGIC_BYTES_MAP.put("doc", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
        MAGIC_BYTES_MAP.put("xls", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
        // ZIP 格式（.docx 和 .xlsx 共用 Office Open XML）
        MAGIC_BYTES_MAP.put("docx", new byte[]{0x50, 0x4B, 0x03, 0x04});
        MAGIC_BYTES_MAP.put("xlsx", new byte[]{0x50, 0x4B, 0x03, 0x04});
    }

    /**
     * 第一层校验：MIME类型 + 扩展名白名单
     *
     * @param contentType MIME类型
     * @param fileName    文件名（用于判断后缀名）
     * @return true 第一层通过
     */
    public static boolean isValidType(String contentType, String fileName) {
        if (contentType == null || fileName == null || contentType.isEmpty() || fileName.isEmpty()) {
            return false;
        }
        if (!ALLOWED_MIME_TYPES.contains(contentType)) {
            return false;
        }
        String fileExtension = getFileExtension(fileName);
        if (fileExtension == null || !ALLOWED_EXTENSIONS.contains(fileExtension.toLowerCase())) {
            return false;
        }
        return true;
    }

    /**
     * 第二/三层防线：通过文件魔术字节校验真实文件类型，并与扩展名交叉验证。
     * <p>
     * 这是文件上传安全的核心防线。MIME 和扩展名都可以被攻击者伪造，
     * 但魔术字节是文件内容的「基因」，无法伪造。
     * <p>
     * 验证逻辑：
     *   1. 读取文件头部的魔术字节
     *   2. 查表获取该扩展名对应的期望魔术字节
     *   3. 逐字节比对，不匹配则拒绝
     *   4. 对于共用魔术字节的格式（如 .doc/.xls 都是 OLE2），
     *      因为步骤2已经按扩展名做了匹配，所以不会出现 .doc 伪装成 .xls 的情况
     *
     * @param file MultipartFile 文件对象
     * @return true 文件真实类型与扩展名一致
     */
    public static boolean isValidType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        // 1. 获取扩展名对应的期望魔术字节
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        String extension = getFileExtension(fileName);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            return false;
        }
        byte[] expectedMagic = MAGIC_BYTES_MAP.get(extension.toLowerCase());
        if (expectedMagic == null) {
            return false;
        }

        // 2. 读取文件头部字节
        byte[] header = new byte[expectedMagic.length];

        // Spring 的 StandardMultipartFile 每次调用 getInputStream() 都会返回新的流，
        // 所以这里的读取不会影响后续的文件处理
        try (InputStream is = file.getInputStream()) {
            int bytesRead = is.read(header, 0, header.length);
            if (bytesRead < header.length) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }

        // 3. 逐字节比对
        for (int i = 0; i < expectedMagic.length; i++) {
            if (header[i] != expectedMagic[i]) {
                return false;
            }
        }

        return true;
    }

    private static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty() || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}