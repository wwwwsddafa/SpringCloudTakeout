package com.example.utils;

import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @description: 上传文件类型的校验器
 * @author: zy
 * @create: 2025-08-19 15:07
 */
public class FileTypeValidator {

    /**
     * 允许上传的文件的MIME类型( http协议中定义的文件类型 )
     */
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/jpg",
            "application/pdf",
            "application/msword", // .doc
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/vnd.ms‑excel", // .xls
            "application/vnd.openxmlformats‑officedocument.spreadsheetml.sheet" // .xlsx
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
     * 校验文件类型是否合法
     * @param contentType ： MIME类型
     * @param fileName ：文件名(主要判断文件的后缀名)
     * @return
     */
    public static boolean isValidType(String contentType, String fileName){
        if (contentType == null || fileName == null || contentType.isEmpty() || fileName.isEmpty()) {
            throw new IllegalArgumentException("文件类型或文件名不能为空");
        }
        // 校验MIME类型
        if (!ALLOWED_MIME_TYPES.contains(contentType)) {
            return false;
        }
        // 校验文件扩展名
        String fileExtension = getFileExtension(fileName);
        if (fileExtension == null || !ALLOWED_EXTENSIONS.contains(fileExtension.toLowerCase())) {
            return false;
        }
        return true;
    }


    private static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty() || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    // TODO: 用文件的魔术字节判断文件类型是否合法
    public static boolean isValidType(MultipartFile file){
        return true;
    }
}