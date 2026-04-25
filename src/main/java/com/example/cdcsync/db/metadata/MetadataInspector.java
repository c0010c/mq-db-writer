package com.example.cdcsync.db.metadata;

public interface MetadataInspector {

    boolean tableExists(String datasource, String tableName);

    boolean columnExists(String datasource, String tableName, String columnName);

    boolean isSingleColumnPrimaryOrUniqueKey(String datasource, String tableName, String columnName);
}
