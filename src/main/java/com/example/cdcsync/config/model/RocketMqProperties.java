package com.example.cdcsync.config.model;

public class RocketMqProperties {
    private String nameServer;
    private Boolean aclEnabled;
    private String accessKey;
    private String secretKey;

    public String getNameServer() {
        return nameServer;
    }

    public void setNameServer(String nameServer) {
        this.nameServer = nameServer;
    }

    public Boolean getAclEnabled() {
        return aclEnabled;
    }

    public void setAclEnabled(Boolean aclEnabled) {
        this.aclEnabled = aclEnabled;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
