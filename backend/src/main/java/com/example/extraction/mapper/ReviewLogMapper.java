package com.example.extraction.mapper;

import com.example.extraction.result.domain.ReviewLogRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ReviewLogMapper {
    List<ReviewLogRecord> selectByTaskId(@Param("taskId") String taskId);

    void insert(ReviewLogRecord record);
}
