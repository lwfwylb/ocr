package com.example.extraction.system.dto;

public class DictItemRequest {
    private String dictCode;
    private String itemValue;
    private String itemLabel;
    private String parentValue;
    private Integer sortNo;
    private Boolean enabled;
    private String extraJson;
    private String remark;

    public String getDictCode() { return dictCode; }
    public void setDictCode(String dictCode) { this.dictCode = dictCode; }
    public String getItemValue() { return itemValue; }
    public void setItemValue(String itemValue) { this.itemValue = itemValue; }
    public String getItemLabel() { return itemLabel; }
    public void setItemLabel(String itemLabel) { this.itemLabel = itemLabel; }
    public String getParentValue() { return parentValue; }
    public void setParentValue(String parentValue) { this.parentValue = parentValue; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getExtraJson() { return extraJson; }
    public void setExtraJson(String extraJson) { this.extraJson = extraJson; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
