package com.example.cdcsync.datasource;

import com.example.cdcsync.config.model.DataSourcePoolProperties;
import com.example.cdcsync.config.model.DataSourceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicDataSourceManagerTest {

    private final DynamicDataSourceManager manager = new DynamicDataSourceManager(
            new DataSourceFactory(new PoolConfigMerger()),
            new DataSourceRegistry(),
            new PoolConfigMerger(),
            new HealthProbeService()
    );

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    @Test
    void shouldGetJdbcTemplateByName() {
        Map<String, DataSourceProperties> sources = new LinkedHashMap<>();
        sources.put("syncDbA", h2Properties("syncDbA"));

        manager.initialize(sources, defaults());

        JdbcTemplate jdbcTemplate = manager.getJdbcTemplate("syncDbA").orElseThrow();
        Integer value = jdbcTemplate.queryForObject("select 1", Integer.class);

        assertThat(value).isEqualTo(1);
    }

    @Test
    void shouldCreateOnlyNewDatasourceOnConfigChange() {
        Map<String, DataSourceProperties> sources = new LinkedHashMap<>();
        sources.put("syncDbA", h2Properties("syncDbA"));
        manager.initialize(sources, defaults());

        Map<String, DataSourceProperties> changed = new LinkedHashMap<>();
        DataSourceProperties modified = h2Properties("syncDbA");
        modified.setUsername("new-user");
        changed.put("syncDbA", modified);
        changed.put("syncDbB", h2Properties("syncDbB"));

        manager.onConfigChanged(changed, defaults());

        assertThat(manager.isDataSourceAvailable("syncDbA")).isTrue();
        assertThat(manager.isDataSourceAvailable("syncDbB")).isTrue();
        assertThat(manager.getJdbcTemplate("syncDbB")).isPresent();
    }

    @Test
    void shouldMarkDatasourceFailureWithoutThrowing() {
        Map<String, DataSourceProperties> sources = new LinkedHashMap<>();
        DataSourceProperties broken = new DataSourceProperties();
        broken.setUrl("jdbc:unknown://localhost:3306/test");
        broken.setUsername("u");
        broken.setPassword("p");
        broken.setDriverClassName("org.unknown.Driver");
        sources.put("broken", broken);

        manager.initialize(sources, defaults());

        assertThat(manager.isDataSourceAvailable("broken")).isFalse();
        assertThat(manager.getFailedDataSources()).containsKey("broken");
    }

    @Test
    void shouldProbeStatusDatasourceHealth() {
        Map<String, DataSourceProperties> sources = new LinkedHashMap<>();
        sources.put("syncAdminDb", h2Properties("syncAdminDb"));

        manager.initialize(sources, defaults());

        assertThat(manager.isStatusDataSourceHealthy("syncAdminDb")).isTrue();
        assertThat(manager.isStatusDataSourceHealthy("missing")).isFalse();
    }

    private DataSourcePoolProperties defaults() {
        DataSourcePoolProperties defaults = new DataSourcePoolProperties();
        defaults.setMaximumPoolSize(3);
        defaults.setMinimumIdle(1);
        defaults.setConnectionTimeoutMs(30_000L);
        return defaults;
    }

    private DataSourceProperties h2Properties(String name) {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setDriverClassName("org.h2.Driver");
        return properties;
    }
}
