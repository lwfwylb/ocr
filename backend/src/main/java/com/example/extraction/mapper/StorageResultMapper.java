package com.example.extraction.mapper;

import com.example.extraction.result.domain.StorageResultRecord;
import com.example.extraction.result.dto.StorageQueryRequest;
import com.example.extraction.result.dto.StorageRecordResponse;
import com.example.extraction.result.dto.StorageTableResponse;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface StorageResultMapper {
    StorageResultRecord selectByTaskId(@Param("taskId") String taskId);

    List<StorageTableResponse> selectTables(@Param("keyword") String keyword);

    List<StorageRecordResponse> selectRecords(@Param("query") StorageQueryRequest query);

    void insert(StorageResultRecord record);

    int update(StorageResultRecord record);
}
