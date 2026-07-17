package com.example.extraction.config;

import com.example.extraction.common.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class StorageStartupValidator {
    private final StorageProperties storageProperties;

    public StorageStartupValidator(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @PostConstruct
    public void validate() {
        Map<String, String> directories = new LinkedHashMap<>();
        directories.put("上传文件目录", storageProperties.getUploadDir());
        directories.put("过程产物目录", storageProperties.getArtifactDir());
        directories.put("OCR试识别预览目录", storageProperties.getOcrPreviewDir());

        for (Map.Entry<String, String> entry : directories.entrySet()) {
            validateDirectory(entry.getKey(), entry.getValue());
        }
    }

    private void validateDirectory(String name, String configuredPath) {
        Path path = Path.of(configuredPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new BusinessException("STORAGE_500", name + "创建失败：" + path + "，" + e.getMessage());
        }
        if (!Files.isDirectory(path)) {
            throw new BusinessException("STORAGE_500", name + "不是有效目录：" + path);
        }
        if (!Files.isWritable(path)) {
            throw new BusinessException("STORAGE_500", name + "不可写：" + path);
        }
    }
}
