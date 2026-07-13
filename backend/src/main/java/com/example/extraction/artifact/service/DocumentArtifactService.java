package com.example.extraction.artifact.service;

import com.example.extraction.artifact.domain.DocumentArtifactRecord;
import com.example.extraction.artifact.domain.DocumentArtifactStepRecord;
import com.example.extraction.artifact.dto.DocumentArtifactResponse;
import com.example.extraction.artifact.dto.DocumentArtifactStepResponse;
import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.configuration.domain.ExtractConfigRecord;
import com.example.extraction.configuration.dto.ConfigWizardPayload;
import com.example.extraction.document.domain.DocumentAccessRecord;
import com.example.extraction.mapper.DocumentArtifactMapper;
import com.example.extraction.mapper.ExtractConfigMapper;
import com.example.extraction.result.domain.DocumentParseResultRecord;
import com.example.extraction.task.domain.ExtractTaskRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class DocumentArtifactService {
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final DocumentArtifactMapper documentArtifactMapper;
    private final ExtractConfigMapper extractConfigMapper;
    private final ObjectMapper objectMapper;
    private final Path artifactRoot;

    public DocumentArtifactService(DocumentArtifactMapper documentArtifactMapper,
                                   ExtractConfigMapper extractConfigMapper,
                                   ObjectMapper objectMapper,
                                   @Value("${app.storage.artifact-dir:data/artifacts}") String artifactDir) {
        this.documentArtifactMapper = documentArtifactMapper;
        this.extractConfigMapper = extractConfigMapper;
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

    public DocumentArtifactRecord recordPreprocessPlan(ExtractTaskRecord task) {
        if (task == null || !StringUtils.hasText(task.getTaskId())) {
            return null;
        }
        DocumentArtifactRecord original = documentArtifactMapper.selectFirstByTraceIdAndType(task.getTraceId(), "ORIGINAL");
        ConfigWizardPayload payload = readConfigPayload(task.getConfigId());
        PdfPreprocessResult preprocessResult = executePdfPreprocess(task, payload, original);
        DocumentArtifactRecord existing = documentArtifactMapper.selectFirstByTaskIdAndType(task.getTaskId(), "OCR_INPUT_MANIFEST");
        DocumentArtifactRecord ocrInput = preprocessResult == null ? original : preprocessResult.artifact();
        Path manifestPath = artifactPath(task, "ocr-input", "030_ocr_input_manifest.json");
        Map<String, Object> manifest = Map.of(
                "traceId", nullToDash(task.getTraceId()),
                "taskId", nullToDash(task.getTaskId()),
                "documentId", nullToDash(task.getDocumentId()),
                "fileName", nullToDash(task.getFileName()),
                "strategy", preprocessResult == null ? "P0_PASS_THROUGH" : "PDF_PAGE_FILTER",
                "ocrInputArtifactId", ocrInput == null ? "-" : nullToDash(ocrInput.getId()),
                "ocrInputFileName", ocrInput == null ? "-" : nullToDash(ocrInput.getFileName()),
                "selectedPages", preprocessResult == null ? "ALL" : preprocessResult.pageRange(),
                "description", preprocessResult == null
                        ? "P0 pass-through input before real preprocess is required."
                        : "PDF page range and keyword filter have generated a preprocessed OCR input file."
        );
        writeFile(manifestPath, writeJson(manifest));

        DocumentArtifactRecord artifact = existing == null
                ? baseArtifact(task.getTraceId(), task.getTaskId(), task.getDocumentId())
                : reuseArtifact(existing, task);
        artifact.setParentId(ocrInput == null ? null : ocrInput.getId());
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
        artifact.setMetadataJson(writeJson(Map.of(
                "mode", preprocessResult == null ? "PASS_THROUGH" : "PDF_PAGE_FILTER",
                "ocrInput", true,
                "inputArtifactId", ocrInput == null ? "-" : nullToDash(ocrInput.getId())
        )));
        saveArtifact(artifact, existing);

        insertStep(task, "PREPROCESS_PLAN", "Preprocess plan", "PASS_THROUGH",
                ocrInput == null ? null : ocrInput.getId(), artifact.getId(),
                writeJson(Map.of(
                        "pageRange", preprocessResult == null ? "ALL" : preprocessResult.pageRange(),
                        "splitMode", "NONE",
                        "realPreprocess", preprocessResult != null
                )), null);
        return artifact;
    }

    public void recordOcrOutput(ExtractTaskRecord task, DocumentParseResultRecord parseResult) {
        if (task == null || parseResult == null || !StringUtils.hasText(task.getTaskId())) {
            return;
        }
        DocumentArtifactRecord existing = documentArtifactMapper.selectFirstByTaskIdAndType(task.getTaskId(), "OCR_OUTPUT_MARKDOWN");
        DocumentArtifactRecord input = documentArtifactMapper.selectFirstByTaskIdAndType(task.getTaskId(), "OCR_INPUT_MANIFEST");
        Path outputPath = artifactPath(task, "ocr-output", "040_ocr_output.md");
        writeFile(outputPath, firstText(parseResult.getParseText(), ""));

        DocumentArtifactRecord artifact = existing == null
                ? baseArtifact(task.getTraceId(), task.getTaskId(), task.getDocumentId())
                : reuseArtifact(existing, task);
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
        saveArtifact(artifact, existing);

        insertStep(task, "OCR_PARSE", "OCR parse output", "SIMULATED_OCR",
                input == null ? null : input.getId(), artifact.getId(),
                writeJson(Map.of("outputFormat", "markdown")), null);
    }

    private PdfPreprocessResult executePdfPreprocess(ExtractTaskRecord task, ConfigWizardPayload payload, DocumentArtifactRecord original) {
        if (!isPdfTask(task) || !preprocessEnabled(payload) || !hasExecutablePdfPreprocess(payload)) {
            return null;
        }
        Path sourcePath = safePath(task.getStoragePath());
        if (sourcePath == null || !Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new BusinessException("PREPROCESS_404", "PDF source file not found");
        }
        DocumentArtifactRecord existing = documentArtifactMapper.selectFirstByTaskIdAndType(task.getTaskId(), "PREPROCESSED");
        try (PDDocument source = PDDocument.load(sourcePath.toFile())) {
            int pageCount = source.getNumberOfPages();
            if (pageCount <= 0) {
                throw new BusinessException("PREPROCESS_400", "PDF has no page");
            }
            List<Integer> selectedPages = IntStream.rangeClosed(1, pageCount).boxed().collect(Collectors.toCollection(ArrayList::new));
            List<ConfigWizardPayload.PreprocessStep> enabledSteps = enabledPreprocessSteps(payload);
            for (ConfigWizardPayload.PreprocessStep step : enabledSteps) {
                String stepType = firstText(step.getStepType(), "");
                if ("PAGE_RANGE".equals(stepType) && StringUtils.hasText(step.getPageRanges())) {
                    Set<Integer> requestedPages = parsePageRanges(step.getPageRanges(), pageCount);
                    selectedPages = selectedPages.stream().filter(requestedPages::contains).collect(Collectors.toCollection(ArrayList::new));
                } else if ("KEYWORD_FILTER".equals(stepType)) {
                    selectedPages = filterPagesByKeywords(source, selectedPages, step.getIncludeKeywords(), step.getExcludeKeywords());
                }
            }
            if (selectedPages.isEmpty()) {
                throw new BusinessException("PREPROCESS_400", "No PDF pages matched preprocess rules");
            }

            Path outputPath = artifactPath(task, "preprocess", "020_preprocessed.pdf");
            try (PDDocument output = new PDDocument()) {
                for (Integer pageNo : selectedPages) {
                    output.importPage(source.getPage(pageNo - 1));
                }
                Files.createDirectories(outputPath.getParent());
                output.save(outputPath.toFile());
            }

            String pageRange = compactPageRanges(selectedPages);
            DocumentArtifactRecord artifact = existing == null
                    ? baseArtifact(task.getTraceId(), task.getTaskId(), task.getDocumentId())
                    : reuseArtifact(existing, task);
            artifact.setParentId(original == null ? null : original.getId());
            artifact.setArtifactType("PREPROCESSED");
            artifact.setStageCode("PREPROCESS");
            artifact.setFileName(outputPath.getFileName().toString());
            artifact.setFileExt("pdf");
            artifact.setMimeType("application/pdf");
            artifact.setStoragePath(outputPath.toString());
            artifact.setPreviewPath(outputPath.toString());
            artifact.setFileSize(size(outputPath));
            artifact.setChecksum(checksum(outputPath));
            artifact.setPageRange(pageRange);
            artifact.setSortNo(20);
            artifact.setStatus("SUCCESS");
            artifact.setMetadataJson(writeJson(Map.of(
                    "sourcePageCount", pageCount,
                    "selectedPageCount", selectedPages.size(),
                    "selectedPages", selectedPages,
                    "rules", pdfPreprocessRuleSummary(enabledSteps)
            )));
            saveArtifact(artifact, existing);
            insertStep(task, "PDF_PREPROCESS", "PDF preprocess", "PAGE_RANGE_KEYWORD_FILTER",
                    original == null ? null : original.getId(), artifact.getId(),
                    artifact.getMetadataJson(), null);
            return new PdfPreprocessResult(artifact, pageRange);
        } catch (IOException e) {
            throw new BusinessException("PREPROCESS_500", "PDF preprocess failed");
        }
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
        record.setTraceId(firstText(traceId, taskId, documentId, "UNKNOWN_TRACE"));
        record.setTaskId(taskId);
        record.setDocumentId(documentId);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private DocumentArtifactRecord reuseArtifact(DocumentArtifactRecord existing, ExtractTaskRecord task) {
        DocumentArtifactRecord record = baseArtifact(task.getTraceId(), task.getTaskId(), task.getDocumentId());
        record.setId(existing.getId());
        record.setCreatedAt(existing.getCreatedAt() == null ? record.getCreatedAt() : existing.getCreatedAt());
        return record;
    }

    private void saveArtifact(DocumentArtifactRecord artifact, DocumentArtifactRecord existing) {
        if (existing == null) {
            documentArtifactMapper.insertArtifact(artifact);
        } else {
            documentArtifactMapper.updateArtifact(artifact);
        }
    }

    private void insertStep(ExtractTaskRecord task, String stepCode, String stepName, String stepType,
                            String inputIds, String outputIds, String configJson, String errorMessage) {
        if (documentArtifactMapper.selectStepByTaskIdAndCode(task.getTaskId(), stepCode) != null) {
            return;
        }
        LocalDateTime startedAt = LocalDateTime.now().minus(Duration.ofMillis(300));
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

    private ConfigWizardPayload readConfigPayload(String configId) {
        if (!StringUtils.hasText(configId)) {
            return null;
        }
        ExtractConfigRecord config = extractConfigMapper.selectById(configId);
        if (config == null || !StringUtils.hasText(config.getConfigPayload())) {
            return null;
        }
        try {
            return objectMapper.readValue(config.getConfigPayload(), ConfigWizardPayload.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private boolean preprocessEnabled(ConfigWizardPayload payload) {
        if (payload == null || enabledPreprocessSteps(payload).isEmpty()) {
            return false;
        }
        return payload.getParseConfig() == null || !Boolean.FALSE.equals(payload.getParseConfig().getPreprocessEnabled());
    }

    private List<ConfigWizardPayload.PreprocessStep> enabledPreprocessSteps(ConfigWizardPayload payload) {
        if (payload == null || payload.getPreprocessSteps() == null) {
            return List.of();
        }
        return payload.getPreprocessSteps().stream()
                .filter(step -> step != null && Boolean.TRUE.equals(step.getEnabled()))
                .toList();
    }

    private boolean hasExecutablePdfPreprocess(ConfigWizardPayload payload) {
        for (ConfigWizardPayload.PreprocessStep step : enabledPreprocessSteps(payload)) {
            String stepType = firstText(step.getStepType(), "");
            if ("PAGE_RANGE".equals(stepType) && StringUtils.hasText(step.getPageRanges())) {
                return true;
            }
            if ("KEYWORD_FILTER".equals(stepType)
                    && (!normalizedKeywords(step.getIncludeKeywords()).isEmpty() || !normalizedKeywords(step.getExcludeKeywords()).isEmpty())) {
                return true;
            }
        }
        return false;
    }

    private boolean isPdfTask(ExtractTaskRecord task) {
        String fileType = firstText(task.getFileType(), "");
        String fileName = firstText(task.getFileName(), "").toLowerCase();
        return "pdf".equalsIgnoreCase(fileType) || fileName.endsWith(".pdf");
    }

    private Set<Integer> parsePageRanges(String expression, int pageCount) {
        Set<Integer> pages = new LinkedHashSet<>();
        String normalized = expression == null ? "" : expression
                .replace('，', ',')
                .replace('；', ';')
                .replace('、', ',')
                .replaceAll("\\s+", "");
        if (!StringUtils.hasText(normalized)) {
            return IntStream.rangeClosed(1, pageCount).boxed().collect(Collectors.toCollection(LinkedHashSet::new));
        }
        String[] parts = normalized.split("[,;]");
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (part.contains("-")) {
                addPageRange(pages, part, pageCount);
            } else {
                addSinglePage(pages, part, pageCount);
            }
        }
        if (pages.isEmpty()) {
            throw new BusinessException("PREPROCESS_400", "Invalid PDF page range");
        }
        return pages;
    }

    private void addPageRange(Set<Integer> pages, String part, int pageCount) {
        String[] bounds = part.split("-", 2);
        if (bounds.length != 2) {
            return;
        }
        Integer start = parsePositiveInt(bounds[0]);
        Integer end = parsePositiveInt(bounds[1]);
        if (start == null || end == null) {
            return;
        }
        int from = Math.max(1, Math.min(start, end));
        int to = Math.min(pageCount, Math.max(start, end));
        for (int page = from; page <= to; page++) {
            pages.add(page);
        }
    }

    private void addSinglePage(Set<Integer> pages, String part, int pageCount) {
        Integer page = parsePositiveInt(part);
        if (page != null && page >= 1 && page <= pageCount) {
            pages.add(page);
        }
    }

    private Integer parsePositiveInt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            int number = Integer.parseInt(value);
            return number > 0 ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Integer> filterPagesByKeywords(PDDocument source, List<Integer> selectedPages,
                                                List<String> includeKeywords, List<String> excludeKeywords) throws IOException {
        List<String> includes = normalizedKeywords(includeKeywords);
        List<String> excludes = normalizedKeywords(excludeKeywords);
        if (includes.isEmpty() && excludes.isEmpty()) {
            return selectedPages;
        }
        PDFTextStripper stripper = new PDFTextStripper();
        List<Integer> result = new ArrayList<>();
        for (Integer pageNo : selectedPages) {
            stripper.setStartPage(pageNo);
            stripper.setEndPage(pageNo);
            String pageText = firstText(stripper.getText(source), "");
            boolean includeMatched = includes.isEmpty() || includes.stream().anyMatch(keyword -> pageText.contains(keyword));
            boolean excludeMatched = excludes.stream().anyMatch(keyword -> pageText.contains(keyword));
            if (includeMatched && !excludeMatched) {
                result.add(pageNo);
            }
        }
        return result;
    }

    private List<String> normalizedKeywords(List<String> keywords) {
        return safeList(keywords).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String compactPageRanges(List<Integer> pages) {
        if (pages == null || pages.isEmpty()) {
            return "-";
        }
        List<Integer> ordered = pages.stream().distinct().sorted().toList();
        List<String> ranges = new ArrayList<>();
        int start = ordered.get(0);
        int previous = start;
        for (int i = 1; i < ordered.size(); i++) {
            int current = ordered.get(i);
            if (current == previous + 1) {
                previous = current;
                continue;
            }
            ranges.add(start == previous ? String.valueOf(start) : start + "-" + previous);
            start = current;
            previous = current;
        }
        ranges.add(start == previous ? String.valueOf(start) : start + "-" + previous);
        return String.join(",", ranges);
    }

    private List<Map<String, Object>> pdfPreprocessRuleSummary(List<ConfigWizardPayload.PreprocessStep> steps) {
        List<Map<String, Object>> summary = new ArrayList<>();
        for (ConfigWizardPayload.PreprocessStep step : steps) {
            String stepType = firstText(step.getStepType(), "-");
            if ("PAGE_RANGE".equals(stepType)) {
                summary.add(Map.of(
                        "stepType", stepType,
                        "pageRanges", firstText(step.getPageRanges(), "ALL")
                ));
            } else if ("KEYWORD_FILTER".equals(stepType)) {
                summary.add(Map.of(
                        "stepType", stepType,
                        "includeKeywords", safeList(step.getIncludeKeywords()),
                        "excludeKeywords", safeList(step.getExcludeKeywords())
                ));
            }
        }
        return summary;
    }

    private Path artifactPath(ExtractTaskRecord task, String stageDir, String fileName) {
        String traceSegment = firstText(task.getTraceId(), task.getTaskId(), "UNKNOWN_TRACE");
        String stageSegment = firstText(stageDir, "unknown-stage");
        String safeFileName = firstText(fileName, "artifact.bin");
        Path path = artifactRoot
                .resolve(LocalDate.now().format(DATE_PATH_FORMATTER))
                .resolve(traceSegment)
                .resolve(stageSegment)
                .resolve(safeFileName)
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
        response.setPreviewUrl("/api/artifacts/" + record.getId() + "/preview");
        response.setDownloadUrl("/api/artifacts/" + record.getId() + "/download");
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

    private record PdfPreprocessResult(DocumentArtifactRecord artifact, String pageRange) {
    }
}
