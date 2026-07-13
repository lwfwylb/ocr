package com.example.extraction.artifact.service;

import com.example.extraction.artifact.domain.DocumentArtifactRecord;
import com.example.extraction.artifact.domain.DocumentArtifactStepRecord;
import com.example.extraction.artifact.dto.DocumentArtifactResponse;
import com.example.extraction.artifact.dto.DocumentArtifactStepResponse;
import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.document.domain.DocumentAccessRecord;
import com.example.extraction.mapper.DocumentArtifactMapper;
import com.example.extraction.result.domain.DocumentParseResultRecord;
import com.example.extraction.task.domain.ExtractTaskRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class DocumentArtifactService {
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final DocumentArtifactMapper documentArtifactMapper;
    private final ObjectMapper objectMapper;
    private final Path artifactRoot;

    public DocumentArtifactService(DocumentArtifactMapper documentArtifactMapper,
                                   ObjectMapper objectMapper,
                                   @Value("${app.storage.artifact-dir:data/artifacts}") String artifactDir) {
        this.documentArtifactMapper = documentArtifactMapper;
        this.objectMapper = objectMapper;
        this.artifactRoot = Path.of(artifactDir).toAbsolutePath().normalize();
    }

    public void recordOriginal(DocumentAccessRecord access) {
        if (access == null || !StringUtils.hasText(access.getTraceId())) {
            return;
        }
        if (documentArtifactMapper.selectFirstByTraceIdAndType(access.getTraceId(), "ORIGINAL") != null) {
            return;
        }
        Path path = safePath(access.getStoragePath());
        DocumentArtifactRecord record = baseArtifact(access.getTraceId(), access.getTaskId(), access.getDocumentId());
        record.setArtifactType("ORIGINAL");
        record.setStageCode("ACCESS");
        record.setFileName(access.getFileName());
        record.setFileExt(fileExt(access.getFileName(), access.getFileType()));
        record.setMimeType(probeMimeType(path, access.getFileType()));
        record.setStoragePath(access.getStoragePath());
        record.setPreviewPath(access.getStoragePath());
        record.setFileSize(access.getFileSize());
        record.setChecksum(checksum(path));
        record.setPageRange("ALL");
        record.setSortNo(10);
        record.setStatus("SUCCESS");
        record.setMetadataJson(writeJson(Map.of(
                "sourceType", nullToDash(access.getSourceType()),
                "sourceSystem", nullToDash(access.getSourceSystem()),
                "businessNo", nullToDash(access.getBusinessNo())
        )));
        documentArtifactMapper.insertArtifact(record);
    }

    public void recordPreprocessPlan(ExtractTaskRecord task) {
        if (task == null || !StringUtils.hasText(task.getTaskId())) {
            return;
        }
        if (documentArtifactMapper.selectFirstByTaskIdAndType(task.getTaskId(), "OCR_INPUT_MANIFEST") != null) {
            return;
        }
        DocumentArtifactRecord original = documentArtifactMapper.selectFirstByTraceIdAndType(task.getTraceId(), "ORIGINAL");
        Path manifestPath = artifactPath(task, "ocr-input", "030_ocr_input_manifest.json");
        Map<String, Object> manifest = Map.of(
                "traceId", nullToDash(task.getTraceId()),
                "taskId", nullToDash(task.getTaskId()),
                "documentId", nullToDash(task.getDocumentId()),
                "fileName", nullToDash(task.getFileName()),
                "strategy", "P0_PASS_THROUGH",
                "description", "P0 records preprocess plan and OCR input manifest before real preprocess/OCR integration."
        );
        writeFile(manifestPath, writeJson(manifest));

        DocumentArtifactRecord artifact = baseArtifact(task.getTraceId(), task.getTaskId(), task.getDocumentId());
        artifact.setParentId(original == null ? null : original.getId());
        artifact.setArtifactType("OCR_INPUT_MANIFEST");
        artifact.setStageCode("PREPROCESS");
        artifact.setFileName(manifestPath.getFileName().toString());
        artifact.setFileExt("json");
        artifact.setMimeType("application/json");
        artifact.setStoragePath(manifestPath.toString());
        artifact.setPreviewPath(manifestPath.toString());
        artifact.setFileSize(size(manifestPath));
        artifact.setChecksum(checksum(manifestPath));
        artifact.setPageRange("ALL");
        artifact.setSortNo(30);
        artifact.setStatus("SUCCESS");
        artifact.setMetadataJson(writeJson(Map.of("mode", "PASS_THROUGH", "ocrInput", true)));
        documentArtifactMapper.insertArtifact(artifact);

        insertStep(task, "PREPROCESS_PLAN", "Preprocess plan", "PASS_THROUGH",
                original == null ? null : original.getId(), artifact.getId(),
                writeJson(Map.of("pageRange", "ALL", "splitMode", "NONE")), null);
    }

    public void recordOcrOutput(ExtractTaskRecord task, DocumentParseResultRecord parseResult) {
        if (task == null || parseResult == null || !StringUtils.hasText(task.getTaskId())) {
            return;
        }
        if (documentArtifactMapper.selectFirstByTaskIdAndType(task.getTaskId(), "OCR_OUTPUT_MARKDOWN") != null) {
            return;
        }
        DocumentArtifactRecord input = documentArtifactMapper.selectFirstByTaskIdAndType(task.getTaskId(), "OCR_INPUT_MANIFEST");
        Path outputPath = artifactPath(task, "ocr-output", "040_ocr_output.md");
        writeFile(outputPath, firstText(parseResult.getParseText(), ""));

        DocumentArtifactRecord artifact = baseArtifact(task.getTraceId(), task.getTaskId(), task.getDocumentId());
        artifact.setParentId(input == null ? null : input.getId());
        artifact.setArtifactType("OCR_OUTPUT_MARKDOWN");
        artifact.setStageCode("PARSE");
        artifact.setFileName(outputPath.getFileName().toString());
        artifact.setFileExt("md");
        artifact.setMimeType("text/markdown; charset=UTF-8");
        artifact.setStoragePath(outputPath.toString());
        artifact.setPreviewPath(outputPath.toString());
        artifact.setFileSize(size(outputPath));
        artifact.setChecksum(checksum(outputPath));
        artifact.setPageRange("ALL");
        artifact.setSortNo(40);
        artifact.setStatus(firstText(parseResult.getStatus(), "SUCCESS"));
        artifact.setMetadataJson(writeJson(Map.of(
                "engineCode", nullToDash(parseResult.getEngineCode()),
                "pageCount", parseResult.getPageCount() == null ? 0 : parseResult.getPageCount()
        )));
        documentArtifactMapper.insertArtifact(artifact);

        insertStep(task, "OCR_PARSE", "OCR parse output", "SIMULATED_OCR",
                input == null ? null : input.getId(), artifact.getId(),
                writeJson(Map.of("outputFormat", "markdown")), null);
    }

    public List<DocumentArtifactResponse> listByTaskId(String taskId) {
        return documentArtifactMapper.selectByTaskId(taskId).stream().map(this::toResponse).toList();
    }

    public List<DocumentArtifactResponse> listByTraceId(String traceId) {
        return documentArtifactMapper.selectByTraceId(traceId).stream().map(this::toResponse).toList();
    }

    public List<DocumentArtifactStepResponse> stepsByTaskId(String taskId) {
        return documentArtifactMapper.selectStepsByTaskId(taskId).stream().map(this::toStepResponse).toList();
    }

    public List<DocumentArtifactStepResponse> stepsByTraceId(String traceId) {
        return documentArtifactMapper.selectStepsByTraceId(traceId).stream().map(this::toStepResponse).toList();
    }

    public DocumentArtifactRecord requireArtifact(String artifactId) {
        DocumentArtifactRecord record = documentArtifactMapper.selectById(artifactId);
        if (record == null) {
            throw new BusinessException("ARTIFACT_404", "Artifact not found");
        }
        return record;
    }

    public Path requireReadablePath(String artifactId) {
        DocumentArtifactRecord record = requireArtifact(artifactId);
        if (!StringUtils.hasText(record.getStoragePath())) {
            throw new BusinessException("ARTIFACT_404", "Artifact path is empty");
        }
        Path path = Path.of(record.getStoragePath()).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BusinessException("ARTIFACT_404", "Artifact file not found");
        }
        return path;
    }

    private DocumentArtifactRecord baseArtifact(String traceId, String taskId, String documentId) {
        LocalDateTime now = LocalDateTime.now();
        DocumentArtifactRecord record = new DocumentArtifactRecord();
        record.setId(IdGenerator.nextId("ART"));
        record.setTraceId(traceId);
        record.setTaskId(taskId);
        record.setDocumentId(documentId);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private void insertStep(ExtractTaskRecord task, String stepCode, String stepName, String stepType,
                            String inputIds, String outputIds, String configJson, String errorMessage) {
        if (documentArtifactMapper.selectStepByTaskIdAndCode(task.getTaskId(), stepCode) != null) {
            return;
        }
        LocalDateTime startedAt = LocalDateTime.now().minusMillis(300);
        LocalDateTime endedAt = LocalDateTime.now();
        DocumentArtifactStepRecord step = new DocumentArtifactStepRecord();
        step.setId(IdGenerator.nextId("ASTEP"));
        step.setTraceId(task.getTraceId());
        step.setTaskId(task.getTaskId());
        step.setStepCode(stepCode);
        step.setStepName(stepName);
        step.setStepType(stepType);
        step.setInputArtifactIds(inputIds);
        step.setOutputArtifactIds(outputIds);
        step.setConfigJson(configJson);
        step.setStatus(StringUtils.hasText(errorMessage) ? "FAILED" : "SUCCESS");
        step.setErrorMessage(errorMessage);
        step.setStartedAt(startedAt);
        step.setEndedAt(endedAt);
        step.setDurationMs(Duration.between(startedAt, endedAt).toMillis());
        step.setCreatedAt(endedAt);
        documentArtifactMapper.insertStep(step);
    }

    private Path artifactPath(ExtractTaskRecord task, String stageDir, String fileName) {
        Path path = artifactRoot
                .resolve(LocalDate.now().format(DATE_PATH_FORMATTER))
                .resolve(task.getTraceId())
                .resolve(stageDir)
                .resolve(fileName)
                .normalize();
        if (!path.startsWith(artifactRoot)) {
            throw new BusinessException("ARTIFACT_400", "Invalid artifact path");
        }
        return path;
    }

    private void writeFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("ARTIFACT_500", "Artifact file save failed");
        }
    }

    private String checksum(Path path) {
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
        } catch (IOException | NoSuchAlgorithmException ignored) {
            return null;
        }
    }

    private Path safePath(String value) {
        if (!StringUtils.hasText(value) || value.contains("://")) {
            return null;
        }
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Long size(Path path) {
        try {
            return path == null || !Files.exists(path) ? null : Files.size(path);
        } catch (IOException ignored) {
            return null;
        }
    }

    private String probeMimeType(Path path, String fallbackExt) {
        try {
            if (path != null && Files.exists(path)) {
                String mime = Files.probeContentType(path);
                if (StringUtils.hasText(mime)) {
                    return mime;
                }
            }
        } catch (IOException ignored) {
        }
        return switch (firstText(fallbackExt, "").toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "txt", "csv", "md", "log" -> "text/plain; charset=UTF-8";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }

    private DocumentArtifactResponse toResponse(DocumentArtifactRecord record) {
        DocumentArtifactResponse response = new DocumentArtifactResponse();
        response.setId(record.getId());
        response.setTraceId(record.getTraceId());
        response.setTaskId(record.getTaskId());
        response.setDocumentId(record.getDocumentId());
        response.setParentId(record.getParentId());
        response.setArtifactType(record.getArtifactType());
        response.setStageCode(record.getStageCode());
        response.setFileName(record.getFileName());
        response.setFileExt(record.getFileExt());
        response.setMimeType(record.getMimeType());
        response.setFileSize(record.getFileSize());
        response.setChecksum(record.getChecksum());
        response.setPageNo(record.getPageNo());
        response.setPageRange(record.getPageRange());
        response.setSortNo(record.getSortNo());
        response.setStatus(record.getStatus());
        response.setMetadataJson(record.getMetadataJson());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    private DocumentArtifactStepResponse toStepResponse(DocumentArtifactStepRecord record) {
        DocumentArtifactStepResponse response = new DocumentArtifactStepResponse();
        response.setId(record.getId());
        response.setTraceId(record.getTraceId());
        response.setTaskId(record.getTaskId());
        response.setStepCode(record.getStepCode());
        response.setStepName(record.getStepName());
        response.setStepType(record.getStepType());
        response.setInputArtifactIds(record.getInputArtifactIds());
        response.setOutputArtifactIds(record.getOutputArtifactIds());
        response.setConfigJson(record.getConfigJson());
        response.setStatus(record.getStatus());
        response.setErrorMessage(record.getErrorMessage());
        response.setStartedAt(record.getStartedAt());
        response.setEndedAt(record.getEndedAt());
        response.setDurationMs(record.getDurationMs());
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }

    private String fileExt(String fileName, String fallback) {
        if (StringUtils.hasText(fileName) && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        }
        return firstText(fallback, "unknown");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
