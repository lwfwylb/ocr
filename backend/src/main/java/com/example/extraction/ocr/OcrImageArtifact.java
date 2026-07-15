package com.example.extraction.ocr;

public class OcrImageArtifact {
    private final String referenceName;
    private final String imageName;
    private final byte[] content;
    private final String mimeType;
    private String previewUrl;

    public OcrImageArtifact(String imageName, byte[] content, String mimeType) {
        this.referenceName = imageName;
        this.imageName = imageName;
        this.content = content;
        this.mimeType = mimeType;
    }

    public OcrImageArtifact(String referenceName, String imageName, byte[] content, String mimeType) {
        this.referenceName = referenceName;
        this.imageName = imageName;
        this.content = content;
        this.mimeType = mimeType;
    }

    public String getReferenceName() {
        return referenceName;
    }

    public String getImageName() {
        return imageName;
    }

    public byte[] getContent() {
        return content;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }
}
