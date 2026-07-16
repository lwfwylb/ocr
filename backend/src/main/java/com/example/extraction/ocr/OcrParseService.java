package com.example.extraction.ocr;

import com.example.extraction.artifact.domain.DocumentArtifactRecord;
import com.example.extraction.artifact.service.DocumentArtifactService;
import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.configuration.domain.ExtractConfigRecord;
import com.example.extraction.configuration.dto.ConfigWizardPayload;
import com.example.extraction.mapper.DocumentArtifactMapper;
import com.example.extraction.mapper.DocumentParseResultMapper;
import com.example.extraction.mapper.ExtractConfigMapper;
import com.example.extraction.mapper.OcrEngineConfigMapper;
import com.example.extraction.model.domain.OcrEngineConfigRecord;
import com.example.extraction.result.domain.DocumentParseResultRecord;
import com.example.extraction.task.domain.ExtractTaskRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class OcrParseService {
    private final OcrEngineConfigMapper ocrEngineConfigMapper;
    private final ExtractConfigMapper extractConfigMapper;
    private final DocumentArtifactMapper documentArtifactMapper;
    private final DocumentParseResultMapper documentParseResultMapper;
    private final DocumentArtifactService documentArtifactService;
    private final OcrPreviewAssetService ocrPreviewAssetService;
    private final ObjectMapper objectMapper;
    private final List<OcrEngineClient> clients;

    public OcrParseService(OcrEngineConfigMapper ocrEngineConfigMapper,
                           ExtractConfigMapper extractConfigMapper,
                           DocumentArtifactMapper documentArtifactMapper,
                           DocumentParseResultMapper documentParseResultMapper,
                           DocumentArtifactService documentArtifactService,
                           OcrPreviewAssetService ocrPreviewAssetService,
                           ObjectMapper objectMapper,
                           List<OcrEngineClient> clients) {
        this.ocrEngineConfigMapper = ocrEngineConfigMapper;
        this.extractConfigMapper = extractConfigMapper;
        this.documentArtifactMapper = documentArtifactMapper;
        this.documentParseResultMapper = documentParseResultMapper;
        this.documentArtifactService = documentArtifactService;
        this.ocrPreviewAssetService = ocrPreviewAssetService;
        this.objectMapper = objectMapper;
        this.clients = clients;
    }

    public OcrTaskParseResult parseAndSave(ExtractTaskRecord task) {
        if (task == null || !StringUtils.hasText(task.getTaskId())) {
            throw new BusinessException("TASK_404", "任务不存在");
        }
        ConfigWizardPayload payload = readConfigPayload(task.getConfigId());
        DocumentArtifactRecord inputArtifact = resolveOcrInputArtifact(task);
        Path inputPath = resolveInputPath(task, inputArtifact);
        if (isTextFile(task, inputArtifact)) {
            return saveDirectTextParse(task, inputArtifact, inputPath);
        }

        OcrEngineConfigRecord engine = resolveEngine(payload);
        OcrEngineClient client = clientFor(engine);
        ConfigWizardPayload requestPayload = mergeEngineParams(payload, engine);

        OcrParseRequest request = new OcrParseRequest(task, engine, requestPayload, inputPath,
                firstText(inputArtifact == null ? null : inputArtifact.getFileName(), task.getFileName(), inputPath.getFileName().toString()),
                firstText(inputArtifact == null ? null : inputArtifact.getFileExt(), task.getFileType(), fileExt(inputPath.getFileName().toString())));
        OcrParseResponse response = client.parse(request);
        documentArtifactService.recordOcrRawResponse(task, response.getRawJson());
        Map<String, String> imageUrls = documentArtifactService.recordOcrImages(task, response.getImages());
        String markdownText = replaceImageReferences(response.getMarkdownText(), imageUrls);
        response.setMarkdownText(markdownText);

        DocumentParseResultRecord record = saveParseRecord(task, response.getEngineCode(), markdownText, null,
                firstPositive(response.getPageCount(), pageCount(inputArtifact)), "SUCCESS");
        DocumentArtifactRecord markdownArtifact = documentArtifactService.recordOcrOutput(task, record);
        if (markdownArtifact != null) {
            record.setParseMarkdownPath(markdownArtifact.getStoragePath());
            record.setUpdatedAt(LocalDateTime.now());
            documentParseResultMapper.update(record);
        }
        return new OcrTaskParseResult(record, engine, response.getDurationMs(), false);
    }

    public OcrParseResponse parsePreview(OcrEngineConfigRecord engine, MultipartFile file) {
        if (engine == null) {
            throw new BusinessException("OCR_404", "OCR 引擎配置不存在");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("OCR_TEST_400", "请上传样本文档");
        }
        if (isMinerUEngine(engine) && !isMinerUSupportedFile(file)) {
            throw new BusinessException("OCR_TEST_400", "MinerU 试识别支持 PDF、常见图片、DOCX、PPTX、XLSX，请上传支持的样本文档");
        }
        if (isPaddleOcrVlEngine(engine) && !isPaddleSupportedFile(file)) {
            throw new BusinessException("OCR_TEST_400", "PaddleOCR-VL 试识别支持 PDF、JPG、JPEG、PNG、BMP、TIF、TIFF，请上传支持的样本文档");
        }
        OcrEngineClient client = clientFor(engine);
        ConfigWizardPayload payload = mergeEngineParams(new ConfigWizardPayload(), engine);
        Path tempPath = null;
        try {
            tempPath = Files.createTempFile("ocr-test-", "-" + safeFileName(file.getOriginalFilename()));
            file.transferTo(tempPath);
            ExtractTaskRecord task = new ExtractTaskRecord();
            task.setTaskId("OCR_TEST");
            task.setTraceId("OCR_TEST");
            task.setDocumentId("OCR_TEST");
            task.setFileName(file.getOriginalFilename());
            task.setFileType(fileExt(file.getOriginalFilename()));
            OcrParseRequest request = new OcrParseRequest(task, engine, payload, tempPath,
                    firstText(file.getOriginalFilename(), tempPath.getFileName().toString()),
                    firstText(fileExt(file.getOriginalFilename()), contentTypeExt(file.getContentType())));
            OcrParseResponse response = client.parse(request);
            Map<String, String> imageUrls = ocrPreviewAssetService.saveImages(response.getImages());
            response.setMarkdownText(replaceImageReferences(response.getMarkdownText(), imageUrls));
            return response;
        } catch (IOException e) {
            throw new BusinessException("OCR_TEST_IO_ERROR", "样本文档读取失败：" + e.getMessage());
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private OcrTaskParseResult saveDirectTextParse(ExtractTaskRecord task, DocumentArtifactRecord inputArtifact, Path inputPath) {
        long begin = System.currentTimeMillis();
        String text;
        try {
            text = Files.readString(inputPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("PARSE_TEXT_ERROR", "文本文件读取失败：" + e.getMessage());
        }
        String markdown = "# 文本文件解析结果\n\n"
                + "- 任务编号: " + task.getTaskId() + "\n"
                + "- 文件名: " + firstText(task.getFileName(), inputArtifact == null ? null : inputArtifact.getFileName(), "-") + "\n"
                + "- 文档类型: " + firstText(task.getDocumentType(), task.getFileType(), "-") + "\n\n"
                + text;
        DocumentParseResultRecord record = saveParseRecord(task, "DIRECT_TEXT_PARSER", markdown, null, 1, "SUCCESS");
        DocumentArtifactRecord markdownArtifact = documentArtifactService.recordOcrOutput(task, record);
        if (markdownArtifact != null) {
            record.setParseMarkdownPath(markdownArtifact.getStoragePath());
            record.setUpdatedAt(LocalDateTime.now());
            documentParseResultMapper.update(record);
        }
        return new OcrTaskParseResult(record, null, System.currentTimeMillis() - begin, true);
    }

    private DocumentParseResultRecord saveParseRecord(ExtractTaskRecord task, String engineCode, String parseText,
                                                      String parseMarkdownPath, Integer pageCount, String status) {
        DocumentParseResultRecord existing = documentParseResultMapper.selectByTaskId(task.getTaskId());
        LocalDateTime now = LocalDateTime.now();
        DocumentParseResultRecord record = existing == null ? new DocumentParseResultRecord() : existing;
        if (existing == null) {
            record.setId(IdGenerator.nextId("DPR"));
            record.setCreatedAt(now);
        }
        record.setTaskId(task.getTaskId());
        record.setTraceId(task.getTraceId());
        record.setDocumentId(task.getDocumentId());
        record.setEngineCode(engineCode);
        record.setParseText(firstText(parseText, ""));
        record.setParseMarkdownPath(parseMarkdownPath);
        record.setPageCount(pageCount == null || pageCount <= 0 ? 1 : pageCount);
        record.setStatus(status);
        record.setUpdatedAt(now);
        if (existing == null) {
            documentParseResultMapper.insert(record);
        } else {
            documentParseResultMapper.update(record);
        }
        return record;
    }

    private OcrEngineClient clientFor(OcrEngineConfigRecord engine) {
        return clients.stream()
                .filter(item -> item.supports(engine.getAdapterType(), engine.getEngineType(), engine.getProvider(), engine.getEngineCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("OCR_400", "暂不支持该 OCR 引擎适配器：" + firstText(engine.getAdapterType(), engine.getEngineType(), engine.getProvider(), engine.getEngineCode(), "未知")));
    }

    private OcrEngineConfigRecord resolveEngine(ConfigWizardPayload payload) {
        String configuredEngineCode = payload == null || payload.getParseConfig() == null ? null : payload.getParseConfig().getEngineCode();
        OcrEngineConfigRecord engine = StringUtils.hasText(configuredEngineCode)
                ? ocrEngineConfigMapper.selectByEngineCode(configuredEngineCode)
                : ocrEngineConfigMapper.selectEnabled().stream().findFirst().orElse(null);
        if (engine == null) {
            throw new BusinessException("OCR_404", "未找到可用 OCR 引擎，请先在模型中心维护并启用 OCR 引擎");
        }
        if (!"ENABLED".equals(engine.getStatus())) {
            throw new BusinessException("OCR_409", "当前配置的 OCR 引擎未启用：" + engine.getEngineName());
        }
        if (!StringUtils.hasText(engine.getBaseUrl())) {
            throw new BusinessException("OCR_400", "OCR 引擎服务地址为空：" + engine.getEngineName());
        }
        return engine;
    }

    private DocumentArtifactRecord resolveOcrInputArtifact(ExtractTaskRecord task) {
        DocumentArtifactRecord preprocessed = documentArtifactMapper.selectFirstByTaskIdAndType(task.getTaskId(), "PREPROCESSED");
        if (preprocessed != null) {
            return preprocessed;
        }
        DocumentArtifactRecord original = documentArtifactMapper.selectFirstByTraceIdAndType(task.getTraceId(), "ORIGINAL");
        if (original != null) {
            return original;
        }
        return null;
    }

    private Path resolveInputPath(ExtractTaskRecord task, DocumentArtifactRecord artifact) {
        String pathValue = firstText(artifact == null ? null : artifact.getStoragePath(), task.getStoragePath());
        if (!StringUtils.hasText(pathValue) || pathValue.contains("://")) {
            throw new BusinessException("OCR_INPUT_404", "OCR 输入文件路径为空");
        }
        Path path = Path.of(pathValue).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BusinessException("OCR_INPUT_404", "OCR 输入文件不存在：" + path.getFileName());
        }
        return path;
    }

    private String replaceImageReferences(String markdownText, Map<String, String> imageUrls) {
        String result = firstText(markdownText, "");
        if (imageUrls == null || imageUrls.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, String> entry : imageUrls.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
                continue;
            }
            result = result.replace("images/" + key, value).replace(key, value);
        }
        return result;
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
            throw new BusinessException("CONFIG_PAYLOAD_ERROR", "配置内容解析失败");
        }
    }

    private ConfigWizardPayload mergeEngineParams(ConfigWizardPayload payload, OcrEngineConfigRecord engine) {
        if (!StringUtils.hasText(engine.getEngineParamsJson())) {
            return payload;
        }
        try {
            Map<String, Object> defaults = objectMapper.readValue(engine.getEngineParamsJson(), new TypeReference<>() {});
            if (defaults.isEmpty()) {
                return payload;
            }
            ConfigWizardPayload target = payload == null ? new ConfigWizardPayload() : payload;
            if (target.getParseConfig() == null) {
                target.setParseConfig(new ConfigWizardPayload.ParseConfig());
            }
            Map<String, Object> merged = new java.util.LinkedHashMap<>(defaults);
            if (target.getParseConfig().getEngineParams() != null) {
                merged.putAll(target.getParseConfig().getEngineParams());
            }
            target.getParseConfig().setEngineParams(merged);
            return target;
        } catch (JsonProcessingException e) {
            throw new BusinessException("OCR_PARAMS_ERROR", "OCR 引擎默认参数 JSON 不合法");
        }
    }

    private boolean isTextFile(ExtractTaskRecord task, DocumentArtifactRecord artifact) {
        String fileType = firstText(artifact == null ? null : artifact.getFileExt(), task.getFileType(), "").toLowerCase();
        String fileName = firstText(artifact == null ? null : artifact.getFileName(), task.getFileName(), "").toLowerCase();
        return List.of("txt", "csv", "md", "log").contains(fileType)
                || fileName.endsWith(".txt")
                || fileName.endsWith(".csv")
                || fileName.endsWith(".md")
                || fileName.endsWith(".log");
    }

    private Integer firstPositive(Integer... values) {
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return 1;
    }

    private Integer pageCount(DocumentArtifactRecord artifact) {
        if (artifact == null || !StringUtils.hasText(artifact.getPageRange()) || "ALL".equalsIgnoreCase(artifact.getPageRange())) {
            return 1;
        }
        int count = 0;
        for (String part : artifact.getPageRange().split(",")) {
            String trimmed = part.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (trimmed.contains("-")) {
                String[] bounds = trimmed.split("-", 2);
                Integer start = parseInt(bounds[0]);
                Integer end = parseInt(bounds[1]);
                if (start != null && end != null) {
                    count += Math.abs(end - start) + 1;
                }
            } else if (parseInt(trimmed) != null) {
                count++;
            }
        }
        return Math.max(count, 1);
    }

    private Integer parseInt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String fileExt(String fileName) {
        if (StringUtils.hasText(fileName) && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        }
        return null;
    }

    private String contentTypeExt(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        contentType = contentType.toLowerCase();
        if (contentType.contains("pdf")) {
            return "pdf";
        }
        if (contentType.contains("wordprocessingml") || contentType.contains("msword")) {
            return "docx";
        }
        if (contentType.contains("presentationml") || contentType.contains("powerpoint")) {
            return "pptx";
        }
        if (contentType.contains("spreadsheetml") || contentType.contains("excel")) {
            return "xlsx";
        }
        if (contentType.contains("jp2") || contentType.contains("jpeg2000")) {
            return "jp2";
        }
        if (contentType.contains("webp")) {
            return "webp";
        }
        if (contentType.contains("gif")) {
            return "gif";
        }
        if (contentType.contains("bmp")) {
            return "bmp";
        }
        if (contentType.contains("tiff") || contentType.contains("tif")) {
            return "tiff";
        }
        if (contentType.contains("png")) {
            return "png";
        }
        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            return "jpg";
        }
        return null;
    }

    private String safeFileName(String fileName) {
        String value = StringUtils.hasText(fileName) ? fileName : "sample.pdf";
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private boolean isMinerUSupportedFile(MultipartFile file) {
        String ext = firstText(fileExt(file.getOriginalFilename()), contentTypeExt(file.getContentType()));
        return List.of("pdf", "png", "jpg", "jpeg", "jp2", "webp", "gif", "bmp", "tif", "tiff", "docx", "pptx", "xlsx")
                .contains(ext == null ? "" : ext.toLowerCase());
    }

    private boolean isPaddleSupportedFile(MultipartFile file) {
        String ext = firstText(fileExt(file.getOriginalFilename()), contentTypeExt(file.getContentType()));
        return List.of("pdf", "jpg", "jpeg", "png", "bmp", "tif", "tiff").contains(ext == null ? "" : ext.toLowerCase());
    }

    private boolean isMinerUEngine(OcrEngineConfigRecord engine) {
        String joined = (firstText(engine.getAdapterType(), "") + " "
                + firstText(engine.getEngineType(), "") + " "
                + firstText(engine.getProvider(), "") + " "
                + firstText(engine.getEngineCode(), "")).toLowerCase();
        return joined.contains("mineru") || joined.contains("miner_u");
    }

    private boolean isPaddleOcrVlEngine(OcrEngineConfigRecord engine) {
        String joined = (firstText(engine.getAdapterType(), "") + " "
                + firstText(engine.getEngineType(), "") + " "
                + firstText(engine.getProvider(), "") + " "
                + firstText(engine.getEngineCode(), "")).toLowerCase();
        return joined.contains("paddle") || joined.contains("paddleocr") || joined.contains("paddle_ocr_vl");
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
