package com.example.extraction.document.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.document.dto.DocumentAccessQueryRequest;
import com.example.extraction.document.dto.DocumentAccessRequest;
import com.example.extraction.document.dto.DocumentAccessResponse;
import com.example.extraction.document.dto.DocumentConfirmRequest;
import com.example.extraction.document.service.DocumentAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentAccessController {
    private final DocumentAccessService documentAccessService;

    public DocumentAccessController(DocumentAccessService documentAccessService) {
        this.documentAccessService = documentAccessService;
    }

    @GetMapping("/access-records")
    public ApiResponse<List<DocumentAccessResponse>> list(DocumentAccessQueryRequest query) {
        return ApiResponse.success(documentAccessService.list(query));
    }

    @GetMapping("/pending-confirm")
    public ApiResponse<List<DocumentAccessResponse>> pendingConfirm(DocumentAccessQueryRequest query) {
        return ApiResponse.success(documentAccessService.pendingConfirm(query));
    }

    @GetMapping("/access-records/{id}")
    public ApiResponse<DocumentAccessResponse> detail(@PathVariable("id") String id) {
        return ApiResponse.success(documentAccessService.detail(id));
    }

    @PostMapping("/manual-upload")
    public ApiResponse<DocumentAccessResponse> manualUpload(@RequestBody DocumentAccessRequest request) {
        return ApiResponse.success(documentAccessService.manualUpload(request));
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
