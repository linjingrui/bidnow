package com.bidnow.bidnow.controller;

import com.bidnow.bidnow.common.BizException;
import com.bidnow.bidnow.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 图片上传。
 * 保存在本地 uploads/ 目录，返回访问 URL。
 * 生产环境应该用 OSS（阿里云/七牛），本地存储仅用于开发测试。
 */
@RestController
@RequestMapping("/api")
public class UploadController {

    /** 上传目录，相对于项目根 */
    @Value("${bidnow.upload.dir:uploads}")
    private String uploadDir;

    /** 允许的图片类型 */
    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB

    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BizException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException("图片大小不能超过 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BizException("只允许上传图片文件");
        }

        // 按日期分目录，避免单目录文件过多
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String fileName = UUID.randomUUID().toString().substring(0, 8) + ext;

        try {
            Path dir = Paths.get(uploadDir, dateDir);
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            file.transferTo(target.toFile());

            // 返回访问路径
            String url = "/uploads/" + dateDir + "/" + fileName;
            return Result.success(Map.of("url", url));
        } catch (IOException e) {
            throw new BizException("图片上传失败，请重试");
        }
    }
}
