package com.example.extraction.mapper;

import com.example.extraction.system.domain.SysDictItemRecord;
import com.example.extraction.system.domain.SysDictTypeRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SystemDictionaryMapper {
    List<SysDictTypeRecord> selectTypes(@Param("keyword") String keyword, @Param("status") String status);

    SysDictTypeRecord selectTypeById(@Param("id") String id);

    SysDictTypeRecord selectTypeByCode(@Param("dictCode") String dictCode);

    int countTypes();

    void insertType(SysDictTypeRecord record);

    int updateType(SysDictTypeRecord record);

    int updateTypeStatus(@Param("id") String id, @Param("status") String status);

    int deleteType(@Param("id") String id);

    List<SysDictItemRecord> selectItems(@Param("dictCode") String dictCode,
                                        @Param("parentValue") String parentValue,
                                        @Param("keyword") String keyword,
                                        @Param("enabled") String enabled);

    SysDictItemRecord selectItemById(@Param("id") String id);

    SysDictItemRecord selectItemByCodeValue(@Param("dictCode") String dictCode, @Param("itemValue") String itemValue);

    int countItemsByDictCode(@Param("dictCode") String dictCode);

    void insertItem(SysDictItemRecord record);

    int updateItem(SysDictItemRecord record);

    int updateItemEnabled(@Param("id") String id, @Param("enabled") String enabled);

    int deleteItem(@Param("id") String id);
}
