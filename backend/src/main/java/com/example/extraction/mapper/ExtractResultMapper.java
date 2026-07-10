package com.example.extraction.mapper;

import com.example.extraction.result.domain.ExtractResultRecord;
import com.example.extraction.result.dto.ResultQueryRequest;
import com.example.extraction.result.dto.ResultSummaryResponse;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtractResultMapper {
    ExtractResultRecord selectByTaskId(@Param("taskId") String taskId);

    List<ResultSummaryResponse> selectSummaryList(@Param("query") ResultQueryRequest query);

    void insert(ExtractResultRecord record);

    int update(ExtractResultRecord record);
}
