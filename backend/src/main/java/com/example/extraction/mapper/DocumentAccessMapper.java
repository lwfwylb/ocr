package com.example.extraction.mapper;

import com.example.extraction.document.domain.DocumentAccessRecord;
import com.example.extraction.document.dto.DocumentAccessQueryRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DocumentAccessMapper {
    List<DocumentAccessRecord> selectList(@Param("query") DocumentAccessQueryRequest query);

    List<DocumentAccessRecord> selectPendingConfirm(@Param("query") DocumentAccessQueryRequest query);

    DocumentAccessRecord selectById(@Param("id") String id);

    DocumentAccessRecord selectByTraceId(@Param("traceId") String traceId);

    void insert(DocumentAccessRecord record);

    int updateMatchResult(DocumentAccessRecord record);

    int confirm(DocumentAccessRecord record);
}
