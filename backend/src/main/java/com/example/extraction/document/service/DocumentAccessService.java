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
            request.setSourceSystem("手工上传");
        }
        return createAccess(request, true, true);
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
        request.setSourceSystem("手工上传");
        request.setBusinessNo(businessNo);
        request.setPriority(priority);
        request.setFileName(storedFile.fileName());
        request.setFileSize(storedFile.fileSize());
        request.setStoragePath(storedFile.storagePath());
        return createAccess(request, true, true);
    }

    @Transactional
    public DocumentAccessResponse apiPush(DocumentAccessRequest request) {
        if (!StringUtils.hasText(request.getSourceType())) {
            request.setSourceType("BUSINESS_API");
        }
        if (!StringUtils.hasText(request.getSourceSystem())) {
            request.setSourceSystem("业务系统API");
        }
        return createAccess(request, false, false);
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
        record.setMatchMessage("人工确认匹配配置：" + config.getConfigName() + " V" + config.getVersion());
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

    private DocumentAccessResponse createAccess(DocumentAccessRequest request, boolean configRequired, boolean allowDraftConfig) {
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
            applySpecifiedConfig(record, request.getConfigId(), allowDraftConfig);
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
            record.setMatchMessage("自动匹配到生效配置：" + config.getConfigName() + " V" + config.getVersion());
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
            record.setMatchMessage("未匹配到已发布配置，需要人工确认");
        } else {
            record.setMatchStatus("MULTIPLE");
            record.setMatchMessage("命中多个已发布配置，请人工选择具体配置");
        }
    }

    private void applySpecifiedConfig(DocumentAccessRecord record, String configId, boolean allowDraftConfig) {
        ExtractConfigRecord config = extractConfigMapper.selectById(configId);
        if (config == null || !isAllowedSpecifiedConfig(config, allowDraftConfig)) {
            throw new BusinessException("CONFIG_404", allowDraftConfig ? "未找到可用于手工上传测试的配置" : "未找到已发布的生效配置");
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
        String specifiedSource = allowDraftConfig ? "手工上传指定解析配置" : "指定生效配置";
        record.setMatchMessage(specifiedSource + "：" + config.getConfigName() + " V" + config.getVersion() + "（" + statusLabel(config.getStatus()) + "）");
    }

    private boolean isAllowedSpecifiedConfig(ExtractConfigRecord config, boolean allowDraftConfig) {
        if ("PUBLISHED".equals(config.getStatus())) {
            return true;
        }
        return allowDraftConfig && ("DRAFT".equals(config.getStatus()) || "TESTING".equals(config.getStatus()));
    }

    private String statusLabel(String status) {
        if ("DRAFT".equals(status)) return "草稿";
        if ("TESTING".equals(status)) return "验证中";
        if ("PUBLISHED".equals(status)) return "已发布";
        if ("DISABLED".equals(status)) return "已停用";
        return status;
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
            case "OPS" -> "运营部";
            case "FINANCE" -> "财务部";
            case "PRODUCT" -> "产品部";
            default -> value;
        };
    }

    private String normalizeCategory(String value) {
        String key = firstText(value);
        if (key == null) {
            return value;
        }
        return switch (key) {
            case "FUND_BUSINESS" -> "资金业务";
            case "FUND_TRADE" -> "基金交易";
            case "CUSTOMER_BUSINESS" -> "客户业务";
            default -> value;
        };
    }

    private String normalizeDocumentType(String value) {
        String key = firstText(value);
        if (key == null) {
            return value;
        }
        return switch (key) {
            case "PAYMENT_INSTRUCTION" -> "划款指令";
            case "BANK_RECEIPT" -> "银行回单";
            case "ACCOUNT_OPENING" -> "开户资料";
            default -> value;
        };
    }

    private String normalizeTemplateType(String value) {
        String key = firstText(value);
        if (key == null) {
            return value;
        }
        return switch (key) {
            case "GENERAL_PAYMENT_INSTRUCTION_TEMPLATE" -> "通用划款指令模板";
            case "GENERAL_BANK_RECEIPT_TEMPLATE" -> "通用银行回单模板";
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
