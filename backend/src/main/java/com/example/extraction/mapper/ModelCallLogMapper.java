package com.example.extraction.mapper;

import com.example.extraction.model.domain.ModelCallLogRecord;
import com.example.extraction.model.dto.ModelCallLogQueryRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ModelCallLogMapper {
    List<ModelCallLogRecord> selectList(@Param("query") ModelCallLogQueryRequest query);

    ModelCallLogRecord selectById(@Param("id") String id);

    void insert(ModelCallLogRecord record);
}
