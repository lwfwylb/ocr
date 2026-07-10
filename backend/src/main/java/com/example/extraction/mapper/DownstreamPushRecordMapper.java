package com.example.extraction.mapper;

import com.example.extraction.result.domain.DownstreamPushRecord;
import com.example.extraction.result.dto.PushQueryRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DownstreamPushRecordMapper {
    List<DownstreamPushRecord> selectList(@Param("query") PushQueryRequest query);

    DownstreamPushRecord selectByPushId(@Param("pushId") String pushId);

    List<DownstreamPushRecord> selectByTaskId(@Param("taskId") String taskId);

    void insert(DownstreamPushRecord record);

    int update(DownstreamPushRecord record);
}
