package com.example.extraction.document.service;

import com.example.extraction.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class DocumentFileStorageService {
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final Path rootDir;

    public DocumentFileStorageService(@Value("${app.storage.upload-dir:data/uploads}") String uploadDir) {
        this.rootDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public StoredFile store(MultipartFile file, String traceId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_400", "上传文件不能为空");
        }
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        Path targetDir = rootDir
                .resolve(LocalDate.now().format(DATE_PATH_FORMATTER))
                .resolve(traceId)
                .normalize();
        Path targetFile = targetDir.resolve(originalFilename).normalize();
        if (!targetFile.startsWith(rootDir)) {
            throw new BusinessException("FILE_400", "文件名不合法");
        }
        try {
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("FILE_500", "文件保存失败");
        }
        return new StoredFile(originalFilename, targetFile.toString(), file.getSize());
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload.bin";
        }
        String sanitized = filename.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return sanitized.isBlank() ? "upload.bin" : sanitized;
    }

    public record StoredFile(String fileName, String storagePath, long fileSize) {
    }
}
