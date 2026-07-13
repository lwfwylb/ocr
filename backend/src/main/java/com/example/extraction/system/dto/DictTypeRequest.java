package com.example.extraction.system.dto;

public class DictTypeRequest {
    private String dictCode;
    private String dictName;
    private String usageScene;
    private String status;
    private Integer sortNo;
    private String remark;

    public String getDictCode() { return dictCode; }
    public void setDictCode(String dictCode) { this.dictCode = dictCode; }
    public String getDictName() { return dictName; }
    public void setDictName(String dictName) { this.dictName = dictName; }
    public String getUsageScene() { return usageScene; }
    public void setUsageScene(String usageScene) { this.usageScene = usageScene; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
