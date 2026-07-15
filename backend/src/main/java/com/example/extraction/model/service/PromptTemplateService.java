package com.example.extraction.model.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.mapper.PromptTemplateMapper;
import com.example.extraction.model.domain.PromptTemplateRecord;
import com.example.extraction.model.dto.PromptTemplateDefaultsRequest;
import com.example.extraction.model.dto.PromptTemplateDefaultsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.stream.Stream;

@Service
public class PromptTemplateService {
    public static final String SYSTEM_TYPE = "SYSTEM";
    public static final String USER_TYPE = "USER";
    public static final String DEFAULT_SYSTEM_TEMPLATE = "你是基金公司智能要素提取助手。请严格根据输入的文档内容提取信息，不允许编造。无法识别的字段返回null。请严格按用户提示词要求输出JSON，不输出解释性文字。";
    public static final String DEFAULT_USER_TEMPLATE = "请从文档内容中提取要素：${fields}。要求无法识别的字段返回null，严格JSON格式输出。";

    private final PromptTemplateMapper promptTemplateMapper;

    public PromptTemplateService(PromptTemplateMapper promptTemplateMapper) {
        this.promptTemplateMapper = promptTemplateMapper;
    }

    @Transactional
    public PromptTemplateDefaultsResponse defaults() {
        PromptTemplateRecord system = ensureTemplate(SYSTEM_TYPE, "系统提示词模板", DEFAULT_SYSTEM_TEMPLATE);
        PromptTemplateRecord user = ensureTemplate(USER_TYPE, "用户提示词模板", DEFAULT_USER_TEMPLATE);
        return toResponse(system, user);
    }

    @Transactional
    public PromptTemplateDefaultsResponse updateDefaults(PromptTemplateDefaultsRequest request) {
        validate(request);
        PromptTemplateRecord system = ensureTemplate(SYSTEM_TYPE, "系统提示词模板", DEFAULT_SYSTEM_TEMPLATE);
        PromptTemplateRecord user = ensureTemplate(USER_TYPE, "用户提示词模板", DEFAULT_USER_TEMPLATE);
        LocalDateTime now = LocalDateTime.now();
        fill(system, "系统提示词模板", request.getSystemTemplate().trim(), now);
        fill(user, "用户提示词模板", request.getUserTemplate().trim(), now);
        promptTemplateMapper.update(system);
        promptTemplateMapper.update(user);
        return toResponse(system, user);
    }

    @Transactional
    public PromptTemplateDefaultsResponse resetDefaults() {
        PromptTemplateRecord system = ensureTemplate(SYSTEM_TYPE, "系统提示词模板", DEFAULT_SYSTEM_TEMPLATE);
        PromptTemplateRecord user = ensureTemplate(USER_TYPE, "用户提示词模板", DEFAULT_USER_TEMPLATE);
        LocalDateTime now = LocalDateTime.now();
        fill(system, "系统提示词模板", DEFAULT_SYSTEM_TEMPLATE, now);
        fill(user, "用户提示词模板", DEFAULT_USER_TEMPLATE, now);
        promptTemplateMapper.update(system);
        promptTemplateMapper.update(user);
        return toResponse(system, user);
    }

    private PromptTemplateRecord ensureTemplate(String type, String name, String content) {
        PromptTemplateRecord record = promptTemplateMapper.selectByType(type);
        if (record != null) {
            return record;
        }
        LocalDateTime now = LocalDateTime.now();
        record = new PromptTemplateRecord();
        record.setId(IdGenerator.nextId("PT"));
        record.setTemplateType(type);
        record.setTemplateName(name);
        record.setTemplateContent(content);
        record.setStatus("ENABLED");
        record.setUpdatedBy("system");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        promptTemplateMapper.insert(record);
        return record;
    }

    private void fill(PromptTemplateRecord record, String name, String content, LocalDateTime now) {
        record.setTemplateName(name);
        record.setTemplateContent(content);
        record.setStatus("ENABLED");
        record.setUpdatedBy("system");
        record.setUpdatedAt(now);
    }

    private void validate(PromptTemplateDefaultsRequest request) {
        if (request == null || !StringUtils.hasText(request.getSystemTemplate())) {
            throw new BusinessException("PARAM_400", "系统提示词模板不能为空");
        }
        if (!StringUtils.hasText(request.getUserTemplate())) {
            throw new BusinessException("PARAM_400", "用户提示词模板不能为空");
        }
        if (!request.getUserTemplate().contains("${fields}")) {
            throw new BusinessException("PARAM_400", "用户提示词模板必须包含 ${fields} 变量");
        }
    }

    private PromptTemplateDefaultsResponse toResponse(PromptTemplateRecord system, PromptTemplateRecord user) {
        PromptTemplateDefaultsResponse response = new PromptTemplateDefaultsResponse();
        response.setSystemTemplate(system.getTemplateContent());
        response.setUserTemplate(user.getTemplateContent());
        response.setUpdatedAt(Stream.of(system.getUpdatedAt(), user.getUpdatedAt())
            .filter(time -> time != null)
            .max(Comparator.naturalOrder())
            .orElse(null));
        return response;
    }
}
