package com.example.extraction.model.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.common.PageResponse;
import com.example.extraction.common.PageSupport;
import com.example.extraction.model.dto.OcrEngineConfigRequest;
import com.example.extraction.model.dto.OcrEngineConfigResponse;
import com.example.extraction.model.dto.OcrEngineQueryRequest;
import com.example.extraction.model.service.OcrEngineConfigService;
import com.example.extraction.ocr.OcrPreviewAssetService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/model/ocr-engines")
public class OcrEngineConfigController {
    private final OcrEngineConfigService ocrEngineConfigService;
    private final OcrPreviewAssetService ocrPreviewAssetService;

    public OcrEngineConfigController(OcrEngineConfigService ocrEngineConfigService,
                                     OcrPreviewAssetService ocrPreviewAssetService) {
        this.ocrEngineConfigService = ocrEngineConfigService;
        this.ocrPreviewAssetService = ocrPreviewAssetService;
    }

    @GetMapping
    public ApiResponse<PageResponse<OcrEngineConfigResponse>> list(OcrEngineQueryRequest query) {
        return ApiResponse.success(PageSupport.page(query, () -> ocrEngineConfigService.list(query)));
    }

    @GetMapping("/options")
    public ApiResponse<List<Map<String, Object>>> options() {
        return ApiResponse.success(ocrEngineConfigService.options());
    }

    @GetMapping("/{id}")
    public ApiResponse<OcrEngineConfigResponse> detail(@PathVariable("id") String id) {
        return ApiResponse.success(ocrEngineConfigService.detail(id));
    }

    @PostMapping
    public ApiResponse<OcrEngineConfigResponse> create(@RequestBody OcrEngineConfigRequest request) {
        return ApiResponse.success(ocrEngineConfigService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<OcrEngineConfigResponse> update(@PathVariable("id") String id, @RequestBody OcrEngineConfigRequest request) {
        return ApiResponse.success(ocrEngineConfigService.update(id, request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<OcrEngineConfigResponse> enable(@PathVariable("id") String id) {
        return ApiResponse.success(ocrEngineConfigService.enable(id));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<OcrEngineConfigResponse> disable(@PathVariable("id") String id) {
        return ApiResponse.success(ocrEngineConfigService.disable(id));
    }

    @PostMapping("/{id}/default")
    public ApiResponse<OcrEngineConfigResponse> setDefault(@PathVariable("id") String id) {
        return ApiResponse.success(ocrEngineConfigService.setDefault(id));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable("id") String id) {
        return ApiResponse.success(ocrEngineConfigService.test(id));
    }

    @PostMapping(value = "/{id}/test-parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> testParse(@PathVariable("id") String id,
                                                     @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(ocrEngineConfigService.testParse(id, file));
    }

    @GetMapping("/preview-assets/{assetId}")
    public ResponseEntity<Resource> previewAsset(@PathVariable("assetId") String assetId) {
        OcrPreviewAssetService.PreviewAsset asset = ocrPreviewAssetService.requireAsset(assetId);
        FileSystemResource resource = new FileSystemResource(asset.path());
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (asset.mimeType() != null && !asset.mimeType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(asset.mimeType());
            } catch (IllegalArgumentException ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(asset.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }
}
