package com.example.cdcsync.startup;

import com.example.cdcsync.config.CdcSyncProperties;
import com.example.cdcsync.config.model.TopicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ConfigSummaryLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigSummaryLogger.class);

    private final CdcSyncProperties properties;

    public ConfigSummaryLogger(CdcSyncProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String datasourceNames = properties.getDatasources().keySet().stream().collect(Collectors.joining(", "));
        log.info("cdc-sync config summary: appName={}, nameServer={}, aclEnabled={}, accessKey={}, secretKey={}, datasources=[{}], topicCount={}",
                properties.getAppName(),
                properties.getRocketmq().getNameServer(),
                properties.getRocketmq().getAclEnabled(),
                mask(properties.getRocketmq().getAccessKey()),
                mask(properties.getRocketmq().getSecretKey()),
                datasourceNames,
                properties.getTopics().size());

        for (TopicProperties topic : properties.getTopics()) {
            log.info("topic config: topic={}, enabled={}, datasource={}, targetTable={}, primaryKey={}, batchSize={}, consumeThreadMin={}, consumeThreadMax={}, consumeFailureThresholdSeconds={}",
                    topic.getTopic(),
                    topic.getEnabled(),
                    topic.getDatasource(),
                    topic.getTargetTable(),
                    topic.getPrimaryKey(),
                    topic.getBatchSize(),
                    topic.getConsumeThreadMin(),
                    topic.getConsumeThreadMax(),
                    topic.getConsumeFailureThresholdSeconds());
        }
    }

    private String mask(String value) {
        return value == null || value.isBlank() ? "" : "******";
    }
}
