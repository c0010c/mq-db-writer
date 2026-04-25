package com.example.cdcsync.db.metadata;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class MySqlMetadataInspector implements MetadataInspector {

    private final Function<String, Optional<JdbcTemplate>> jdbcTemplateProvider;

    public MySqlMetadataInspector(Function<String, Optional<JdbcTemplate>> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public boolean tableExists(String datasource, String tableName) {
        return withTemplate(datasource, template -> {
            Integer count = template.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    Integer.class,
                    tableName
            );
            return count != null && count > 0;
        });
    }

    @Override
    public boolean columnExists(String datasource, String tableName, String columnName) {
        return withTemplate(datasource, template -> {
            Integer count = template.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class,
                    tableName,
                    columnName
            );
            return count != null && count > 0;
        });
    }

    @Override
    public boolean isSingleColumnPrimaryOrUniqueKey(String datasource, String tableName, String columnName) {
        return withTemplate(datasource, template -> {
            List<KeyColumn> keyColumns = template.query(
                    "SELECT s.INDEX_NAME, s.NON_UNIQUE, s.SEQ_IN_INDEX, s.COLUMN_NAME " +
                            "FROM INFORMATION_SCHEMA.STATISTICS s " +
                            "WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = ?",
                    (rs, rowNum) -> new KeyColumn(
                            rs.getString("INDEX_NAME"),
                            rs.getInt("NON_UNIQUE"),
                            rs.getInt("SEQ_IN_INDEX"),
                            rs.getString("COLUMN_NAME")
                    ),
                    tableName
            );

            return keyColumns.stream()
                    .filter(key -> "PRIMARY".equalsIgnoreCase(key.indexName()) || key.nonUnique() == 0)
                    .filter(key -> key.seqInIndex() == 1)
                    .anyMatch(key -> columnName.equalsIgnoreCase(key.columnName())
                            && keyColumns.stream()
                            .noneMatch(other -> other.indexName().equals(key.indexName()) && other.seqInIndex() > 1));
        });
    }

    private boolean withTemplate(String datasource, Function<JdbcTemplate, Boolean> action) {
        return jdbcTemplateProvider.apply(datasource)
                .map(action)
                .orElse(false);
    }

    private record KeyColumn(String indexName, int nonUnique, int seqInIndex, String columnName) {
    }
}
