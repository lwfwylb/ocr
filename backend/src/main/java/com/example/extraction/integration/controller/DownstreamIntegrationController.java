package com.example.extraction.integration.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.common.PageResponse;
import com.example.extraction.common.PageSupport;
import com.example.extraction.integration.dto.DownstreamServiceRequest;
import com.example.extraction.integration.dto.DownstreamServiceResponse;
import com.example.extraction.integration.dto.DownstreamSystemRequest;
import com.example.extraction.integration.dto.DownstreamSystemResponse;
import com.example.extraction.integration.dto.IntegrationQueryRequest;
import com.example.extraction.integration.service.DownstreamIntegrationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/integrations")
public class DownstreamIntegrationController {
    private final DownstreamIntegrationService integrationService;

    public DownstreamIntegrationController(DownstreamIntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping("/systems")
    public ApiResponse<PageResponse<DownstreamSystemResponse>> systems(IntegrationQueryRequest query) {
        integrationService.ensureDefaultsForList();
        return ApiResponse.success(PageSupport.page(query, () -> integrationService.systemsWithoutDefaults(query)));
    }

    @GetMapping("/services")
    public ApiResponse<PageResponse<DownstreamServiceResponse>> services(IntegrationQueryRequest query) {
        integrationService.ensureDefaultsForList();
        return ApiResponse.success(PageSupport.page(query, () -> integrationService.servicesWithoutDefaults(query)));
    }

    @PostMapping("/systems")
    public ApiResponse<DownstreamSystemResponse> createSystem(@RequestBody DownstreamSystemRequest request) {
        return ApiResponse.success(integrationService.createSystem(request));
    }

    @PutMapping("/systems/{id}")
    public ApiResponse<DownstreamSystemResponse> updateSystem(@PathVariable("id") String id,
                                                              @RequestBody DownstreamSystemRequest request) {
        return ApiResponse.success(integrationService.updateSystem(id, request));
    }

    @PostMapping("/services")
    public ApiResponse<DownstreamServiceResponse> createService(@RequestBody DownstreamServiceRequest request) {
        return ApiResponse.success(integrationService.createService(request));
    }

    @PutMapping("/services/{id}")
    public ApiResponse<DownstreamServiceResponse> updateService(@PathVariable("id") String id,
                                                                @RequestBody DownstreamServiceRequest request) {
        return ApiResponse.success(integrationService.updateService(id, request));
    }

    @PostMapping("/systems/{id}/enable")
    public ApiResponse<DownstreamSystemResponse> enableSystem(@PathVariable("id") String id) {
        return ApiResponse.success(integrationService.enableSystem(id));
    }

    @PostMapping("/systems/{id}/disable")
    public ApiResponse<DownstreamSystemResponse> disableSystem(@PathVariable("id") String id) {
        return ApiResponse.success(integrationService.disableSystem(id));
    }

    @DeleteMapping("/systems/{id}")
    public ApiResponse<Void> deleteSystem(@PathVariable("id") String id) {
        integrationService.deleteSystem(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/services/{id}/enable")
    public ApiResponse<DownstreamServiceResponse> enableService(@PathVariable("id") String id) {
        return ApiResponse.success(integrationService.enableService(id));
    }

    @PostMapping("/services/{id}/disable")
    public ApiResponse<DownstreamServiceResponse> disableService(@PathVariable("id") String id) {
        return ApiResponse.success(integrationService.disableService(id));
    }

    @DeleteMapping("/services/{id}")
    public ApiResponse<Void> deleteService(@PathVariable("id") String id) {
        integrationService.deleteService(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/services/{id}/test")
    public ApiResponse<Map<String, Object>> testService(@PathVariable("id") String id) {
        return ApiResponse.success(integrationService.testService(id));
    }
}
