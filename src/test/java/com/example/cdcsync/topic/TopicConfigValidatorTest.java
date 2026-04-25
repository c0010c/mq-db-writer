package com.example.cdcsync.topic;

import com.example.cdcsync.config.CdcSyncProperties;
import com.example.cdcsync.config.model.TopicProperties;
import com.example.cdcsync.db.metadata.MetadataInspector;
import com.example.cdcsync.rocketmq.OffsetInspector;
import com.example.cdcsync.status.TopicStatus;
import com.example.cdcsync.status.TopicStatusService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopicConfigValidatorTest {

    @Test
    void shouldFailFastWhenDuplicateTopicsExist() {
        CdcSyncProperties properties = baseProperties(List.of(topic("t1", true), topic("t1", false)));

        TopicConfigValidator validator = new TopicConfigValidator(
                properties,
                ds -> true,
                new AlwaysPassMetadataInspector(),
                (topic, group) -> true,
                new ConsumerGroupNameGenerator(),
                new TopicStatusService()
        );

        assertThatThrownBy(validator::validateAndInitialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate topic config");
    }

    @Test
    void shouldResolveStoppedRunningAndOffsetNotFoundStatuses() {
        CdcSyncProperties properties = baseProperties(List.of(
                topic("disabled_topic", false),
                enabledTopic("running_topic"),
                enabledTopic("offset_missing_topic")
        ));

        OffsetInspector offsetInspector = (topic, group) -> !"offset_missing_topic".equals(topic);
        TopicStatusService statusService = new TopicStatusService();
        TopicConfigValidator validator = new TopicConfigValidator(
                properties,
                ds -> true,
                new AlwaysPassMetadataInspector(),
                offsetInspector,
                new ConsumerGroupNameGenerator(),
                statusService
        );

        Map<String, TopicValidationResult> result = validator.validateAndInitialize();

        assertThat(result.get("disabled_topic").status()).isEqualTo(TopicStatus.STOPPED);
        assertThat(result.get("running_topic").status()).isEqualTo(TopicStatus.RUNNING);
        assertThat(result.get("offset_missing_topic").status()).isEqualTo(TopicStatus.OFFSET_NOT_FOUND);

        assertThat(statusService.get("running_topic")).isPresent();
        assertThat(statusService.get("running_topic").map(record -> record.status()))
                .contains(TopicStatus.RUNNING);
    }

    @Test
    void shouldMarkConfigInvalidWhenMetadataValidationFails() {
        CdcSyncProperties properties = baseProperties(List.of(enabledTopic("bad_table")));

        MetadataInspector metadataInspector = new MetadataInspector() {
            @Override
            public boolean tableExists(String datasource, String tableName) {
                return false;
            }

            @Override
            public boolean columnExists(String datasource, String tableName, String columnName) {
                return true;
            }

            @Override
            public boolean isSingleColumnPrimaryOrUniqueKey(String datasource, String tableName, String columnName) {
                return true;
            }
        };

        TopicStatusService statusService = new TopicStatusService();
        TopicConfigValidator validator = new TopicConfigValidator(
                properties,
                ds -> true,
                metadataInspector,
                (topic, group) -> true,
                new ConsumerGroupNameGenerator(),
                statusService
        );

        validator.validateAndInitialize();

        assertThat(statusService.get("bad_table").map(record -> record.status()))
                .contains(TopicStatus.CONFIG_INVALID);
        assertThat(statusService.get("bad_table").flatMap(record -> Optional.ofNullable(record.lastErrorMessage())))
                .hasValueSatisfying(error -> assertThat(error).contains("target table not found"));
    }

    private CdcSyncProperties baseProperties(List<TopicProperties> topics) {
        CdcSyncProperties properties = new CdcSyncProperties();
        properties.setAppName("debezium-cdc-sync");
        properties.setTopics(topics);
        return properties;
    }

    private TopicProperties topic(String name, boolean enabled) {
        TopicProperties topic = new TopicProperties();
        topic.setTopic(name);
        topic.setEnabled(enabled);
        topic.setDatasource("syncDbA");
        topic.setTargetTable("user_info");
        topic.setPrimaryKey("id");
        return topic;
    }

    private TopicProperties enabledTopic(String name) {
        return topic(name, true);
    }

    private static class AlwaysPassMetadataInspector implements MetadataInspector {
        @Override
        public boolean tableExists(String datasource, String tableName) {
            return true;
        }

        @Override
        public boolean columnExists(String datasource, String tableName, String columnName) {
            return true;
        }

        @Override
        public boolean isSingleColumnPrimaryOrUniqueKey(String datasource, String tableName, String columnName) {
            return true;
        }
    }
}
