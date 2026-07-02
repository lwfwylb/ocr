# 智能要素提取平台后端 P0

本工程是配置向导优先的第一版后端，使用 Spring Boot + MyBatis，接口围绕“完整配置草稿”的保存、读取、发布、复制、停用设计。

## 设计边界

- P0 实现配置中心后端，不执行真实 OCR、LLM、自动建表、下游推送。
- 配置向导使用聚合接口保存，便于前端从 Mock 快速切换到 API。
- 数据库字段尽量使用 `varchar`、`decimal`、`timestamp`、`clob/text` 等通用类型，兼容 MySQL、Oracle、达梦。
- 复杂配置在 P0 同时保留 `config_payload` 快照，后续可逐步拆到明细表。

## 常用接口

```text
GET  /api/config/extract-configs
GET  /api/config/extract-configs/{id}
POST /api/config/extract-configs/draft
PUT  /api/config/extract-configs/{id}/draft
POST /api/config/extract-configs/{id}/copy
POST /api/config/extract-configs/{id}/publish
POST /api/config/extract-configs/{id}/disable
POST /api/config/extract-configs/{id}/validate
GET  /api/config/options
```

## 新建草稿请求示例

```json
{
  "baseInfo": {
    "configName": "划款指令-运营部-提取配置",
    "category": "资金业务",
    "subCategory": "划款指令",
    "templateType": "通用划款指令模板",
    "documentType": "划款指令",
    "departmentId": "运营部",
    "ownerRole": "模板配置员",
    "defaultPriority": "HIGH"
  },
  "parseConfig": {
    "engineCode": "paddleocr_vl",
    "outputFormat": "Markdown",
    "preprocessEnabled": true
  },
  "storageConfig": {
    "storageMode": "REUSE",
    "mappingProfileName": "划款指令-资金结果表映射",
    "targetTable": "ext_fund_business_result",
    "targetTableName": "基金业务要素结果表",
    "saveMode": "SINGLE"
  },
  "resultTableColumns": [
    {
      "columnName": "business_amount",
      "columnCnName": "业务金额",
      "dbType": "decimal",
      "precision": 18,
      "scale": 2,
      "required": true
    }
  ],
  "extractFields": [
    {
      "fieldCode": "amount",
      "fieldName": "金额",
      "extractRequired": true,
      "targetColumn": "business_amount"
    }
  ],
  "fieldMappings": [
    {
      "extractFieldCode": "amount",
      "targetColumn": "business_amount",
      "requiredForStorage": true
    }
  ],
  "extractStrategy": {
    "aiEnabled": true,
    "outputMode": "SINGLE",
    "defaultStrategy": "AI_FIRST_RULE_FALLBACK",
    "confidenceThreshold": 0.9
  }
}
```

```bash
curl -X POST http://127.0.0.1:8889/api/config/extract-configs/draft \
  -H "Content-Type: application/json" \
  -d @payload.json
```

## 数据库脚本

- `src/main/resources/db/mysql/schema.sql`
- `src/main/resources/db/oracle/schema.sql`
- `src/main/resources/db/dameng/schema.sql`

## 启动

```bash
mvn clean package
mvn spring-boot:run
```

默认启用 Maven 的 `mysql` profile，并且 Spring 默认激活 `mysql` 运行配置，因此本地直接编译会携带 MySQL 驱动。

如需切换 Oracle 或达梦，需要同时切换 Maven profile 和 Spring profile：

```bash
SPRING_PROFILES_ACTIVE=oracle mvn -Poracle spring-boot:run
SPRING_PROFILES_ACTIVE=dameng mvn -Pdameng spring-boot:run
```
