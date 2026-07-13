package com.example.extraction.system.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.system.dto.DictItemRequest;
import com.example.extraction.system.dto.DictItemResponse;
import com.example.extraction.system.dto.DictTypeRequest;
import com.example.extraction.system.dto.DictTypeResponse;
import com.example.extraction.system.service.SystemDictionaryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/dictionaries")
public class SystemDictionaryController {
    private final SystemDictionaryService dictionaryService;

    public SystemDictionaryController(SystemDictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    @GetMapping("/types")
    public ApiResponse<List<DictTypeResponse>> types(@RequestParam(value = "keyword", required = false) String keyword,
                                                     @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(dictionaryService.types(keyword, status));
    }

    @PostMapping("/types")
    public ApiResponse<DictTypeResponse> createType(@RequestBody DictTypeRequest request) {
        return ApiResponse.success(dictionaryService.createType(request));
    }

    @PutMapping("/types/{id}")
    public ApiResponse<DictTypeResponse> updateType(@PathVariable("id") String id, @RequestBody DictTypeRequest request) {
        return ApiResponse.success(dictionaryService.updateType(id, request));
    }

    @PostMapping("/types/{id}/enable")
    public ApiResponse<DictTypeResponse> enableType(@PathVariable("id") String id) {
        return ApiResponse.success(dictionaryService.enableType(id));
    }

    @PostMapping("/types/{id}/disable")
    public ApiResponse<DictTypeResponse> disableType(@PathVariable("id") String id) {
        return ApiResponse.success(dictionaryService.disableType(id));
    }

    @DeleteMapping("/types/{id}")
    public ApiResponse<Void> deleteType(@PathVariable("id") String id) {
        dictionaryService.deleteType(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/items")
    public ApiResponse<List<DictItemResponse>> items(@RequestParam(value = "dictCode", required = false) String dictCode,
                                                     @RequestParam(value = "parentValue", required = false) String parentValue,
                                                     @RequestParam(value = "keyword", required = false) String keyword,
                                                     @RequestParam(value = "enabled", required = false) Boolean enabled) {
        return ApiResponse.success(dictionaryService.items(dictCode, parentValue, keyword, enabled));
    }

    @PostMapping("/items")
    public ApiResponse<DictItemResponse> createItem(@RequestBody DictItemRequest request) {
        return ApiResponse.success(dictionaryService.createItem(request));
    }

    @PutMapping("/items/{id}")
    public ApiResponse<DictItemResponse> updateItem(@PathVariable("id") String id, @RequestBody DictItemRequest request) {
        return ApiResponse.success(dictionaryService.updateItem(id, request));
    }

    @PostMapping("/items/{id}/enable")
    public ApiResponse<DictItemResponse> enableItem(@PathVariable("id") String id) {
        return ApiResponse.success(dictionaryService.enableItem(id));
    }

    @PostMapping("/items/{id}/disable")
    public ApiResponse<DictItemResponse> disableItem(@PathVariable("id") String id) {
        return ApiResponse.success(dictionaryService.disableItem(id));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> deleteItem(@PathVariable("id") String id) {
        dictionaryService.deleteItem(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/options")
    public ApiResponse<List<Map<String, Object>>> options(@RequestParam("dictCode") String dictCode) {
        return ApiResponse.success(dictionaryService.options(dictCode));
    }

    @GetMapping("/business-category-tree")
    public ApiResponse<List<Map<String, Object>>> businessCategoryTree() {
        return ApiResponse.success(dictionaryService.businessCategoryTree());
    }
}
