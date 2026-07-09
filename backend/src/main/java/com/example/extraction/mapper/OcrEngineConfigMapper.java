package com.example.extraction.mapper;

import com.example.extraction.model.domain.OcrEngineConfigRecord;
import com.example.extraction.model.dto.OcrEngineQueryRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OcrEngineConfigMapper {
    List<OcrEngineConfigRecord> selectList(@Param("query") OcrEngineQueryRequest query);

    List<OcrEngineConfigRecord> selectEnabled();

    OcrEngineConfigRecord selectById(@Param("id") String id);

    OcrEngineConfigRecord selectByEngineCode(@Param("engineCode") String engineCode);

    void insert(OcrEngineConfigRecord record);

    int update(OcrEngineConfigRecord record);

    int updateStatus(@Param("id") String id, @Param("status") String status);

    int clearDefault();

    int setDefault(@Param("id") String id);
}
