package com.example.extraction.mapper;

import com.example.extraction.configuration.domain.ExtractConfigRecord;
import com.example.extraction.configuration.dto.ConfigQueryRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtractConfigMapper {
    List<ExtractConfigRecord> selectList(@Param("query") ConfigQueryRequest query);

    ExtractConfigRecord selectById(@Param("id") String id);

    List<ExtractConfigRecord> selectByConfigCode(@Param("configCode") String configCode);

    Integer selectMaxVersion(@Param("configCode") String configCode);

    void insert(ExtractConfigRecord record);

    int updateDraft(ExtractConfigRecord record);

    int updateStatus(@Param("id") String id, @Param("status") String status);

    int disablePublishedByConfigCode(@Param("configCode") String configCode, @Param("excludeId") String excludeId);

    int publish(@Param("id") String id);
}
