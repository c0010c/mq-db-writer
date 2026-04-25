package com.example.cdcsync.config;

import com.example.cdcsync.config.model.DataSourcePoolProperties;
import com.example.cdcsync.config.model.DataSourceProperties;
import com.example.cdcsync.config.model.RocketMqProperties;
import com.example.cdcsync.config.model.TopicProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "cdc-sync")
public class CdcSyncProperties {

    private String appName;
    private RocketMqProperties rocketmq = new RocketMqProperties();
    private String statusDatasource;
    private DataSourcePoolProperties datasourceDefaults = new DataSourcePoolProperties();
    private Map<String, DataSourceProperties> datasources = new LinkedHashMap<>();

    private Integer defaultBatchSize = 10;
    private Integer defaultConsumeThreadMin = 1;
    private Integer defaultConsumeThreadMax = 1;
    private Integer defaultConsumeFailureThresholdSeconds = 300;

    private List<TopicProperties> topics = new ArrayList<>();

    @PostConstruct
    public void normalize() {
        for (TopicProperties topic : topics) {
            if (topic.getEnabled() == null) {
                topic.setEnabled(false);
            }
            if (topic.getBatchSize() == null) {
                topic.setBatchSize(defaultBatchSize);
            }
            if (topic.getConsumeThreadMin() == null) {
                topic.setConsumeThreadMin(defaultConsumeThreadMin);
            }
            if (topic.getConsumeThreadMax() == null) {
                topic.setConsumeThreadMax(defaultConsumeThreadMax);
            }
            if (topic.getConsumeFailureThresholdSeconds() == null) {
                topic.setConsumeFailureThresholdSeconds(defaultConsumeFailureThresholdSeconds);
            }
        }
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public RocketMqProperties getRocketmq() {
        return rocketmq;
    }

    public void setRocketmq(RocketMqProperties rocketmq) {
        this.rocketmq = rocketmq;
    }

    public String getStatusDatasource() {
        return statusDatasource;
    }

    public void setStatusDatasource(String statusDatasource) {
        this.statusDatasource = statusDatasource;
    }

    public DataSourcePoolProperties getDatasourceDefaults() {
        return datasourceDefaults;
    }

    public void setDatasourceDefaults(DataSourcePoolProperties datasourceDefaults) {
        this.datasourceDefaults = datasourceDefaults;
    }

    public Map<String, DataSourceProperties> getDatasources() {
        return datasources;
    }

    public void setDatasources(Map<String, DataSourceProperties> datasources) {
        this.datasources = datasources;
    }

    public Integer getDefaultBatchSize() {
        return defaultBatchSize;
    }

    public void setDefaultBatchSize(Integer defaultBatchSize) {
        this.defaultBatchSize = defaultBatchSize;
    }

    public Integer getDefaultConsumeThreadMin() {
        return defaultConsumeThreadMin;
    }

    public void setDefaultConsumeThreadMin(Integer defaultConsumeThreadMin) {
        this.defaultConsumeThreadMin = defaultConsumeThreadMin;
    }

    public Integer getDefaultConsumeThreadMax() {
        return defaultConsumeThreadMax;
    }

    public void setDefaultConsumeThreadMax(Integer defaultConsumeThreadMax) {
        this.defaultConsumeThreadMax = defaultConsumeThreadMax;
    }

    public Integer getDefaultConsumeFailureThresholdSeconds() {
        return defaultConsumeFailureThresholdSeconds;
    }

    public void setDefaultConsumeFailureThresholdSeconds(Integer defaultConsumeFailureThresholdSeconds) {
        this.defaultConsumeFailureThresholdSeconds = defaultConsumeFailureThresholdSeconds;
    }

    public List<TopicProperties> getTopics() {
        return topics;
    }

    public void setTopics(List<TopicProperties> topics) {
        this.topics = topics;
    }
}
