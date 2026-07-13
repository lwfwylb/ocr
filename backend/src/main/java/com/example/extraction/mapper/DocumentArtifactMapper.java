package com.example.extraction.mapper;

import com.example.extraction.artifact.domain.DocumentArtifactRecord;
import com.example.extraction.artifact.domain.DocumentArtifactStepRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DocumentArtifactMapper {
    List<DocumentArtifactRecord> selectByTaskId(@Param("taskId") String taskId);

    List<DocumentArtifactRecord> selectByTraceId(@Param("traceId") String traceId);

    DocumentArtifactRecord selectById(@Param("id") String id);

    DocumentArtifactRecord selectFirstByTraceIdAndType(@Param("traceId") String traceId,
                                                       @Param("artifactType") String artifactType);

    DocumentArtifactRecord selectFirstByTaskIdAndType(@Param("taskId") String taskId,
                                                      @Param("artifactType") String artifactType);

    void insertArtifact(DocumentArtifactRecord record);

    List<DocumentArtifactStepRecord> selectStepsByTaskId(@Param("taskId") String taskId);

    List<DocumentArtifactStepRecord> selectStepsByTraceId(@Param("traceId") String traceId);

    DocumentArtifactStepRecord selectStepByTaskIdAndCode(@Param("taskId") String taskId,
                                                         @Param("stepCode") String stepCode);

    void insertStep(DocumentArtifactStepRecord record);
}
