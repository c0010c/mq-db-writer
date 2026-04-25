package com.example.cdcsync.topic;

import com.example.cdcsync.config.CdcSyncProperties;
import com.example.cdcsync.config.model.TopicProperties;
import com.example.cdcsync.db.metadata.MetadataInspector;
import com.example.cdcsync.rocketmq.OffsetInspector;
import com.example.cdcsync.status.TopicStatus;
import com.example.cdcsync.status.TopicStatusRecord;
import com.example.cdcsync.status.TopicStatusService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TopicConfigValidator {

    private final CdcSyncProperties properties;
    private final Predicate<String> dataSourceAvailable;
    private final MetadataInspector metadataInspector;
    private final OffsetInspector offsetInspector;
    private final ConsumerGroupNameGenerator consumerGroupNameGenerator;
    private final TopicStatusService topicStatusService;

    public TopicConfigValidator(CdcSyncProperties properties,
                                Predicate<String> dataSourceAvailable,
                                MetadataInspector metadataInspector,
                                OffsetInspector offsetInspector,
                                ConsumerGroupNameGenerator consumerGroupNameGenerator,
                                TopicStatusService topicStatusService) {
        this.properties = properties;
        this.dataSourceAvailable = dataSourceAvailable;
        this.metadataInspector = metadataInspector;
        this.offsetInspector = offsetInspector;
        this.consumerGroupNameGenerator = consumerGroupNameGenerator;
        this.topicStatusService = topicStatusService;
    }

    public Map<String, TopicValidationResult> validateAndInitialize() {
        ensureNoDuplicateTopics();

        Map<String, TopicValidationResult> results = new LinkedHashMap<>();
        for (TopicProperties topic : properties.getTopics()) {
            TopicValidationResult result = validateSingleTopic(topic);
            results.put(topic.getTopic(), result);
            topicStatusService.upsert(new TopicStatusRecord(
                    topic.getTopic(),
                    topic.getDatasource(),
                    topic.getTargetTable(),
                    topic.getPrimaryKey(),
                    result.consumerGroup(),
                    result.status(),
                    result.errorMessage()
            ));
        }
        return results;
    }

    private void ensureNoDuplicateTopics() {
        Map<String, Long> topicCounts = properties.getTopics().stream()
                .collect(Collectors.groupingBy(TopicProperties::getTopic, Collectors.counting()));

        Set<String> duplicates = topicCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("duplicate topic config found: " + duplicates);
        }
    }

    private TopicValidationResult validateSingleTopic(TopicProperties topic) {
        String consumerGroup = consumerGroupNameGenerator.generate(properties.getAppName(), topic.getTopic());
        if (!Boolean.TRUE.equals(topic.getEnabled())) {
            return new TopicValidationResult(topic.getTopic(), consumerGroup, TopicStatus.STOPPED,
                    "topic disabled or enabled flag missing");
        }

        if (isBlank(topic.getDatasource()) || isBlank(topic.getTargetTable()) || isBlank(topic.getPrimaryKey())) {
            return new TopicValidationResult(topic.getTopic(), consumerGroup, TopicStatus.CONFIG_INVALID,
                    "datasource/targetTable/primaryKey is required for enabled topic");
        }

        if (!dataSourceAvailable.test(topic.getDatasource())) {
            return new TopicValidationResult(topic.getTopic(), consumerGroup, TopicStatus.CONFIG_INVALID,
                    "datasource not available: " + topic.getDatasource());
        }

        if (!metadataInspector.tableExists(topic.getDatasource(), topic.getTargetTable())) {
            return new TopicValidationResult(topic.getTopic(), consumerGroup, TopicStatus.CONFIG_INVALID,
                    "target table not found: " + topic.getTargetTable());
        }

        if (!metadataInspector.columnExists(topic.getDatasource(), topic.getTargetTable(), topic.getPrimaryKey())) {
            return new TopicValidationResult(topic.getTopic(), consumerGroup, TopicStatus.CONFIG_INVALID,
                    "primary key column not found: " + topic.getPrimaryKey());
        }

        if (!metadataInspector.isSingleColumnPrimaryOrUniqueKey(topic.getDatasource(), topic.getTargetTable(), topic.getPrimaryKey())) {
            return new TopicValidationResult(topic.getTopic(), consumerGroup, TopicStatus.CONFIG_INVALID,
                    "primary key must be single-column primary key or unique key: " + topic.getPrimaryKey());
        }

        if (!offsetInspector.hasOffset(topic.getTopic(), consumerGroup)) {
            return new TopicValidationResult(topic.getTopic(), consumerGroup, TopicStatus.OFFSET_NOT_FOUND,
                    "consumer group offset not found for topic");
        }

        return new TopicValidationResult(topic.getTopic(), consumerGroup, TopicStatus.RUNNING, "ready");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
