package com.example.cdcsync.datasource;

import com.example.cdcsync.config.model.DataSourcePoolProperties;
import com.example.cdcsync.config.model.DataSourceProperties;
import com.zaxxer.hikari.HikariDataSource;

public class DataSourceFactory {

    private final PoolConfigMerger poolConfigMerger;

    public DataSourceFactory(PoolConfigMerger poolConfigMerger) {
        this.poolConfigMerger = poolConfigMerger;
    }

    public HikariDataSource create(String name,
                                   DataSourceProperties properties,
                                   DataSourcePoolProperties defaultPoolProperties) {
        DataSourcePoolProperties mergedPool = poolConfigMerger.merge(defaultPoolProperties, properties.getPool());

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("cdc-sync-" + name);
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName(properties.getDriverClassName());

        if (mergedPool.getMaximumPoolSize() != null) {
            dataSource.setMaximumPoolSize(mergedPool.getMaximumPoolSize());
        }
        if (mergedPool.getMinimumIdle() != null) {
            dataSource.setMinimumIdle(mergedPool.getMinimumIdle());
        }
        if (mergedPool.getConnectionTimeoutMs() != null) {
            dataSource.setConnectionTimeout(mergedPool.getConnectionTimeoutMs());
        }
        if (mergedPool.getIdleTimeoutMs() != null) {
            dataSource.setIdleTimeout(mergedPool.getIdleTimeoutMs());
        }
        if (mergedPool.getMaxLifetimeMs() != null) {
            dataSource.setMaxLifetime(mergedPool.getMaxLifetimeMs());
        }
        return dataSource;
    }
}
