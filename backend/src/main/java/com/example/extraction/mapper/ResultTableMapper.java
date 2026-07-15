package com.example.extraction.mapper;

import com.example.extraction.configuration.domain.ResultTableColumnRecord;
import com.example.extraction.configuration.domain.ResultTableRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ResultTableMapper {
    List<ResultTableRecord> selectTables(@Param("keyword") String keyword, @Param("status") String status);

    ResultTableRecord selectTableByCode(@Param("tableCode") String tableCode);

    List<ResultTableColumnRecord> selectColumnsByTableId(@Param("resultTableId") String resultTableId);

    int countTables();

    void insertTable(ResultTableRecord record);

    int updateTable(ResultTableRecord record);

    void deleteColumnsByTableId(@Param("resultTableId") String resultTableId);

    void insertColumn(ResultTableColumnRecord record);
}
