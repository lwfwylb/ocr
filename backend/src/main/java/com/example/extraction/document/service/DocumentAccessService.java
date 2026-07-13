package com.example.extraction.document.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.artifact.service.DocumentArtifactService;
import com.example.extraction.configuration.domain.ExtractConfigRecord;
import com.example.extraction.configuration.dto.ConfigQueryRequest;
import com.example.extraction.document.domain.DocumentAccessRecord;
import com.example.extraction.document.dto.DocumentAccessQueryRequest;
import com.example.extraction.document.dto.DocumentAccessRequest;
import com.example.extraction.document.dto.DocumentAccessResponse;
import com.example.extraction.document.dto.DocumentConfirmRequest;
import com.example.extraction.mapper.DocumentAccessMapper;
import com.example.extraction.mapper.ExtractConfigMapper;
import com.example.extraction.task.service.ExtractTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DocumentAccessService {
    private final DocumentAccessMapper documentAccessMapper;
    private final ExtractConfigMapper extractConfigMapper;
    private final DocumentFileStorageService documentFileStorageService;
    private final ExtractTaskService extractTaskService;
    private final DocumentArtifactService documentArtifactService;

    public DocumentAccessService(DocumentAccessMapper documentAccessMapper,
                                 ExtractConfigMapper extractConfigMapper,
                                 DocumentFileStorageService documentFileStorageService,
                                 ExtractTaskService extractTaskService,
                                 DocumentArtifactService documentArtifactService) {
        this.documentAccessMapper = documentAccessMapper;
        this.extractConfigMapper = extractConfigMapper;
        this.documentFileStorageService = documentFileStorageService;
        this.extractTaskService = extractTaskService;
        this.documentArtifactService = documentArtifactService;
    }

    public List<DocumentAccessResponse> list(DocumentAccessQueryRequest query) {
        normalizeQuery(query);
        return documentAccessMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    public List<DocumentAccessResponse> pendingConfirm(DocumentAccessQueryRequest query) {
        normalizeQuery(query);
        return documentAccessMapper.selectPendingConfirm(query).stream().map(this::toResponse).toList();
    }

    public DocumentAccessResponse detail(String id) {
        return toResponse(requireRecord(id));
    }

    @Transactional
    public DocumentAccessResponse manualUpload(DocumentAccessRequest request) {
        request.setSourceType("MANUAL_UPLOAD");
        if (!StringUtils.hasText(request.getSourceSystem())) {
            request.setSourceSystem("\u624b\u5de5\u4e0a\u4f20");
        }
        return createAccess(request, true);
    }

    @Transactional
    public DocumentAccessResponse manualUploadFile(String configId, String businessNo, String priority, MultipartFile file) {
        if (!StringUtils.hasText(configId)) {
            throw new BusinessException("PARAM_400", "configId is required");
        }
        String traceId = nextTraceId();
        DocumentFileStorageService.StoredFile storedFile = documentFileStorageService.store(file, traceId);
        DocumentAccessRequest request = new DocumentAccessRequest();
        request.setTraceId(traceId);
        request.setConfigId(configId);
        request.setSourceType("MANUAL_UPLOAD");
        request.setSourceSystem("\u624b\u5de5\u4e0a\u4f20");
        request.setBusinessNo(businessNo);
        request.setPriority(priority);
        request.setFileName(storedFile.fileName());
        request.setFileSize(storedFile.fileSize());
        request.setStoragePath(storedFile.storagePath());
        return createAccess(request, true);
    }

    @Transactional
    public DocumentAccessResponse apiPush(DocumentAccessRequest request) {
        if (!StringUtils.hasText(request.getSourceType())) {
            request.setSourceType("BUSINESS_API");
        }
        if (!StringUtils.hasText(request.getSourceSystem())) {
            request.setSourceSystem("\u4e1a\u52a1\u7cfb\u7edfAPI");
        }
        return createAccess(request, false);
    }

    @Transactional
    public DocumentAccessResponse rematch(String id) {
        DocumentAccessRecord record = requireRecord(id);
        applyMatch(record);
        record.setUpdatedAt(LocalDateTime.now());
        documentAccessMapper.updateMatchResult(record);
        extractTaskService.createFromAccessRecord(record);
        return detail(id);
    }

    @Transactional
    public DocumentAccessResponse confirm(String id, DocumentConfirmRequest request) {
        normalizeConfirmRequest(request);
        DocumentAccessRecord record = requireRecord(id);
        if (!"PENDING_CONFIRM".equals(record.getAccessStatus())) {
            throw new BusinessException("DOC_409", "Only pending documents can be confirmed");
        }
        if (!StringUtils.hasText(request.getConfigId())) {
            throw new BusinessException("PARAM_400", "configId is required");
        }
        ExtractConfigRecord config = extractConfigMapper.selectById(request.getConfigId());
        if (config == null || !"PUBLISHED".equals(config.getStatus())) {
            throw new BusinessException("CONFIG_404", "Published config not found");
        }
        record.setCategory(firstText(request.getCategory(), config.getCategory()));
        record.setSubCategory(firstText(request.getSubCategory(), config.getSubCategory()));
        record.setTemplateType(firstText(request.getTemplateType(), config.getTemplateType()));
        record.setDocumentType(firstText(request.getDocumentType(), config.getDocumentType()));
        record.setPriority(firstText(request.getPriority(), record.getPriority(), config.getDefaultPriority(), "MEDIUM"));
        record.setMatchedConfigId(config.getId());
        record.setMatchedConfigName(config.getConfigName());
        record.setMatchedConfigVersion(config.getVersion());
        record.setMatchStatus("MATCHED");
        record.setAccessStatus("CREATED_TASK");
        record.setTaskId(nextTaskId());
        record.setMatchMessage("\u4eba\u5de5\u786e\u8ba4\u5339\u914d\u914d\u7f6e\uff1a" + config.getConfigName() + " V" + config.getVersion());
        record.setConfirmComment(request.getComment());
        record.setConfirmedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getConfirmedAt());
        int updated = documentAccessMapper.confirm(record);
        if (updated == 0) {
            throw new BusinessException("DOC_409", "Document status changed, please refresh and retry");
        }
        extractTaskService.createFromAccessRecord(record);
        return detail(id);
    }

    private DocumentAccessResponse createAccess(DocumentAccessRequest request, boolean configRequired) {
        normalizeRequest(request);
        validateRequest(request);
        DocumentAccessRecord record = new DocumentAccessRecord();
        record.setId(IdGenerator.nextId("DAR"));
        record.setTraceId(StringUtils.hasText(request.getTraceId()) ? request.getTraceId() : nextTraceId());
        record.setDocumentId(IdGenerator.nextId("DOC"));
        record.setFileName(request.getFileName());
        record.setFileType(resolveFileType(request));
        record.setFileSize(request.getFileSize());
        record.setStoragePath(firstText(request.getStoragePath(), "mock://" + request.getFileName()));
        record.setSourceType(request.getSourceType());
        record.setSourceSystem(request.getSourceSystem());
        record.setBusinessNo(request.getBusinessNo());
        record.setDepartmentId(request.getDepartmentId());
        record.setCategory(request.getCategory());
        record.setSubCategory(request.getSubCategory());
        record.setTemplateType(request.getTemplateType());
        record.setDocumentType(request.getDocumentType());
        record.setPriority(request.getPriority());
        record.setCreatedBy("system");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        if (StringUtils.hasText(request.getConfigId())) {
            applySpecifiedConfig(record, request.getConfigId());
        } else if (configRequired) {
            throw new BusinessException("PARAM_400", "configId is required");
        } else {
            applyMatch(record);
        }
        documentAccessMapper.insert(record);
        documentArtifactService.recordOriginal(record);
        extractTaskService.createFromAccessRecord(record);
        return detail(record.getId());
    }

    private void normalizeQuery(DocumentAccessQueryRequest query) {
        if (query == null) {
            return;
        }
        query.setDepartmentId(normalizeDepartment(query.getDepartmentId()));
    }

    private void normalizeRequest(DocumentAccessRequest request) {
        request.setDepartmentId(normalizeDepartment(request.getDepartmentId()));
        request.setCategory(normalizeCategory(request.getCategory()));
        request.setSubCategory(normalizeDocumentType(request.getSubCategory()));
        request.setTemplateType(normalizeTemplateType(request.getTemplateType()));
        request.setDocumentType(normalizeDocumentType(request.getDocumentType()));
    }

    private void normalizeConfirmRequest(DocumentConfirmRequest request) {
        request.setCategory(normalizeCategory(request.getCategory()));
        request.setSubCategory(normalizeDocumentType(request.getSubCategory()));
        request.setTemplateType(normalizeTemplateType(request.getTemplateType()));
        request.setDocumentType(normalizeDocumentType(request.getDocumentType()));
    }

    private void validateRequest(DocumentAccessRequest request) {
        if (!StringUtils.hasText(request.getFileName())) {
            throw new BusinessException("PARAM_400", "fileName is required");
        }
        if (!StringUtils.hasText(request.getDepartmentId()) && !StringUtils.hasText(request.getConfigId())) {
            throw new BusinessException("PARAM_400", "departmentId is required");
        }
        if (!StringUtils.hasText(request.getSourceType())) {
            throw new BusinessException("PARAM_400", "sourceType is required");
        }
    }

    private void applyMatch(DocumentAccessRecord record) {
        ConfigQueryRequest query = new ConfigQueryRequest();
        query.setStatus("PUBLISHED");
        query.setDepartmentId(record.getDepartmentId());
        query.setCategory(record.getCategory());
        query.setSubCategory(record.getSubCategory());
        query.setTemplateType(record.getTemplateType());
        query.setDocumentType(record.getDocumentType());
        List<ExtractConfigRecord> candidates = extractConfigMapper.selectList(query);
        if (candidates.size() == 1) {
            ExtractConfigRecord config = candidates.get(0);
            record.setMatchedConfigId(config.getId());
            record.setMatchedConfigName(config.getConfigName());
            record.setMatchedConfigVersion(config.getVersion());
            record.setPriority(firstText(record.getPriority(), config.getDefaultPriority(), "MEDIUM"));
            record.setMatchStatus("MATCHED");
            record.setAccessStatus("CREATED_TASK");
            record.setTaskId(nextTaskId());
            record.setMatchMessage("\u81ea\u52a8\u5339\u914d\u5230\u751f\u6548\u914d\u7f6e\uff1a" + config.getConfigName() + " V" + config.getVersion());
            return;
        }
        record.setMatchedConfigId(null);
        record.setMatchedConfigName(null);
        record.setMatchedConfigVersion(null);
        record.setTaskId(null);
        record.setAccessStatus("PENDING_CONFIRM");
        record.setPriority(firstText(record.getPriority(), "MEDIUM"));
        if (candidates.isEmpty()) {
            record.setMatchStatus("UNMATCHED");
            record.setMatchMessage("\u672a\u5339\u914d\u5230\u5df2\u53d1\u5e03\u914d\u7f6e\uff0c\u9700\u8981\u4eba\u5de5\u786e\u8ba4");
        } else {
            record.setMatchStatus("MULTIPLE");
            record.setMatchMessage("\u547d\u4e2d\u591a\u4e2a\u5df2\u53d1\u5e03\u914d\u7f6e\uff0c\u8bf7\u4eba\u5de5\u9009\u62e9\u5177\u4f53\u914d\u7f6e");
        }
    }

    private void applySpecifiedConfig(DocumentAccessRecord record, String configId) {
        ExtractConfigRecord config = extractConfigMapper.selectById(configId);
        if (config == null || !"PUBLISHED".equals(config.getStatus())) {
            throw new BusinessException("CONFIG_404", "Published config not found");
        }
        record.setDepartmentId(config.getDepartmentId());
        record.setCategory(config.getCategory());
        record.setSubCategory(config.getSubCategory());
        record.setTemplateType(config.getTemplateType());
        record.setDocumentType(config.getDocumentType());
        record.setPriority(firstText(record.getPriority(), config.getDefaultPriority(), "MEDIUM"));
        record.setMatchedConfigId(config.getId());
        record.setMatchedConfigName(config.getConfigName());
        record.setMatchedConfigVersion(config.getVersion());
        record.setMatchStatus("MATCHED");
        record.setAccessStatus("CREATED_TASK");
        record.setTaskId(nextTaskId());
        record.setMatchMessage("\u624b\u5de5\u4e0a\u4f20\u6307\u5b9a\u751f\u6548\u914d\u7f6e\uff1a" + config.getConfigName() + " V" + config.getVersion());
    }

    private DocumentAccessRecord requireRecord(String id) {
        DocumentAccessRecord record = documentAccessMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("DOC_404", "Document access record not found");
        }
        return record;
    }

    private String resolveFileType(DocumentAccessRequest request) {
        if (StringUtils.hasText(request.getFileType())) {
            return request.getFileType();
        }
        String fileName = request.getFileName();
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "unknown";
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    private String normalizeDepartment(String value) {
        String key = firstText(value);
        if (key == null) {
            return value;
        }
        return switch (key) {
            case "OPS" -> "\u8fd0\u8425\u90e8";
            case "FINANCE" -> "\u8d22\u52a1\u90e8";
            case "PRODUCT" -> "\u4ea7\u54c1\u90e8";
            default -> value;
        };
    }

    private String normalizeCategory(String value) {
        String key = firstText(value);
        if (key == null) {
            return value;
        }
        return switch (key) {
            case "FUND_BUSINESS" -> "\u8d44\u91d1\u4e1a\u52a1";
            case "FUND_TRADE" -> "\u57fa\u91d1\u4ea4\u6613";
            case "CUSTOMER_BUSINESS" -> "\u5ba2\u6237\u4e1a\u52a1";
            default -> value;
        };
    }

    private String normalizeDocumentType(String value) {
        String key = firstText(value);
        if (key == null) {
            return value;
        }
        return switch (key) {
            case "PAYMENT_INSTRUCTION" -> "\u5212\u6b3e\u6307\u4ee4";
            case "BANK_RECEIPT" -> "\u94f6\u884c\u56de\u5355";
            case "ACCOUNT_OPENING" -> "\u5f00\u6237\u8d44\u6599";
            default -> value;
        };
    }

    private String normalizeTemplateType(String value) {
        String key = firstText(value);
        if (key == null) {
            return value;
        }
        return switch (key) {
            case "GENERAL_PAYMENT_INSTRUCTION_TEMPLATE" -> "\u901a\u7528\u5212\u6b3e\u6307\u4ee4\u6a21\u677f";
            case "GENERAL_BANK_RECEIPT_TEMPLATE" -> "\u901a\u7528\u94f6\u884c\u56de\u5355\u6a21\u677f";
            default -> value;
        };
    }

    private String nextTraceId() {
        return IdGenerator.nextId("TRACE");
    }

    private String nextTaskId() {
        return IdGenerator.nextId("TASK");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private DocumentAccessResponse toResponse(DocumentAccessRecord record) {
        DocumentAccessResponse response = new DocumentAccessResponse();
        response.setId(record.getId());
        response.setTraceId(record.getTraceId());
        response.setDocumentId(record.getDocumentId());
        response.setTaskId(record.getTaskId());
        response.setFileName(record.getFileName());
        response.setFileType(record.getFileType());
        response.setFileSize(record.getFileSize());
        response.setStoragePath(record.getStoragePath());
        response.setSourceType(record.getSourceType());
        response.setSourceSystem(record.getSourceSystem());
        response.setBusinessNo(record.getBusinessNo());
        response.setDepartmentId(record.getDepartmentId());
        response.setCategory(record.getCategory());
        response.setSubCategory(record.getSubCategory());
        response.setTemplateType(record.getTemplateType());
        response.setDocumentType(record.getDocumentType());
        response.setPriority(record.getPriority());
        response.setMatchStatus(record.getMatchStatus());
        response.setAccessStatus(record.getAccessStatus());
        response.setMatchedConfigId(record.getMatchedConfigId());
        response.setMatchedConfigName(record.getMatchedConfigName());
        response.setMatchedConfigVersion(record.getMatchedConfigVersion());
        response.setMatchMessage(record.getMatchMessage());
        response.setConfirmComment(record.getConfirmComment());
        response.setConfirmedAt(record.getConfirmedAt());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }
}
