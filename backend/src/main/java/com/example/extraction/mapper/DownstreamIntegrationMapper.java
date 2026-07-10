package com.example.extraction.mapper;

import com.example.extraction.integration.domain.DownstreamServiceConfigRecord;
import com.example.extraction.integration.domain.DownstreamSystemConfigRecord;
import com.example.extraction.integration.dto.IntegrationQueryRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DownstreamIntegrationMapper {
    List<DownstreamSystemConfigRecord> selectSystems(@Param("query") IntegrationQueryRequest query);

    DownstreamSystemConfigRecord selectSystemById(@Param("id") String id);

    DownstreamSystemConfigRecord selectSystemByCode(@Param("systemCode") String systemCode);

    List<DownstreamServiceConfigRecord> selectServices(@Param("query") IntegrationQueryRequest query);

    DownstreamServiceConfigRecord selectServiceById(@Param("id") String id);

    DownstreamServiceConfigRecord selectServiceByCode(@Param("serviceCode") String serviceCode);

    int countSystems();

    int countServicesBySystemId(@Param("systemId") String systemId);

    void insertSystem(DownstreamSystemConfigRecord record);

    void insertService(DownstreamServiceConfigRecord record);

    int updateSystem(DownstreamSystemConfigRecord record);

    int updateService(DownstreamServiceConfigRecord record);

    int updateSystemStatus(@Param("id") String id, @Param("status") String status);

    int updateServiceEnabled(@Param("id") String id, @Param("enabled") String enabled);

    int deleteSystem(@Param("id") String id);

    int deleteService(@Param("id") String id);
}
