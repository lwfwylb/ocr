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

    List<ExtractTaskRecord> selectNextQueued(@Param("departmentId") String departmentId);

    int countQueueTasks(@Param("departmentId") String departmentId, @Param("queueLevel") String queueLevel);

    int incrementQueuePositions(@Param("departmentId") String departmentId,
                                @Param("queueLevel") String queueLevel,
                                @Param("fromPosition") Integer fromPosition,
                                @Param("toPosition") Integer toPosition,
                                @Param("excludeTaskId") String excludeTaskId);

    int decrementQueuePositions(@Param("departmentId") String departmentId,
                                @Param("queueLevel") String queueLevel,
                                @Param("fromPosition") Integer fromPosition,
                                @Param("toPosition") Integer toPosition,
                                @Param("excludeTaskId") String excludeTaskId);

    void insert(ExtractTaskRecord record);

    int updateDispatch(ExtractTaskRecord record);

    int updateRetry(ExtractTaskRecord record);

    int updateExecutionState(ExtractTaskRecord record);
}
