package com.example.extraction.document.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.common.PageResponse;
import com.example.extraction.common.PageSupport;
import com.example.extraction.document.dto.DocumentAccessQueryRequest;
import com.example.extraction.document.dto.DocumentAccessRequest;
import com.example.extraction.document.dto.DocumentAccessResponse;
import com.example.extraction.document.dto.DocumentConfirmRequest;
import com.example.extraction.document.service.DocumentAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentAccessController {
    private final DocumentAccessService documentAccessService;

    public DocumentAccessController(DocumentAccessService documentAccessService) {
        this.documentAccessService = documentAccessService;
    }

    @GetMapping("/access-records")
    public ApiResponse<PageResponse<DocumentAccessResponse>> list(DocumentAccessQueryRequest query) {
        return ApiResponse.success(PageSupport.page(query, () -> documentAccessService.list(query)));
    }

    @GetMapping("/pending-confirm")
    public ApiResponse<PageResponse<DocumentAccessResponse>> pendingConfirm(DocumentAccessQueryRequest query) {
        return ApiResponse.success(PageSupport.page(query, () -> documentAccessService.pendingConfirm(query)));
    }

    @GetMapping("/access-records/{id}")
    public ApiResponse<DocumentAccessResponse> detail(@PathVariable("id") String id) {
        return ApiResponse.success(documentAccessService.detail(id));
    }

    @PostMapping(value = "/manual-upload", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<DocumentAccessResponse> manualUpload(@RequestBody DocumentAccessRequest request) {
        return ApiResponse.success(documentAccessService.manualUpload(request));
    }

    @PostMapping(value = "/manual-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentAccessResponse> manualUploadFile(@RequestParam("configId") String configId,
                                                                @RequestParam(value = "businessNo", required = false) String businessNo,
                                                                @RequestParam(value = "priority", required = false) String priority,
                                                                @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(documentAccessService.manualUploadFile(configId, businessNo, priority, file));
    }

    @PostMapping("/api-push")
    public ApiResponse<DocumentAccessResponse> apiPush(@RequestBody DocumentAccessRequest request) {
        return ApiResponse.success(documentAccessService.apiPush(request));
    }

    @PostMapping("/access-records/{id}/rematch")
    public ApiResponse<DocumentAccessResponse> rematch(@PathVariable("id") String id) {
        return ApiResponse.success(documentAccessService.rematch(id));
    }

    @PostMapping("/access-records/{id}/confirm")
    public ApiResponse<DocumentAccessResponse> confirm(@PathVariable("id") String id, @RequestBody DocumentConfirmRequest request) {
        return ApiResponse.success(documentAccessService.confirm(id, request));
    }
}
