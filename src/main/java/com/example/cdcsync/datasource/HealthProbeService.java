package com.example.cdcsync.datasource;

import javax.sql.DataSource;
import java.sql.Connection;

public class HealthProbeService {

    public boolean canConnect(DataSource dataSource) {
        try (Connection ignored = dataSource.getConnection()) {
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
