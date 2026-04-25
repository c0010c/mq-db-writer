package com.example.cdcsync.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DataSourceRegistry {

    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, JdbcTemplate> jdbcTemplates = new ConcurrentHashMap<>();

    public void register(String name, HikariDataSource dataSource) {
        dataSources.put(name, dataSource);
        jdbcTemplates.put(name, new JdbcTemplate(dataSource));
    }

    public Optional<JdbcTemplate> getJdbcTemplate(String name) {
        return Optional.ofNullable(jdbcTemplates.get(name));
    }

    public Optional<DataSource> getDataSource(String name) {
        return Optional.ofNullable(dataSources.get(name)).map(ds -> (DataSource) ds);
    }

    public boolean contains(String name) {
        return dataSources.containsKey(name);
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(dataSources.keySet());
    }

    public void closeAll() {
        dataSources.values().forEach(HikariDataSource::close);
        dataSources.clear();
        jdbcTemplates.clear();
    }
}
