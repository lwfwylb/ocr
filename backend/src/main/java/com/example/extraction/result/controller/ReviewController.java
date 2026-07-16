package com.example.extraction.result.controller;

import com.example.extraction.common.ApiResponse;
import com.example.extraction.common.PageResponse;
import com.example.extraction.common.PageSupport;
import com.example.extraction.result.dto.ResultSummaryResponse;
import com.example.extraction.result.dto.ReviewDetailResponse;
import com.example.extraction.result.dto.ReviewQueryRequest;
import com.example.extraction.result.dto.ReviewSubmitRequest;
import com.example.extraction.result.service.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ResultSummaryResponse>> list(ReviewQueryRequest query) {
        return ApiResponse.success(PageSupport.page(query, () -> reviewService.list(query)));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<ReviewDetailResponse> detail(@PathVariable("taskId") String taskId) {
        return ApiResponse.success(reviewService.detail(taskId));
    }

    @PostMapping("/{taskId}/draft")
    public ApiResponse<ReviewDetailResponse> saveDraft(@PathVariable("taskId") String taskId,
                                                       @RequestBody ReviewSubmitRequest request) {
        return ApiResponse.success(reviewService.saveDraft(taskId, request));
    }

    @PostMapping("/{taskId}/approve")
    public ApiResponse<ReviewDetailResponse> approve(@PathVariable("taskId") String taskId,
                                                     @RequestBody ReviewSubmitRequest request) {
        return ApiResponse.success(reviewService.approve(taskId, request));
    }

    @PostMapping("/{taskId}/reject")
    public ApiResponse<ReviewDetailResponse> reject(@PathVariable("taskId") String taskId,
                                                    @RequestBody ReviewSubmitRequest request) {
        return ApiResponse.success(reviewService.reject(taskId, request));
    }
}
