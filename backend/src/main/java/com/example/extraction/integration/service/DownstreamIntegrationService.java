package com.example.extraction.integration.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.integration.domain.DownstreamServiceConfigRecord;
import com.example.extraction.integration.domain.DownstreamSystemConfigRecord;
import com.example.extraction.integration.dto.DownstreamServiceRequest;
import com.example.extraction.integration.dto.DownstreamServiceResponse;
import com.example.extraction.integration.dto.DownstreamSystemRequest;
import com.example.extraction.integration.dto.DownstreamSystemResponse;
import com.example.extraction.integration.dto.IntegrationQueryRequest;
import com.example.extraction.mapper.DownstreamIntegrationMapper;
import com.example.extraction.mapper.ExtractConfigMapper;
import com.example.extraction.result.dto.PushQueryRequest;
import com.example.extraction.result.dto.PushRecordResponse;
import com.example.extraction.result.service.DownstreamPushService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DownstreamIntegrationService {
    private final DownstreamIntegrationMapper integrationMapper;
    private final ExtractConfigMapper extractConfigMapper;
    private final DownstreamPushService downstreamPushService;
    private final ObjectMapper objectMapper;

    public DownstreamIntegrationService(DownstreamIntegrationMapper integrationMapper,
                                        ExtractConfigMapper extractConfigMapper,
                                        DownstreamPushService downstreamPushService,
                                        ObjectMapper objectMapper) {
        this.integrationMapper = integrationMapper;
        this.extractConfigMapper = extractConfigMapper;
        this.downstreamPushService = downstreamPushService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<DownstreamSystemResponse> systems(IntegrationQueryRequest query) {
        ensureDefaults();
        List<DownstreamServiceConfigRecord> services = integrationMapper.selectServices(new IntegrationQueryRequest());
        return integrationMapper.selectSystems(query).stream()
                .map(system -> toSystemResponse(system, services))
                .toList();
    }

    @Transactional
    public void ensureDefaultsForList() {
        ensureDefaults();
    }

    public List<DownstreamSystemResponse> systemsWithoutDefaults(IntegrationQueryRequest query) {
        List<DownstreamSystemConfigRecord> systems = integrationMapper.selectSystems(query);
        List<DownstreamServiceConfigRecord> services = integrationMapper.selectServices(new IntegrationQueryRequest());
        return systems.stream()
                .map(system -> toSystemResponse(system, services))
                .toList();
    }

    @Transactional
    public List<DownstreamServiceResponse> services(IntegrationQueryRequest query) {
        ensureDefaults();
        return integrationMapper.selectServices(query).stream().map(this::toServiceResponse).toList();
    }

    public List<DownstreamServiceResponse> servicesWithoutDefaults(IntegrationQueryRequest query) {
        return integrationMapper.selectServices(query).stream().map(this::toServiceResponse).toList();
    }

    @Transactional
    public DownstreamSystemResponse enableSystem(String id) {
        return updateSystemStatus(id, "ENABLED");
    }

    @Transactional
    public DownstreamSystemResponse createSystem(DownstreamSystemRequest request) {
        ensureDefaults();
        validateSystemRequest(request);
        if (integrationMapper.selectSystemByCode(request.getSystemCode()) != null) {
            throw new BusinessException("INTEGRATION_409", "下游系统编码已存在");
        }
        DownstreamSystemConfigRecord record = new DownstreamSystemConfigRecord();
        record.setId(IdGenerator.nextId("DSYS"));
        fillSystem(record, request);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        integrationMapper.insertSystem(record);
        return toSystemResponse(record, integrationMapper.selectServices(new IntegrationQueryRequest()));
    }

    @Transactional
    public DownstreamSystemResponse updateSystem(String id, DownstreamSystemRequest request) {
        ensureDefaults();
        validateSystemRequest(request);
        DownstreamSystemConfigRecord record = requireSystem(id);
        DownstreamSystemConfigRecord sameCode = integrationMapper.selectSystemByCode(request.getSystemCode());
        if (sameCode != null && !id.equals(sameCode.getId())) {
            throw new BusinessException("INTEGRATION_409", "下游系统编码已存在");
        }
        fillSystem(record, request);
        record.setUpdatedAt(LocalDateTime.now());
        integrationMapper.updateSystem(record);
        return toSystemResponse(requireSystem(id), integrationMapper.selectServices(new IntegrationQueryRequest()));
    }

    @Transactional
    public DownstreamSystemResponse disableSystem(String id) {
        return updateSystemStatus(id, "DISABLED");
    }

    @Transactional
    public void deleteSystem(String id) {
        requireSystem(id);
        int serviceCount = integrationMapper.countServicesBySystemId(id);
        if (serviceCount > 0) {
            throw new BusinessException("INTEGRATION_409", "该下游系统下仍有接口服务，请先删除接口服务后再删除系统");
        }
        integrationMapper.deleteSystem(id);
    }

    @Transactional
    public DownstreamServiceResponse enableService(String id) {
        return updateServiceEnabled(id, "1");
    }

    @Transactional
    public DownstreamServiceResponse createService(DownstreamServiceRequest request) {
        ensureDefaults();
        validateServiceRequest(request);
        requireSystem(request.getSystemId());
        if (integrationMapper.selectServiceByCode(request.getServiceCode()) != null) {
            throw new BusinessException("INTEGRATION_409", "接口服务编码已存在");
        }
        DownstreamServiceConfigRecord record = new DownstreamServiceConfigRecord();
        record.setId(IdGenerator.nextId("DSRV"));
        fillService(record, request);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        integrationMapper.insertService(record);
        return toServiceResponse(integrationMapper.selectServiceById(record.getId()));
    }

    @Transactional
    public DownstreamServiceResponse updateService(String id, DownstreamServiceRequest request) {
        ensureDefaults();
        validateServiceRequest(request);
        requireSystem(request.getSystemId());
        DownstreamServiceConfigRecord record = requireService(id);
        DownstreamServiceConfigRecord sameCode = integrationMapper.selectServiceByCode(request.getServiceCode());
        if (sameCode != null && !id.equals(sameCode.getId())) {
            throw new BusinessException("INTEGRATION_409", "接口服务编码已存在");
        }
        fillService(record, request);
        record.setUpdatedAt(LocalDateTime.now());
        integrationMapper.updateService(record);
        return toServiceResponse(requireService(id));
    }

    @Transactional
    public DownstreamServiceResponse disableService(String id) {
        return updateServiceEnabled(id, "0");
    }

    @Transactional
    public void deleteService(String id) {
        DownstreamServiceConfigRecord service = requireService(id);
        int referenceCount = countServiceReferences(service.getServiceCode());
        if (referenceCount > 0) {
            throw new BusinessException("INTEGRATION_409", "该接口服务已被配置向导引用，请先在配置中解除绑定或停用服务");
        }
        integrationMapper.deleteService(id);
    }

    public Map<String, Object> testService(String id) {
        DownstreamServiceConfigRecord service = requireService(id);
        Map<String, Object> result = baseTestResult(service);
        if (!"1".equals(service.getEnabled())) {
            result.put("message", "接口服务已停用，不能测试连接");
            result.put("errorCode", "SERVICE_DISABLED");
            return result;
        }
        if (!"HTTP".equalsIgnoreCase(service.getServiceType())) {
            result.put("message", "第一版连接测试仅支持 HTTP 接口，微服务/MQ 执行器后续接入");
            result.put("errorCode", "UNSUPPORTED_SERVICE_TYPE");
            return result;
        }
        if (!StringUtils.hasText(service.getEndpoint())) {
            result.put("message", "接口地址不能为空");
            result.put("errorCode", "ENDPOINT_EMPTY");
            return result;
        }

        long begin = System.currentTimeMillis();
        HttpMethod method = resolveHttpMethod(service.getHttpMethod());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));
        Object requestBody = method == HttpMethod.GET || method == HttpMethod.HEAD ? null : buildTestPayload(service);
        result.put("requestMethod", method.name());
        result.put("requestPreview", requestBody == null ? "" : toJson(requestBody));
        List<String> warnings = new ArrayList<>();
        if (StringUtils.hasText(service.getAuthMode()) && !"NONE".equalsIgnoreCase(service.getAuthMode())) {
            warnings.add("当前测试请求暂未附加鉴权信息，如接口要求 Token/签名，可能返回 401/403");
        }
        result.put("warnings", warnings);

        try {
            ResponseEntity<String> response = restTemplate(service).exchange(service.getEndpoint(), method, new HttpEntity<>(requestBody, headers), String.class);
            int httpStatus = response.getStatusCode().value();
            String responseBody = response.getBody() == null ? "" : response.getBody();
            boolean passed = evaluateSuccessRule(httpStatus, responseBody, service.getResponseSuccessRule(), warnings);
            result.put("passed", passed);
            result.put("httpStatus", httpStatus);
            result.put("responsePreview", compactPreview(responseBody, 4000));
            result.put("warnings", warnings);
            result.put("message", passed ? "HTTP 接口真实连接测试通过" : "HTTP 接口已响应，但未满足成功判断规则");
        } catch (HttpStatusCodeException e) {
            result.put("passed", false);
            result.put("httpStatus", e.getStatusCode().value());
            result.put("errorCode", "HTTP_" + e.getStatusCode().value());
            result.put("responsePreview", compactPreview(e.getResponseBodyAsString(), 4000));
            result.put("message", "HTTP 接口调用失败：" + e.getStatusCode().value());
        } catch (Exception e) {
            result.put("passed", false);
            result.put("errorCode", "HTTP_TEST_ERROR");
            result.put("message", "HTTP 接口调用失败：" + e.getMessage());
        } finally {
            result.put("durationMs", System.currentTimeMillis() - begin);
            result.put("checkedAt", LocalDateTime.now());
        }
        return result;
    }

    private Map<String, Object> baseTestResult(DownstreamServiceConfigRecord service) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", false);
        result.put("serviceCode", service.getServiceCode());
        result.put("serviceName", service.getServiceName());
        result.put("systemName", service.getSystemName());
        result.put("serviceType", service.getServiceType());
        result.put("endpoint", service.getEndpoint());
        result.put("checkedAt", LocalDateTime.now());
        return result;
    }

    private HttpMethod resolveHttpMethod(String httpMethod) {
        if (!StringUtils.hasText(httpMethod) || "-".equals(httpMethod.trim())) return HttpMethod.POST;
        try {
            return HttpMethod.valueOf(httpMethod.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("PARAM_400", "暂不支持的 HTTP 请求方式：" + httpMethod);
        }
    }

    private Map<String, Object> buildTestPayload(DownstreamServiceConfigRecord service) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceId", "TRACE_TEST_0001");
        payload.put("taskId", "TASK_TEST_0001");
        payload.put("documentId", "DOC_TEST_0001");
        payload.put("serviceCode", service.getServiceCode());
        payload.put("idempotentKey", "TRACE_TEST_0001-TASK_TEST_0001-" + service.getServiceCode());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("test", true);
        data.put("message", "智能要素提取平台接口服务连通性测试");
        payload.put("data", data);
        return payload;
    }

    private boolean evaluateSuccessRule(int httpStatus, String responseBody, String successRule, List<String> warnings) {
        boolean statusPassed = evaluateHttpStatus(httpStatus, successRule);
        boolean bodyPassed = evaluateBodyRule(responseBody, successRule, warnings);
        return statusPassed && bodyPassed;
    }

    private boolean evaluateHttpStatus(int httpStatus, String successRule) {
        if (!StringUtils.hasText(successRule)) return httpStatus >= 200 && httpStatus < 300;
        String rule = successRule.replace(" ", "").toLowerCase();
        if (rule.contains("httpstatusin[")) {
            int start = rule.indexOf("httpstatusin[") + "httpstatusin[".length();
            int end = rule.indexOf(']', start);
            if (end > start) {
                String[] values = rule.substring(start, end).split(",");
                for (String value : values) {
                    if (String.valueOf(httpStatus).equals(value.trim())) return true;
                }
                return false;
            }
        }
        if (rule.contains("httpstatus==")) {
            String expected = rule.substring(rule.indexOf("httpstatus==") + "httpstatus==".length()).split("&&|\\|\\|")[0];
            return String.valueOf(httpStatus).equals(expected.trim());
        }
        return httpStatus >= 200 && httpStatus < 300;
    }

    private boolean evaluateBodyRule(String responseBody, String successRule, List<String> warnings) {
        if (!StringUtils.hasText(successRule)) return true;
        String normalized = successRule.replace(" ", "");
        if (!normalized.contains("body.")) return true;
        if (!StringUtils.hasText(responseBody)) {
            warnings.add("成功判断包含 body 条件，但响应体为空");
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (normalized.contains("body.code==0")) {
                JsonNode code = root.path("code");
                return (code.isNumber() && code.asInt() == 0) || "0".equals(code.asText());
            }
            if (normalized.contains("body.success==true")) return root.path("success").asBoolean(false);
            if (normalized.contains("body.passed==true")) return root.path("passed").asBoolean(false);
            warnings.add("暂未解析复杂响应成功判断，已按 HTTP 状态码优先判断");
            return true;
        } catch (JsonProcessingException e) {
            warnings.add("响应体不是 JSON，无法校验 body 条件：" + e.getOriginalMessage());
            return false;
        }
    }

    private RestTemplate restTemplate(DownstreamServiceConfigRecord service) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = service.getTimeoutSeconds() == null || service.getTimeoutSeconds() <= 0 ? 30 : service.getTimeoutSeconds();
        factory.setConnectTimeout(Duration.ofSeconds(Math.min(timeout, 30)));
        factory.setReadTimeout(Duration.ofSeconds(timeout));
        return new RestTemplate(factory);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String compactPreview(String value, int maxLength) {
        if (!StringUtils.hasText(value)) return "";
        String text = value.trim();
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    @Transactional
    public List<Map<String, Object>> serviceOptions() {
        ensureDefaults();
        IntegrationQueryRequest query = new IntegrationQueryRequest();
        query.setStatus("ENABLED");
        return integrationMapper.selectServices(query).stream().map(service -> {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("serviceCode", service.getServiceCode());
            option.put("serviceName", service.getServiceName());
            option.put("systemCode", service.getSystemCode());
            option.put("systemName", service.getSystemName());
            option.put("serviceType", service.getServiceType());
            option.put("purpose", service.getPurpose());
            option.put("endpoint", service.getEndpoint());
            option.put("httpMethod", service.getHttpMethod());
            option.put("responseSuccessRule", service.getResponseSuccessRule());
            option.put("retryCount", service.getRetryCount());
            option.put("enabled", "1".equals(service.getEnabled()));
            option.put("label", service.getSystemName() + " / " + service.getServiceName() + " (" + service.getServiceType() + ")");
            return option;
        }).toList();
    }

    private DownstreamSystemResponse updateSystemStatus(String id, String status) {
        requireSystem(id);
        integrationMapper.updateSystemStatus(id, status);
        DownstreamSystemConfigRecord system = requireSystem(id);
        return toSystemResponse(system, integrationMapper.selectServices(new IntegrationQueryRequest()));
    }

    private DownstreamServiceResponse updateServiceEnabled(String id, String enabled) {
        requireService(id);
        integrationMapper.updateServiceEnabled(id, enabled);
        return toServiceResponse(requireService(id));
    }

    private void validateSystemRequest(DownstreamSystemRequest request) {
        if (!StringUtils.hasText(request.getSystemCode())) {
            throw new BusinessException("PARAM_400", "系统编码不能为空");
        }
        if (!request.getSystemCode().matches("^[a-zA-Z][a-zA-Z0-9_\\-]*$")) {
            throw new BusinessException("PARAM_400", "系统编码必须以字母开头，仅支持字母、数字、下划线和中划线");
        }
        if (!StringUtils.hasText(request.getSystemName())) {
            throw new BusinessException("PARAM_400", "系统名称不能为空");
        }
        if (request.getDefaultTimeoutSeconds() != null && request.getDefaultTimeoutSeconds() <= 0) {
            throw new BusinessException("PARAM_400", "默认超时时间必须大于 0");
        }
        if (request.getDefaultRetryCount() != null && request.getDefaultRetryCount() < 0) {
            throw new BusinessException("PARAM_400", "默认重试次数不能小于 0");
        }
    }

    private void validateServiceRequest(DownstreamServiceRequest request) {
        if (!StringUtils.hasText(request.getSystemId())) {
            throw new BusinessException("PARAM_400", "所属系统不能为空");
        }
        if (!StringUtils.hasText(request.getServiceCode())) {
            throw new BusinessException("PARAM_400", "服务编码不能为空");
        }
        if (!request.getServiceCode().matches("^[a-zA-Z][a-zA-Z0-9_\\-]*$")) {
            throw new BusinessException("PARAM_400", "服务编码必须以字母开头，仅支持字母、数字、下划线和中划线");
        }
        if (!StringUtils.hasText(request.getServiceName())) {
            throw new BusinessException("PARAM_400", "服务名称不能为空");
        }
        if (!StringUtils.hasText(request.getServiceType())) {
            throw new BusinessException("PARAM_400", "服务类型不能为空");
        }
        if (request.getTimeoutSeconds() != null && request.getTimeoutSeconds() <= 0) {
            throw new BusinessException("PARAM_400", "超时时间必须大于 0");
        }
        if (request.getRetryCount() != null && request.getRetryCount() < 0) {
            throw new BusinessException("PARAM_400", "重试次数不能小于 0");
        }
    }

    private void fillSystem(DownstreamSystemConfigRecord record, DownstreamSystemRequest request) {
        record.setSystemCode(request.getSystemCode());
        record.setSystemName(request.getSystemName());
        record.setOwnerDepartmentId(request.getOwnerDepartmentId());
        record.setDefaultAuthMode(StringUtils.hasText(request.getDefaultAuthMode()) ? request.getDefaultAuthMode() : "NONE");
        record.setDefaultTimeoutSeconds(request.getDefaultTimeoutSeconds() == null ? 30 : request.getDefaultTimeoutSeconds());
        record.setDefaultRetryCount(request.getDefaultRetryCount() == null ? 3 : request.getDefaultRetryCount());
        record.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ENABLED");
    }

    private void fillService(DownstreamServiceConfigRecord record, DownstreamServiceRequest request) {
        record.setSystemId(request.getSystemId());
        record.setServiceCode(request.getServiceCode());
        record.setServiceName(request.getServiceName());
        record.setPurpose(request.getPurpose());
        record.setServiceType(request.getServiceType());
        record.setEndpoint(request.getEndpoint());
        record.setHttpMethod(StringUtils.hasText(request.getHttpMethod()) ? request.getHttpMethod() : "-");
        record.setAuthMode(StringUtils.hasText(request.getAuthMode()) ? request.getAuthMode() : "INHERIT");
        record.setTimeoutSeconds(request.getTimeoutSeconds() == null ? 30 : request.getTimeoutSeconds());
        record.setRetryCount(request.getRetryCount() == null ? 3 : request.getRetryCount());
        record.setResponseSuccessRule(request.getResponseSuccessRule());
        record.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? "0" : "1");
    }

    private DownstreamSystemConfigRecord requireSystem(String id) {
        DownstreamSystemConfigRecord record = integrationMapper.selectSystemById(id);
        if (record == null) {
            throw new BusinessException("INTEGRATION_404", "下游系统不存在");
        }
        return record;
    }

    private DownstreamServiceConfigRecord requireService(String id) {
        DownstreamServiceConfigRecord record = integrationMapper.selectServiceById(id);
        if (record == null) {
            throw new BusinessException("INTEGRATION_404", "接口服务不存在");
        }
        return record;
    }

    private DownstreamSystemResponse toSystemResponse(DownstreamSystemConfigRecord record, List<DownstreamServiceConfigRecord> services) {
        List<DownstreamServiceConfigRecord> systemServices = services.stream()
                .filter(service -> record.getSystemCode().equals(service.getSystemCode()))
                .toList();
        DownstreamSystemResponse response = new DownstreamSystemResponse();
        response.setId(record.getId());
        response.setSystemCode(record.getSystemCode());
        response.setSystemName(record.getSystemName());
        response.setOwnerDepartmentId(record.getOwnerDepartmentId());
        response.setDefaultAuthMode(record.getDefaultAuthMode());
        response.setDefaultTimeoutSeconds(record.getDefaultTimeoutSeconds());
        response.setDefaultRetryCount(record.getDefaultRetryCount());
        response.setStatus(record.getStatus());
        response.setEnabled("ENABLED".equals(record.getStatus()));
        response.setServiceCount(systemServices.size());
        response.setEnabledServiceCount((int) systemServices.stream().filter(service -> "1".equals(service.getEnabled())).count());
        response.setSuccessRate(successRate(systemServices));
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    private DownstreamServiceResponse toServiceResponse(DownstreamServiceConfigRecord record) {
        DownstreamServiceResponse response = new DownstreamServiceResponse();
        response.setId(record.getId());
        response.setSystemId(record.getSystemId());
        response.setSystemCode(record.getSystemCode());
        response.setSystemName(record.getSystemName());
        response.setOwnerDepartmentId(record.getOwnerDepartmentId());
        response.setServiceCode(record.getServiceCode());
        response.setServiceName(record.getServiceName());
        response.setPurpose(record.getPurpose());
        response.setServiceType(record.getServiceType());
        response.setEndpoint(record.getEndpoint());
        response.setHttpMethod(record.getHttpMethod());
        response.setAuthMode(record.getAuthMode());
        response.setTimeoutSeconds(record.getTimeoutSeconds());
        response.setRetryCount(record.getRetryCount());
        response.setResponseSuccessRule(record.getResponseSuccessRule());
        response.setEnabled("1".equals(record.getEnabled()));
        response.setBoundConfigCount(countServiceReferences(record.getServiceCode()));
        response.setSuccessRate(successRate(List.of(record)));
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    private int countServiceReferences(String serviceCode) {
        if (!StringUtils.hasText(serviceCode)) {
            return 0;
        }
        return extractConfigMapper.countPayloadReferences(serviceCode);
    }

    private int successRate(List<DownstreamServiceConfigRecord> services) {
        List<String> serviceCodes = services.stream().map(DownstreamServiceConfigRecord::getServiceCode).toList();
        List<PushRecordResponse> records = downstreamPushService.list(new PushQueryRequest()).stream()
                .filter(record -> serviceCodes.contains(record.getServiceCode()))
                .toList();
        if (records.isEmpty()) {
            return 100;
        }
        long success = records.stream().filter(record -> "SUCCESS".equals(record.getStatus())).count();
        return Math.round((success * 100f) / records.size());
    }

    private void ensureDefaults() {
        if (integrationMapper.countSystems() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        DownstreamSystemConfigRecord ops = system("fund_ops", "运营业务系统", "运营部", "TOKEN", 30, 3, now);
        DownstreamSystemConfigRecord finance = system("finance_core", "财务核算系统", "财务部", "SIGN", 20, 2, now);
        DownstreamSystemConfigRecord warehouse = system("data_warehouse", "数据仓库", "产品部", "NONE", 60, 5, now);
        integrationMapper.insertSystem(ops);
        integrationMapper.insertSystem(finance);
        integrationMapper.insertSystem(warehouse);
        integrationMapper.insertService(service(ops, "fund_ops_result_receive", "接收提取结果服务", "结果推送", "HTTP", "https://ops.example.com/api/extract-results", "POST", "httpStatus in [200,202] && body.code == 0", 3, now));
        integrationMapper.insertService(service(finance, "finance_result_receive", "核算结果接收服务", "结果推送", "MICROSERVICE", "finance-result-service.receiveExtractResult", "-", "response.accepted == true", 2, now));
        integrationMapper.insertService(service(warehouse, "dw_extract_result_topic", "结果批量同步 Topic", "批量同步", "MQ", "topic.extract.result", "-", "broker ack", 5, now));
    }

    private DownstreamSystemConfigRecord system(String code, String name, String department, String authMode,
                                                int timeoutSeconds, int retryCount, LocalDateTime now) {
        DownstreamSystemConfigRecord record = new DownstreamSystemConfigRecord();
        record.setId(IdGenerator.nextId("DSYS"));
        record.setSystemCode(code);
        record.setSystemName(name);
        record.setOwnerDepartmentId(department);
        record.setDefaultAuthMode(authMode);
        record.setDefaultTimeoutSeconds(timeoutSeconds);
        record.setDefaultRetryCount(retryCount);
        record.setStatus("ENABLED");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private DownstreamServiceConfigRecord service(DownstreamSystemConfigRecord system, String code, String name,
                                                  String purpose, String type, String endpoint, String httpMethod,
                                                  String successRule, int retryCount, LocalDateTime now) {
        DownstreamServiceConfigRecord record = new DownstreamServiceConfigRecord();
        record.setId(IdGenerator.nextId("DSRV"));
        record.setSystemId(system.getId());
        record.setServiceCode(code);
        record.setServiceName(name);
        record.setPurpose(purpose);
        record.setServiceType(type);
        record.setEndpoint(endpoint);
        record.setHttpMethod(httpMethod);
        record.setAuthMode("INHERIT");
        record.setTimeoutSeconds(system.getDefaultTimeoutSeconds());
        record.setRetryCount(retryCount);
        record.setResponseSuccessRule(successRule);
        record.setEnabled("1");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }
}
