package com.example.extraction.ocr;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.config.StorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OcrPreviewAssetService {
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Duration ASSET_TTL = Duration.ofHours(24);

    private final Path rootDir;
    private final Map<String, PreviewAsset> assets = new ConcurrentHashMap<>();

    public OcrPreviewAssetService(StorageProperties storageProperties) {
        this.rootDir = Path.of(storageProperties.getOcrPreviewDir()).toAbsolutePath().normalize();
    }

    public Map<String, String> saveImages(List<OcrImageArtifact> images) {
        cleanupExpired();
        Map<String, String> previewByName = new LinkedHashMap<>();
        if (images == null || images.isEmpty()) {
            return previewByName;
        }
        String batchId = IdGenerator.nextId("OPB");
        int index = 1;
        for (OcrImageArtifact image : images) {
            if (image == null || image.getContent() == null || image.getContent().length == 0) {
                continue;
            }
            String assetId = IdGenerator.nextId("OPV");
            String fileName = index + "_" + safeFileName(firstText(image.getImageName(), image.getReferenceName(), "image.jpg"));
            Path path = rootDir
                    .resolve(LocalDate.now().format(DATE_PATH_FORMATTER))
                    .resolve(batchId)
                    .resolve(assetId)
                    .resolve(fileName)
                    .normalize();
            if (!path.startsWith(rootDir)) {
                throw new BusinessException("OCR_PREVIEW_400", "OCR试识别图片路径不合法");
            }
            try {
                Files.createDirectories(path.getParent());
                Files.write(path, image.getContent());
            } catch (IOException e) {
                throw new BusinessException("OCR_PREVIEW_500", "OCR试识别图片保存失败：" + e.getMessage());
            }
            String previewUrl = "/api/model/ocr-engines/preview-assets/" + assetId;
            image.setPreviewUrl(previewUrl);
            assets.put(assetId, new PreviewAsset(assetId, path, fileName, firstText(image.getMimeType(), "image/jpeg"), LocalDateTime.now()));
            putPreviewMapping(previewByName, image.getReferenceName(), previewUrl);
            putPreviewMapping(previewByName, image.getImageName(), previewUrl);
            putPreviewMapping(previewByName, fileName, previewUrl);
            index++;
        }
        return previewByName;
    }

    public PreviewAsset requireAsset(String assetId) {
        PreviewAsset asset = assets.get(assetId);
        if (asset == null) {
            throw new BusinessException("OCR_PREVIEW_404", "OCR试识别图片不存在或已过期");
        }
        if (asset.createdAt().plus(ASSET_TTL).isBefore(LocalDateTime.now())) {
            assets.remove(assetId);
            throw new BusinessException("OCR_PREVIEW_404", "OCR试识别图片不存在或已过期");
        }
        if (!Files.exists(asset.path()) || !Files.isRegularFile(asset.path())) {
            assets.remove(assetId);
            throw new BusinessException("OCR_PREVIEW_404", "OCR试识别图片文件不存在");
        }
        return asset;
    }

    private void cleanupExpired() {
        LocalDateTime threshold = LocalDateTime.now().minus(ASSET_TTL);
        assets.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(threshold));
    }

    private void putPreviewMapping(Map<String, String> mapping, String key, String previewUrl) {
        if (StringUtils.hasText(key) && StringUtils.hasText(previewUrl)) {
            mapping.putIfAbsent(key, previewUrl);
        }
    }

    private String safeFileName(String value) {
        String text = StringUtils.hasText(value) ? value : "image.jpg";
        return text.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    public record PreviewAsset(String assetId, Path path, String fileName, String mimeType, LocalDateTime createdAt) {
    }
}
