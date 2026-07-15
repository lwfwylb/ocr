package com.example.extraction.configuration.dto;

import java.util.ArrayList;
import java.util.List;

public class ResultTableDetailResponse {
    private String id;
    private String tableCode;
    private String tableName;
    private String tableComment;
    private String ownerDepartmentId;
    private String status;
    private List<ConfigWizardPayload.ResultTableColumn> columns = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTableCode() { return tableCode; }
    public void setTableCode(String tableCode) { this.tableCode = tableCode; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getTableComment() { return tableComment; }
    public void setTableComment(String tableComment) { this.tableComment = tableComment; }
    public String getOwnerDepartmentId() { return ownerDepartmentId; }
    public void setOwnerDepartmentId(String ownerDepartmentId) { this.ownerDepartmentId = ownerDepartmentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<ConfigWizardPayload.ResultTableColumn> getColumns() { return columns; }
    public void setColumns(List<ConfigWizardPayload.ResultTableColumn> columns) { this.columns = columns; }
}
