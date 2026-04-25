package com.example.cdcsync.datasource;

import com.example.cdcsync.config.model.DataSourcePoolProperties;

public class PoolConfigMerger {

    public DataSourcePoolProperties merge(DataSourcePoolProperties defaults, DataSourcePoolProperties overrides) {
        DataSourcePoolProperties merged = new DataSourcePoolProperties();
        DataSourcePoolProperties safeDefaults = defaults == null ? new DataSourcePoolProperties() : defaults;
        DataSourcePoolProperties safeOverrides = overrides == null ? new DataSourcePoolProperties() : overrides;

        merged.setMaximumPoolSize(firstNonNull(safeOverrides.getMaximumPoolSize(), safeDefaults.getMaximumPoolSize()));
        merged.setMinimumIdle(firstNonNull(safeOverrides.getMinimumIdle(), safeDefaults.getMinimumIdle()));
        merged.setConnectionTimeoutMs(firstNonNull(safeOverrides.getConnectionTimeoutMs(), safeDefaults.getConnectionTimeoutMs()));
        merged.setIdleTimeoutMs(firstNonNull(safeOverrides.getIdleTimeoutMs(), safeDefaults.getIdleTimeoutMs()));
        merged.setMaxLifetimeMs(firstNonNull(safeOverrides.getMaxLifetimeMs(), safeDefaults.getMaxLifetimeMs()));
        return merged;
    }

    private <T> T firstNonNull(T left, T right) {
        return left != null ? left : right;
    }
}
