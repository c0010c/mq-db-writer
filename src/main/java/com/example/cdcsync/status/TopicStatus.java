package com.example.cdcsync.status;

public enum TopicStatus {
    RUNNING,
    STOPPED,
    CONFIG_INVALID,
    OFFSET_NOT_FOUND,
    START_FAILED,
    RETRYING,
    CONSUME_FAILED
}
