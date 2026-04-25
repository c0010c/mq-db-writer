package com.example.cdcsync.topic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public class ConsumerGroupNameGenerator {

    private static final int MAX_GROUP_LENGTH = 100;

    public String generate(String appName, String topic) {
        String normalizedTopic = normalizeTopic(topic);
        String baseGroup = appName + "-" + normalizedTopic + "-sync-group";
        if (baseGroup.length() <= MAX_GROUP_LENGTH) {
            return baseGroup;
        }

        String hash = sha1Hex(baseGroup).substring(0, 8);
        int allowedPrefixLength = MAX_GROUP_LENGTH - "-sync-group-".length() - hash.length();
        String trimmedAppAndTopic = (appName + "-" + normalizedTopic).substring(0, allowedPrefixLength);
        return trimmedAppAndTopic + "-sync-group-" + hash;
    }

    String normalizeTopic(String topic) {
        String raw = topic == null ? "" : topic.trim();
        String normalized = raw.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "topic" : normalized;
    }

    private String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 algorithm not available", ex);
        }
    }
}
