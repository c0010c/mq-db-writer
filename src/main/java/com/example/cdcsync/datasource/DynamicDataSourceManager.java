package com.example.cdcsync.datasource;

import com.example.cdcsync.config.model.DataSourcePoolProperties;
import com.example.cdcsync.config.model.DataSourceProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicDataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceManager.class);

    private final DataSourceFactory dataSourceFactory;
    private final DataSourceRegistry dataSourceRegistry;
    private final PoolConfigMerger poolConfigMerger;
    private final HealthProbeService healthProbeService;

    private final Map<String, DataSourceSnapshot> appliedDataSources = new ConcurrentHashMap<>();
    private final Map<String, String> failedDataSources = new ConcurrentHashMap<>();

    public DynamicDataSourceManager(DataSourceFactory dataSourceFactory,
                                    DataSourceRegistry dataSourceRegistry,
                                    PoolConfigMerger poolConfigMerger,
                                    HealthProbeService healthProbeService) {
        this.dataSourceFactory = dataSourceFactory;
        this.dataSourceRegistry = dataSourceRegistry;
        this.poolConfigMerger = poolConfigMerger;
        this.healthProbeService = healthProbeService;
    }

    public void initialize(Map<String, DataSourceProperties> dataSources, DataSourcePoolProperties defaultPoolProperties) {
        for (Map.Entry<String, DataSourceProperties> entry : dataSources.entrySet()) {
            tryCreateAndRegister(entry.getKey(), entry.getValue(), defaultPoolProperties);
        }
    }

    public void onConfigChanged(Map<String, DataSourceProperties> newDataSources,
                                DataSourcePoolProperties defaultPoolProperties) {
        for (Map.Entry<String, DataSourceProperties> entry : newDataSources.entrySet()) {
            String name = entry.getKey();
            DataSourceProperties properties = entry.getValue();
            DataSourceSnapshot newSnapshot = snapshot(properties, defaultPoolProperties);

            if (!appliedDataSources.containsKey(name)) {
                tryCreateAndRegister(name, properties, defaultPoolProperties);
                continue;
            }

            DataSourceSnapshot oldSnapshot = appliedDataSources.get(name);
            if (!oldSnapshot.equals(newSnapshot)) {
                log.warn("datasource {} config changed but hot replacement is not supported, restart required", name);
            }
        }
    }

    public Optional<JdbcTemplate> getJdbcTemplate(String name) {
        return dataSourceRegistry.getJdbcTemplate(name);
    }

    public boolean isDataSourceAvailable(String name) {
        return dataSourceRegistry.contains(name);
    }

    public Map<String, String> getFailedDataSources() {
        return Collections.unmodifiableMap(failedDataSources);
    }

    public boolean isStatusDataSourceHealthy(String statusDatasource) {
        return dataSourceRegistry.getDataSource(statusDatasource)
                .map(healthProbeService::canConnect)
                .orElse(false);
    }

    public void shutdown() {
        dataSourceRegistry.closeAll();
    }

    private void tryCreateAndRegister(String name,
                                      DataSourceProperties properties,
                                      DataSourcePoolProperties defaultPoolProperties) {
        try {
            HikariDataSource dataSource = dataSourceFactory.create(name, properties, defaultPoolProperties);
            dataSourceRegistry.register(name, dataSource);
            appliedDataSources.put(name, snapshot(properties, defaultPoolProperties));
            failedDataSources.remove(name);
            log.info("datasource {} initialized", name);
        } catch (Exception ex) {
            failedDataSources.put(name, ex.getMessage());
            log.error("datasource {} initialization failed: {}", name, ex.getMessage(), ex);
        }
    }

    private DataSourceSnapshot snapshot(DataSourceProperties properties, DataSourcePoolProperties defaults) {
        DataSourcePoolProperties mergedPool = poolConfigMerger.merge(defaults, properties.getPool());
        return new DataSourceSnapshot(
                properties.getUrl(),
                properties.getUsername(),
                properties.getPassword(),
                properties.getDriverClassName(),
                mergedPool.getMaximumPoolSize(),
                mergedPool.getMinimumIdle(),
                mergedPool.getConnectionTimeoutMs(),
                mergedPool.getIdleTimeoutMs(),
                mergedPool.getMaxLifetimeMs()
        );
    }

    private record DataSourceSnapshot(String url,
                                      String username,
                                      String password,
                                      String driverClassName,
                                      Integer maximumPoolSize,
                                      Integer minimumIdle,
                                      Long connectionTimeoutMs,
                                      Long idleTimeoutMs,
                                      Long maxLifetimeMs) {
    }
}
