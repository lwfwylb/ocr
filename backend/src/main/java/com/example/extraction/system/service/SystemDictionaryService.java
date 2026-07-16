package com.example.extraction.system.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.common.IdGenerator;
import com.example.extraction.mapper.SystemDictionaryMapper;
import com.example.extraction.system.domain.SysDictItemRecord;
import com.example.extraction.system.domain.SysDictTypeRecord;
import com.example.extraction.system.dto.DictItemRequest;
import com.example.extraction.system.dto.DictItemResponse;
import com.example.extraction.system.dto.DictTypeRequest;
import com.example.extraction.system.dto.DictTypeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SystemDictionaryService {
    private final SystemDictionaryMapper dictionaryMapper;

    public SystemDictionaryService(SystemDictionaryMapper dictionaryMapper) {
        this.dictionaryMapper = dictionaryMapper;
    }

    @Transactional
    public List<DictTypeResponse> types(String keyword, String status) {
        ensureDefaults();
        return dictionaryMapper.selectTypes(keyword, status).stream().map(this::toTypeResponse).toList();
    }

    @Transactional
    public void ensureDefaultsForList() {
        ensureDefaults();
    }

    public List<DictTypeResponse> typesWithoutDefaults(String keyword, String status) {
        return dictionaryMapper.selectTypes(keyword, status).stream().map(this::toTypeResponse).toList();
    }

    @Transactional
    public DictTypeResponse createType(DictTypeRequest request) {
        ensureDefaults();
        validateTypeRequest(request);
        if (dictionaryMapper.selectTypeByCode(request.getDictCode()) != null) {
            throw new BusinessException("DICT_409", "字典编码已存在");
        }
        SysDictTypeRecord record = new SysDictTypeRecord();
        record.setId(IdGenerator.nextId("DTYPE"));
        fillType(record, request);
        record.setCreatedBy("system");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        dictionaryMapper.insertType(record);
        return toTypeResponse(record);
    }

    @Transactional
    public DictTypeResponse updateType(String id, DictTypeRequest request) {
        ensureDefaults();
        validateTypeRequest(request);
        SysDictTypeRecord record = requireType(id);
        SysDictTypeRecord sameCode = dictionaryMapper.selectTypeByCode(request.getDictCode());
        if (sameCode != null && !id.equals(sameCode.getId())) {
            throw new BusinessException("DICT_409", "字典编码已存在");
        }
        fillType(record, request);
        record.setUpdatedAt(LocalDateTime.now());
        dictionaryMapper.updateType(record);
        return toTypeResponse(requireType(id));
    }

    @Transactional
    public DictTypeResponse enableType(String id) {
        dictionaryMapper.updateTypeStatus(id, "ENABLED");
        return toTypeResponse(requireType(id));
    }

    @Transactional
    public DictTypeResponse disableType(String id) {
        dictionaryMapper.updateTypeStatus(id, "DISABLED");
        return toTypeResponse(requireType(id));
    }

    @Transactional
    public void deleteType(String id) {
        SysDictTypeRecord record = requireType(id);
        if (dictionaryMapper.countItemsByDictCode(record.getDictCode()) > 0) {
            throw new BusinessException("DICT_409", "该字典类型下仍有字典项，请先删除字典项");
        }
        dictionaryMapper.deleteType(id);
    }

    @Transactional
    public List<DictItemResponse> items(String dictCode, String parentValue, String keyword, Boolean enabled) {
        ensureDefaults();
        String enabledFlag = enabled == null ? null : (enabled ? "1" : "0");
        return dictionaryMapper.selectItems(dictCode, parentValue, keyword, enabledFlag).stream().map(this::toItemResponse).toList();
    }

    public List<DictItemResponse> itemsWithoutDefaults(String dictCode, String parentValue, String keyword, Boolean enabled) {
        String enabledFlag = enabled == null ? null : (enabled ? "1" : "0");
        return dictionaryMapper.selectItems(dictCode, parentValue, keyword, enabledFlag).stream().map(this::toItemResponse).toList();
    }

    @Transactional
    public DictItemResponse createItem(DictItemRequest request) {
        ensureDefaults();
        validateItemRequest(request);
        requireTypeCode(request.getDictCode());
        if (dictionaryMapper.selectItemByCodeValue(request.getDictCode(), request.getItemValue()) != null) {
            throw new BusinessException("DICT_409", "同一字典下字典值已存在");
        }
        SysDictItemRecord record = new SysDictItemRecord();
        record.setId(IdGenerator.nextId("DITEM"));
        fillItem(record, request);
        record.setCreatedBy("system");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        dictionaryMapper.insertItem(record);
        return toItemResponse(record);
    }

    @Transactional
    public DictItemResponse updateItem(String id, DictItemRequest request) {
        ensureDefaults();
        validateItemRequest(request);
        requireTypeCode(request.getDictCode());
        SysDictItemRecord record = requireItem(id);
        SysDictItemRecord sameValue = dictionaryMapper.selectItemByCodeValue(request.getDictCode(), request.getItemValue());
        if (sameValue != null && !id.equals(sameValue.getId())) {
            throw new BusinessException("DICT_409", "同一字典下字典值已存在");
        }
        fillItem(record, request);
        record.setUpdatedAt(LocalDateTime.now());
        dictionaryMapper.updateItem(record);
        return toItemResponse(requireItem(id));
    }

    @Transactional
    public DictItemResponse enableItem(String id) {
        dictionaryMapper.updateItemEnabled(id, "1");
        return toItemResponse(requireItem(id));
    }

    @Transactional
    public DictItemResponse disableItem(String id) {
        dictionaryMapper.updateItemEnabled(id, "0");
        return toItemResponse(requireItem(id));
    }

    @Transactional
    public void deleteItem(String id) {
        requireItem(id);
        dictionaryMapper.deleteItem(id);
    }

    @Transactional
    public List<Map<String, Object>> options(String dictCode) {
        ensureDefaults();
        return dictionaryMapper.selectItems(dictCode, null, null, "1").stream().map(this::toOption).toList();
    }

    @Transactional
    public List<Map<String, Object>> businessCategoryTree() {
        ensureDefaults();
        List<SysDictItemRecord> categories = dictionaryMapper.selectItems("BUSINESS_CATEGORY", null, null, "1");
        List<SysDictItemRecord> subCategories = dictionaryMapper.selectItems("BUSINESS_SUB_CATEGORY", null, null, "1");
        List<SysDictItemRecord> templates = dictionaryMapper.selectItems("TEMPLATE_TYPE", null, null, "1");
        Map<String, List<SysDictItemRecord>> subByCategory = subCategories.stream()
                .collect(Collectors.groupingBy(item -> item.getParentValue() == null ? "" : item.getParentValue(), LinkedHashMap::new, Collectors.toList()));
        Map<String, List<SysDictItemRecord>> templateBySub = templates.stream()
                .collect(Collectors.groupingBy(item -> item.getParentValue() == null ? "" : item.getParentValue(), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysDictItemRecord category : categories) {
            Map<String, Object> categoryOption = toOption(category);
            List<Map<String, Object>> children = new ArrayList<>();
            for (SysDictItemRecord subCategory : subByCategory.getOrDefault(category.getItemValue(), List.of())) {
                Map<String, Object> subOption = toOption(subCategory);
                subOption.put("templates", templateBySub.getOrDefault(subCategory.getItemValue(), List.of()).stream()
                        .map(SysDictItemRecord::getItemLabel)
                        .toList());
                children.add(subOption);
            }
            categoryOption.put("children", children);
            result.add(categoryOption);
        }
        return result;
    }

    public List<Map<String, Object>> departments() {
        return options("DEPARTMENT");
    }

    public List<Map<String, Object>> roles() {
        return options("ROLE");
    }

    private void ensureDefaults() {
        if (dictionaryMapper.countTypes() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        insertDefaultType("DEPARTMENT", "部门", "组织与权限", 10, now);
        insertDefaultType("ROLE", "角色", "权限控制", 20, now);
        insertDefaultType("BUSINESS_CATEGORY", "业务分类", "配置向导", 30, now);
        insertDefaultType("BUSINESS_SUB_CATEGORY", "业务子类", "配置向导", 40, now);
        insertDefaultType("TEMPLATE_TYPE", "模板/表单类型", "配置向导", 50, now);
        insertDefaultType("DOCUMENT_TYPE", "文档类型", "文档接入", 60, now);

        insertDefaultItem("DEPARTMENT", "运营部", "运营部", null, 10, now);
        insertDefaultItem("DEPARTMENT", "财务部", "财务部", null, 20, now);
        insertDefaultItem("DEPARTMENT", "产品部", "产品部", null, 30, now);
        insertDefaultItem("ROLE", "模板配置员", "模板配置员", null, 10, now);
        insertDefaultItem("ROLE", "复核人员", "复核人员", null, 20, now);
        insertDefaultItem("ROLE", "运营复核岗", "运营复核岗", null, 30, now);
        insertDefaultItem("ROLE", "部门管理员", "部门管理员", null, 40, now);
        insertDefaultItem("ROLE", "普通业务用户", "普通业务用户", null, 50, now);

        insertDefaultItem("BUSINESS_CATEGORY", "资金业务", "资金业务", null, 10, now);
        insertDefaultItem("BUSINESS_CATEGORY", "基金交易", "基金交易", null, 20, now);
        insertDefaultItem("BUSINESS_CATEGORY", "客户业务", "客户业务", null, 30, now);
        insertDefaultItem("BUSINESS_SUB_CATEGORY", "划款指令", "划款指令", "资金业务", 10, now);
        insertDefaultItem("BUSINESS_SUB_CATEGORY", "银行回单", "银行回单", "资金业务", 20, now);
        insertDefaultItem("BUSINESS_SUB_CATEGORY", "基金申购", "基金申购", "基金交易", 30, now);
        insertDefaultItem("BUSINESS_SUB_CATEGORY", "基金赎回", "基金赎回", "基金交易", 40, now);
        insertDefaultItem("BUSINESS_SUB_CATEGORY", "开户资料", "开户资料", "客户业务", 50, now);
        insertDefaultItem("TEMPLATE_TYPE", "通用划款指令模板", "通用划款指令模板", "划款指令", 10, now);
        insertDefaultItem("TEMPLATE_TYPE", "托管行划款指令模板", "托管行划款指令模板", "划款指令", 20, now);
        insertDefaultItem("TEMPLATE_TYPE", "通用银行回单模板", "通用银行回单模板", "银行回单", 30, now);
        insertDefaultItem("TEMPLATE_TYPE", "大成基金申购单", "大成基金申购单", "基金申购", 40, now);
        insertDefaultItem("TEMPLATE_TYPE", "南方基金申购单", "南方基金申购单", "基金申购", 50, now);
        insertDefaultItem("TEMPLATE_TYPE", "华夏基金申购单", "华夏基金申购单", "基金申购", 60, now);
        insertDefaultItem("TEMPLATE_TYPE", "大成基金赎回单", "大成基金赎回单", "基金赎回", 70, now);
        insertDefaultItem("TEMPLATE_TYPE", "南方基金赎回单", "南方基金赎回单", "基金赎回", 80, now);
        insertDefaultItem("TEMPLATE_TYPE", "机构客户开户资料", "机构客户开户资料", "开户资料", 90, now);
        insertDefaultItem("TEMPLATE_TYPE", "产品户开户资料", "产品户开户资料", "开户资料", 100, now);
        insertDefaultItem("DOCUMENT_TYPE", "划款指令", "划款指令", null, 10, now);
        insertDefaultItem("DOCUMENT_TYPE", "银行回单", "银行回单", null, 20, now);
        insertDefaultItem("DOCUMENT_TYPE", "基金申购", "基金申购", null, 30, now);
        insertDefaultItem("DOCUMENT_TYPE", "基金赎回", "基金赎回", null, 40, now);
        insertDefaultItem("DOCUMENT_TYPE", "开户资料", "开户资料", null, 50, now);
    }

    private void insertDefaultType(String code, String name, String scene, int sortNo, LocalDateTime now) {
        SysDictTypeRecord record = new SysDictTypeRecord();
        record.setId(IdGenerator.nextId("DTYPE"));
        record.setDictCode(code);
        record.setDictName(name);
        record.setUsageScene(scene);
        record.setStatus("ENABLED");
        record.setSortNo(sortNo);
        record.setCreatedBy("system");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        dictionaryMapper.insertType(record);
    }

    private void insertDefaultItem(String dictCode, String value, String label, String parentValue, int sortNo, LocalDateTime now) {
        SysDictItemRecord record = new SysDictItemRecord();
        record.setId(IdGenerator.nextId("DITEM"));
        record.setDictCode(dictCode);
        record.setItemValue(value);
        record.setItemLabel(label);
        record.setParentValue(parentValue);
        record.setSortNo(sortNo);
        record.setEnabled("1");
        record.setCreatedBy("system");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        dictionaryMapper.insertItem(record);
    }

    private void validateTypeRequest(DictTypeRequest request) {
        if (!StringUtils.hasText(request.getDictCode())) {
            throw new BusinessException("PARAM_400", "字典编码不能为空");
        }
        if (!request.getDictCode().matches("^[A-Z][A-Z0-9_]*$")) {
            throw new BusinessException("PARAM_400", "字典编码只能包含大写字母、数字、下划线，并以大写字母开头");
        }
        if (!StringUtils.hasText(request.getDictName())) {
            throw new BusinessException("PARAM_400", "字典名称不能为空");
        }
    }

    private void validateItemRequest(DictItemRequest request) {
        if (!StringUtils.hasText(request.getDictCode())) {
            throw new BusinessException("PARAM_400", "所属字典不能为空");
        }
        if (!StringUtils.hasText(request.getItemValue())) {
            throw new BusinessException("PARAM_400", "字典值不能为空");
        }
        if (!StringUtils.hasText(request.getItemLabel())) {
            throw new BusinessException("PARAM_400", "显示名称不能为空");
        }
    }

    private void fillType(SysDictTypeRecord record, DictTypeRequest request) {
        record.setDictCode(request.getDictCode());
        record.setDictName(request.getDictName());
        record.setUsageScene(request.getUsageScene());
        record.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ENABLED");
        record.setSortNo(request.getSortNo() == null ? 100 : request.getSortNo());
        record.setRemark(request.getRemark());
    }

    private void fillItem(SysDictItemRecord record, DictItemRequest request) {
        record.setDictCode(request.getDictCode());
        record.setItemValue(request.getItemValue());
        record.setItemLabel(request.getItemLabel());
        record.setParentValue(request.getParentValue());
        record.setSortNo(request.getSortNo() == null ? 100 : request.getSortNo());
        record.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? "0" : "1");
        record.setExtraJson(request.getExtraJson());
        record.setRemark(request.getRemark());
    }

    private SysDictTypeRecord requireType(String id) {
        SysDictTypeRecord record = dictionaryMapper.selectTypeById(id);
        if (record == null) {
            throw new BusinessException("DICT_404", "字典类型不存在");
        }
        return record;
    }

    private void requireTypeCode(String dictCode) {
        if (dictionaryMapper.selectTypeByCode(dictCode) == null) {
            throw new BusinessException("DICT_404", "所属字典不存在");
        }
    }

    private SysDictItemRecord requireItem(String id) {
        SysDictItemRecord record = dictionaryMapper.selectItemById(id);
        if (record == null) {
            throw new BusinessException("DICT_404", "字典项不存在");
        }
        return record;
    }

    private DictTypeResponse toTypeResponse(SysDictTypeRecord record) {
        DictTypeResponse response = new DictTypeResponse();
        response.setId(record.getId());
        response.setDictCode(record.getDictCode());
        response.setDictName(record.getDictName());
        response.setUsageScene(record.getUsageScene());
        response.setStatus(record.getStatus());
        response.setSortNo(record.getSortNo());
        response.setRemark(record.getRemark());
        response.setCreatedBy(record.getCreatedBy());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    private DictItemResponse toItemResponse(SysDictItemRecord record) {
        DictItemResponse response = new DictItemResponse();
        response.setId(record.getId());
        response.setDictCode(record.getDictCode());
        response.setItemValue(record.getItemValue());
        response.setItemLabel(record.getItemLabel());
        response.setParentValue(record.getParentValue());
        response.setSortNo(record.getSortNo());
        response.setEnabled("1".equals(record.getEnabled()));
        response.setExtraJson(record.getExtraJson());
        response.setRemark(record.getRemark());
        response.setCreatedBy(record.getCreatedBy());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    private Map<String, Object> toOption(SysDictItemRecord record) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", record.getItemValue());
        option.put("label", record.getItemLabel());
        option.put("parentValue", record.getParentValue());
        option.put("sortNo", record.getSortNo());
        option.put("extraJson", record.getExtraJson());
        return option;
    }
}
