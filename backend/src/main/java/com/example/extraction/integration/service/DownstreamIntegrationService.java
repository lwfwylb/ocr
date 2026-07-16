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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DownstreamIntegrationService {
    private final DownstreamIntegrationMapper integrationMapper;
    private final ExtractConfigMapper extractConfigMapper;
    private final DownstreamPushService downstreamPushService;

    public DownstreamIntegrationService(DownstreamIntegrationMapper integrationMapper,
                                        ExtractConfigMapper extractConfigMapper,
                                        DownstreamPushService downstreamPushService) {
        this.integrationMapper = integrationMapper;
        this.extractConfigMapper = extractConfigMapper;
        this.downstreamPushService = downstreamPushService;
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", true);
        result.put("message", "模拟连接测试通过，真实调用将在推送执行器中接入");
        result.put("serviceCode", service.getServiceCode());
        result.put("serviceName", service.getServiceName());
        result.put("endpoint", service.getEndpoint());
        result.put("checkedAt", LocalDateTime.now());
        return result;
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
