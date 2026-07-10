package com.example.extraction.mapper;

import com.example.extraction.result.domain.DocumentParseResultRecord;
import org.apache.ibatis.annotations.Param;

public interface DocumentParseResultMapper {
    DocumentParseResultRecord selectByTaskId(@Param("taskId") String taskId);

    void insert(DocumentParseResultRecord record);

    int update(DocumentParseResultRecord record);
}
