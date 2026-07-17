package com.example.extraction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {
    /**
     * Current storage backend. P0 uses local filesystem; later versions can extend to NAS or object storage.
     */
    private String type = "local";
    private String uploadDir = "data/uploads";
    private String artifactDir = "data/artifacts";
    private String ocrPreviewDir = "data/ocr-preview";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getArtifactDir() {
        return artifactDir;
    }

    public void setArtifactDir(String artifactDir) {
        this.artifactDir = artifactDir;
    }

    public String getOcrPreviewDir() {
        return ocrPreviewDir;
    }

    public void setOcrPreviewDir(String ocrPreviewDir) {
        this.ocrPreviewDir = ocrPreviewDir;
    }
}
