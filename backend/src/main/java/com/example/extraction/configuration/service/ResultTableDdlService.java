package com.example.extraction.configuration.service;

import com.example.extraction.common.BusinessException;
import com.example.extraction.configuration.dto.ConfigWizardPayload;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ResultTableDdlService {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Set<String> SUPPORTED_TYPES = Set.of("varchar", "char", "decimal", "number", "date", "datetime");

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public ResultTableDdlService(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    public String previewDdl(ConfigWizardPayload payload) {
        ConfigWizardPayload.StorageConfig storageConfig = storageConfig(payload);
        if (!storageEnabled(payload)) {
            return "-- 当前配置未启用结果落库\n-- 不生成 DDL，不写入物理结果表，仅保留提取、加工、复核和下游推送能力。";
        }
        if (!"CREATE".equals(storageConfig.getStorageMode())) {
            return "-- 当前配置复用已有表：" + nullToDash(storageConfig.getTargetTable()) + "\n-- 验证阶段不执行 DDL，仅校验目标字段映射。";
        }
        DatabaseDialect dialect = resolveDialect();
        List<String> statements = buildCreateStatements(payload, dialect);
        StringBuilder ddl = new StringBuilder();
        ddl.append("-- 当前数据库方言：").append(dialect.displayName).append("\n");
        ddl.append("-- 验证阶段仅预览 DDL，不会真正创建物理结果表；发布阶段才会执行。\n");
        ddl.append(String.join(";\n\n", statements));
        if (!statements.isEmpty()) {
            ddl.append(";");
        }
        return ddl.toString();
    }

    public List<Map<String, Object>> validateCreateTable(ConfigWizardPayload payload) {
        List<Map<String, Object>> issues = new ArrayList<>();
        ConfigWizardPayload.StorageConfig storageConfig = storageConfig(payload);
        if (!storageEnabled(payload) || !"CREATE".equals(storageConfig.getStorageMode())) {
            return issues;
        }
        DatabaseDialect dialect = resolveDialect();
        issues.add(issue("INFO", "当前数据库方言识别为 " + dialect.displayName + "，验证阶段只做 DDL 预检查，发布阶段才会执行自动建表"));
        validateIdentifier("目标表编码", storageConfig.getTargetTable(), dialect, issues);
        if (payload.getResultTableColumns() != null) {
            for (ConfigWizardPayload.ResultTableColumn column : payload.getResultTableColumns()) {
                validateIdentifier("目标字段", column.getColumnName(), dialect, issues);
                if (StringUtils.hasText(column.getDbType()) && !SUPPORTED_TYPES.contains(column.getDbType().toLowerCase(Locale.ROOT))) {
                    issues.add(issue("ERROR", "目标字段 " + nullToDash(column.getColumnName()) + " 的数据库类型暂不支持自动建表：" + column.getDbType()));
                }
            }
        }
        if (payload.getUniqueConstraints() != null) {
            for (ConfigWizardPayload.UniqueConstraint constraint : payload.getUniqueConstraints()) {
                if (!Boolean.TRUE.equals(constraint.getEnabled()) || !Boolean.TRUE.equals(constraint.getGenerateDbIndex())) {
                    continue;
                }
                validateIdentifier("唯一索引名称", constraint.getConstraintName(), dialect, issues);
            }
        }
        if (StringUtils.hasText(storageConfig.getTargetTable()) && tableExists(storageConfig.getTargetTable())) {
            issues.add(issue("ERROR", "物理表已存在，请改为复用已有表或更换新的目标表编码：" + storageConfig.getTargetTable()));
        }
        return issues;
    }

    public void createTableIfNecessary(ConfigWizardPayload payload) {
        ConfigWizardPayload.StorageConfig storageConfig = storageConfig(payload);
        if (!storageEnabled(payload) || !"CREATE".equals(storageConfig.getStorageMode())) {
            return;
        }
        DatabaseDialect dialect = resolveDialect();
        List<Map<String, Object>> issues = validateCreateTable(payload);
        List<String> errors = issues.stream()
                .filter(issue -> "ERROR".equals(issue.get("level")))
                .map(issue -> String.valueOf(issue.get("message")))
                .toList();
        if (!errors.isEmpty()) {
            throw new BusinessException("DDL_400", String.join("；", errors));
        }
        List<String> statements = buildCreateStatements(payload, dialect);
        try {
            for (String sql : statements) {
                jdbcTemplate.execute(sql);
            }
        } catch (RuntimeException e) {
            throw new BusinessException("DDL_500", "自动建表失败：" + rootMessage(e));
        }
    }

    public boolean tableExists(String tableName) {
        if (!StringUtils.hasText(tableName)) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<String> schemas = new ArrayList<>();
            addIfPresent(schemas, safeSchema(connection));
            addIfPresent(schemas, safeUserName(metaData));
            schemas.add(null);
            List<String> patterns = List.of(tableName, tableName.toUpperCase(Locale.ROOT), tableName.toLowerCase(Locale.ROOT));
            for (String schema : schemas) {
                for (String pattern : patterns) {
                    if (metadataTableExists(metaData, connection.getCatalog(), schema, pattern, tableName)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new BusinessException("DDL_500", "检查物理表是否存在失败：" + e.getMessage());
        }
    }

    private List<String> buildCreateStatements(ConfigWizardPayload payload, DatabaseDialect dialect) {
        ConfigWizardPayload.StorageConfig storageConfig = storageConfig(payload);
        String tableName = storageConfig.getTargetTable();
        List<String> columnLines = new ArrayList<>();
        if (payload.getResultTableColumns() != null) {
            for (ConfigWizardPayload.ResultTableColumn column : payload.getResultTableColumns()) {
                if (!StringUtils.hasText(column.getColumnName()) || !StringUtils.hasText(column.getDbType())) {
                    continue;
                }
                StringBuilder line = new StringBuilder();
                line.append("  ").append(column.getColumnName()).append(" ").append(columnType(column, dialect));
                if (Boolean.TRUE.equals(column.getRequired())) {
                    line.append(" NOT NULL");
                }
                if (dialect == DatabaseDialect.MYSQL && StringUtils.hasText(column.getColumnCnName())) {
                    line.append(" COMMENT '").append(escapeSqlText(column.getColumnCnName())).append("'");
                }
                columnLines.add(line.toString());
            }
        }
        List<String> statements = new ArrayList<>();
        StringBuilder createTable = new StringBuilder();
        createTable.append("CREATE TABLE ").append(tableName).append(" (\n");
        createTable.append(String.join(",\n", columnLines));
        createTable.append("\n)");
        if (dialect == DatabaseDialect.MYSQL && tableComment(storageConfig) != null) {
            createTable.append(" COMMENT='").append(escapeSqlText(tableComment(storageConfig))).append("'");
        }
        statements.add(createTable.toString());
        statements.addAll(uniqueIndexStatements(payload, tableName));
        if (dialect != DatabaseDialect.MYSQL) {
            String tableComment = tableComment(storageConfig);
            if (StringUtils.hasText(tableComment)) {
                statements.add("COMMENT ON TABLE " + tableName + " IS '" + escapeSqlText(tableComment) + "'");
            }
            if (payload.getResultTableColumns() != null) {
                for (ConfigWizardPayload.ResultTableColumn column : payload.getResultTableColumns()) {
                    if (StringUtils.hasText(column.getColumnName()) && StringUtils.hasText(column.getColumnCnName())) {
                        statements.add("COMMENT ON COLUMN " + tableName + "." + column.getColumnName() + " IS '" + escapeSqlText(column.getColumnCnName()) + "'");
                    }
                }
            }
        }
        return statements;
    }

    private List<String> uniqueIndexStatements(ConfigWizardPayload payload, String tableName) {
        List<String> statements = new ArrayList<>();
        if (payload.getUniqueConstraints() == null) {
            return statements;
        }
        for (ConfigWizardPayload.UniqueConstraint constraint : payload.getUniqueConstraints()) {
            if (!Boolean.TRUE.equals(constraint.getEnabled()) || !Boolean.TRUE.equals(constraint.getGenerateDbIndex())) {
                continue;
            }
            if (!StringUtils.hasText(constraint.getConstraintName()) || constraint.getUniqueColumns() == null || constraint.getUniqueColumns().isEmpty()) {
                continue;
            }
            statements.add("CREATE UNIQUE INDEX " + constraint.getConstraintName() + " ON " + tableName
                    + " (" + String.join(", ", constraint.getUniqueColumns()) + ")");
        }
        return statements;
    }

    private String columnType(ConfigWizardPayload.ResultTableColumn column, DatabaseDialect dialect) {
        String dbType = StringUtils.hasText(column.getDbType()) ? column.getDbType().toLowerCase(Locale.ROOT) : "varchar";
        return switch (dbType) {
            case "char" -> "CHAR(" + defaultLength(column) + ")";
            case "decimal" -> switch (dialect) {
                case MYSQL, DAMENG -> "DECIMAL(" + defaultPrecision(column) + "," + defaultScale(column) + ")";
                case ORACLE -> "NUMBER(" + defaultPrecision(column) + "," + defaultScale(column) + ")";
            };
            case "number" -> switch (dialect) {
                case MYSQL, DAMENG -> "DECIMAL(" + defaultPrecision(column) + "," + defaultScale(column) + ")";
                case ORACLE -> "NUMBER(" + defaultPrecision(column) + "," + defaultScale(column) + ")";
            };
            case "date" -> "DATE";
            case "datetime" -> dialect == DatabaseDialect.MYSQL ? "DATETIME" : "TIMESTAMP";
            default -> dialect == DatabaseDialect.ORACLE ? "VARCHAR2(" + defaultLength(column) + ")" : "VARCHAR(" + defaultLength(column) + ")";
        };
    }

    private int defaultLength(ConfigWizardPayload.ResultTableColumn column) {
        return column.getLength() == null || column.getLength() <= 0 ? 100 : column.getLength();
    }

    private int defaultPrecision(ConfigWizardPayload.ResultTableColumn column) {
        return column.getPrecision() == null || column.getPrecision() <= 0 ? 18 : column.getPrecision();
    }

    private int defaultScale(ConfigWizardPayload.ResultTableColumn column) {
        return column.getScale() == null || column.getScale() < 0 ? 2 : column.getScale();
    }

    private DatabaseDialect resolveDialect() {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            String normalized = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
            if (normalized.contains("mysql")) {
                return DatabaseDialect.MYSQL;
            }
            if (normalized.contains("oracle")) {
                return DatabaseDialect.ORACLE;
            }
            if (normalized.contains("dm") || normalized.contains("dameng") || normalized.contains("达梦")) {
                return DatabaseDialect.DAMENG;
            }
            throw new BusinessException("DDL_400", "暂不支持当前数据库自动建表：" + productName);
        } catch (SQLException e) {
            throw new BusinessException("DDL_500", "识别数据库类型失败：" + e.getMessage());
        }
    }

    private void validateIdentifier(String label, String identifier, DatabaseDialect dialect, List<Map<String, Object>> issues) {
        if (!StringUtils.hasText(identifier)) {
            return;
        }
        if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            issues.add(issue("ERROR", label + "只能包含小写字母、数字、下划线，并以小写字母开头：" + identifier));
            return;
        }
        int maxLength = dialect == DatabaseDialect.MYSQL ? 64 : 30;
        if (identifier.length() > maxLength) {
            issues.add(issue("ERROR", label + "超过 " + dialect.displayName + " 标识符长度限制 " + maxLength + "：" + identifier));
        }
    }

    private boolean metadataTableExists(DatabaseMetaData metaData, String catalog, String schema, String pattern, String targetTable) throws SQLException {
        try (ResultSet rs = metaData.getTables(catalog, schema, pattern, new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (targetTable.equalsIgnoreCase(tableName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String safeSchema(Connection connection) {
        try {
            return connection.getSchema();
        } catch (Exception e) {
            return null;
        }
    }

    private String safeUserName(DatabaseMetaData metaData) {
        try {
            return metaData.getUserName();
        } catch (Exception e) {
            return null;
        }
    }

    private void addIfPresent(List<String> values, String value) {
        if (StringUtils.hasText(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    private ConfigWizardPayload.StorageConfig storageConfig(ConfigWizardPayload payload) {
        return payload == null || payload.getStorageConfig() == null ? new ConfigWizardPayload.StorageConfig() : payload.getStorageConfig();
    }

    private boolean storageEnabled(ConfigWizardPayload payload) {
        return payload == null || payload.getStorageConfig() == null || !Boolean.FALSE.equals(payload.getStorageConfig().getStorageEnabled());
    }

    private String tableComment(ConfigWizardPayload.StorageConfig storageConfig) {
        return StringUtils.hasText(storageConfig.getTargetTableComment()) ? storageConfig.getTargetTableComment() : storageConfig.getTargetTableName();
    }

    private String escapeSqlText(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getMessage() : current.getMessage();
    }

    private Map<String, Object> issue(String level, String message) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("level", level);
        issue.put("message", message);
        return issue;
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private enum DatabaseDialect {
        MYSQL("MySQL"),
        ORACLE("Oracle"),
        DAMENG("达梦");

        private final String displayName;

        DatabaseDialect(String displayName) {
            this.displayName = displayName;
        }
    }
}
