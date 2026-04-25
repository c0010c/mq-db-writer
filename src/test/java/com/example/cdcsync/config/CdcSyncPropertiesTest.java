package com.example.cdcsync.config;

import com.example.cdcsync.CdcSyncApplication;
import com.example.cdcsync.config.model.TopicProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CdcSyncApplication.class)
@TestPropertySource(properties = {
        "cdc-sync.app-name=test-app",
        "cdc-sync.default-batch-size=20",
        "cdc-sync.default-consume-thread-min=2",
        "cdc-sync.default-consume-thread-max=4",
        "cdc-sync.default-consume-failure-threshold-seconds=600",
        "cdc-sync.topics[0].topic=user_cdc_topic",
        "cdc-sync.topics[0].datasource=syncDbA",
        "cdc-sync.topics[0].target-table=user_info",
        "cdc-sync.topics[0].primary-key=id",
        "cdc-sync.topics[1].topic=device_cdc_topic",
        "cdc-sync.topics[1].enabled=true",
        "cdc-sync.topics[1].datasource=syncDbB",
        "cdc-sync.topics[1].target-table=device_info",
        "cdc-sync.topics[1].primary-key=device_id",
        "cdc-sync.topics[1].batch-size=50"
})
class CdcSyncPropertiesTest {

    @Autowired
    private CdcSyncProperties properties;

    @Test
    void shouldBindAndNormalizeTopicDefaults() {
        assertThat(properties.getAppName()).isEqualTo("test-app");
        assertThat(properties.getTopics()).hasSize(2);

        TopicProperties first = properties.getTopics().get(0);
        assertThat(first.getEnabled()).isFalse();
        assertThat(first.getBatchSize()).isEqualTo(20);
        assertThat(first.getConsumeThreadMin()).isEqualTo(2);
        assertThat(first.getConsumeThreadMax()).isEqualTo(4);
        assertThat(first.getConsumeFailureThresholdSeconds()).isEqualTo(600);

        TopicProperties second = properties.getTopics().get(1);
        assertThat(second.getEnabled()).isTrue();
        assertThat(second.getBatchSize()).isEqualTo(50);
        assertThat(second.getConsumeThreadMin()).isEqualTo(2);
        assertThat(second.getConsumeThreadMax()).isEqualTo(4);
        assertThat(second.getConsumeFailureThresholdSeconds()).isEqualTo(600);
    }
}
