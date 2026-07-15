package com.example.extraction.mapper;

import com.example.extraction.model.domain.PromptTemplateRecord;
import org.apache.ibatis.annotations.Param;

public interface PromptTemplateMapper {
    PromptTemplateRecord selectByType(@Param("templateType") String templateType);

    void insert(PromptTemplateRecord record);

    int update(PromptTemplateRecord record);
}
