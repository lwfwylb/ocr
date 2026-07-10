package com.example.extraction.mapper;

import com.example.extraction.task.domain.TaskStageLogRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TaskStageLogMapper {
    List<TaskStageLogRecord> selectByTaskId(@Param("taskId") String taskId);

    void insert(TaskStageLogRecord record);
}
