package com.example.cdcsync.topic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumerGroupNameGeneratorTest {

    private final ConsumerGroupNameGenerator generator = new ConsumerGroupNameGenerator();

    @Test
    void shouldGenerateDeterministicGroupWithNormalizedTopic() {
        String group = generator.generate("debezium-cdc-sync", "User.CDC-Topic");

        assertThat(group).isEqualTo("debezium-cdc-sync-user_cdc_topic-sync-group");
    }

    @Test
    void shouldApplyLengthLimitWithDeterministicHashSuffix() {
        String appName = "a".repeat(70);
        String topic = "very-long-topic-name-with-many-segments-and-characters-1234567890";

        String first = generator.generate(appName, topic);
        String second = generator.generate(appName, topic);

        assertThat(first).hasSizeLessThanOrEqualTo(100);
        assertThat(first).isEqualTo(second);
        assertThat(first).contains("-sync-group-");
    }
}
