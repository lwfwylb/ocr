package com.example.extraction.mapper;

import com.example.extraction.task.domain.ExtractTaskRecord;
import com.example.extraction.task.dto.TaskQueryRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtractTaskMapper {
    List<ExtractTaskRecord> selectList(@Param("query") TaskQueryRequest query);

    List<ExtractTaskRecord> selectFailed(@Param("query") TaskQueryRequest query);

    ExtractTaskRecord selectByTaskId(@Param("taskId") String taskId);

    ExtractTaskRecord selectByTraceId(@Param("traceId") String traceId);

    List<ExtractTaskRecord> selectNextQueued();

    int countQueueTasks(@Param("departmentId") String departmentId, @Param("queueLevel") String queueLevel);

    void insert(ExtractTaskRecord record);

    int updateDispatch(ExtractTaskRecord record);

    int updateRetry(ExtractTaskRecord record);

    int updateExecutionState(ExtractTaskRecord record);
}
