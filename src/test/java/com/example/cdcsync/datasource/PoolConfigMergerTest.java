package com.example.cdcsync.datasource;

import com.example.cdcsync.config.model.DataSourcePoolProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PoolConfigMergerTest {

    private final PoolConfigMerger merger = new PoolConfigMerger();

    @Test
    void shouldMergeDefaultsAndOverrides() {
        DataSourcePoolProperties defaults = new DataSourcePoolProperties();
        defaults.setMaximumPoolSize(10);
        defaults.setMinimumIdle(2);
        defaults.setConnectionTimeoutMs(30_000L);

        DataSourcePoolProperties overrides = new DataSourcePoolProperties();
        overrides.setMinimumIdle(5);
        overrides.setIdleTimeoutMs(60_000L);

        DataSourcePoolProperties merged = merger.merge(defaults, overrides);

        assertThat(merged.getMaximumPoolSize()).isEqualTo(10);
        assertThat(merged.getMinimumIdle()).isEqualTo(5);
        assertThat(merged.getConnectionTimeoutMs()).isEqualTo(30_000L);
        assertThat(merged.getIdleTimeoutMs()).isEqualTo(60_000L);
    }
}
