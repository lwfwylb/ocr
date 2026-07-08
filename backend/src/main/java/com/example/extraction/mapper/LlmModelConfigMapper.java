package com.example.extraction.mapper;

import com.example.extraction.model.domain.LlmModelConfigRecord;
import com.example.extraction.model.dto.LlmModelQueryRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LlmModelConfigMapper {
    List<LlmModelConfigRecord> selectList(@Param("query") LlmModelQueryRequest query);

    List<LlmModelConfigRecord> selectEnabled();

    LlmModelConfigRecord selectById(@Param("id") String id);

    LlmModelConfigRecord selectByModelCode(@Param("modelCode") String modelCode);

    void insert(LlmModelConfigRecord record);

    int update(LlmModelConfigRecord record);

    int updateStatus(@Param("id") String id, @Param("status") String status);

    int clearDefault();

    int setDefault(@Param("id") String id);
}
