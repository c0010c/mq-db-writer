package com.example.cdcsync.config.model;

public class TopicProperties {
    private String topic;
    private Boolean enabled;
    private String datasource;
    private String targetTable;
    private String primaryKey;
    private Integer batchSize;
    private Integer consumeThreadMin;
    private Integer consumeThreadMax;
    private Integer consumeFailureThresholdSeconds;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDatasource() {
        return datasource;
    }

    public void setDatasource(String datasource) {
        this.datasource = datasource;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }

    public String getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(String primaryKey) {
        this.primaryKey = primaryKey;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

    public Integer getConsumeThreadMin() {
        return consumeThreadMin;
    }

    public void setConsumeThreadMin(Integer consumeThreadMin) {
        this.consumeThreadMin = consumeThreadMin;
    }

    public Integer getConsumeThreadMax() {
        return consumeThreadMax;
    }

    public void setConsumeThreadMax(Integer consumeThreadMax) {
        this.consumeThreadMax = consumeThreadMax;
    }

    public Integer getConsumeFailureThresholdSeconds() {
        return consumeFailureThresholdSeconds;
    }

    public void setConsumeFailureThresholdSeconds(Integer consumeFailureThresholdSeconds) {
        this.consumeFailureThresholdSeconds = consumeFailureThresholdSeconds;
    }
}
