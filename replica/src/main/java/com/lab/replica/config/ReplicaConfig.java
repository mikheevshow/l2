package com.lab.replica.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class ReplicaConfig {

    @Value("${replica.n:3}")
    private int n;

    @Value("${replica.w:2}")
    private int w;

    @Value("${replica.r:2}")
    private int r;

    // Comma-separated list of all replica base URLs (including self)
    @Value("${replica.peers:}")
    private String peersRaw;

    @Value("${replica.replication-delay-max-ms:1000}")
    private int replicationDelayMaxMs;

    public int getN() { return n; }
    public int getW() { return w; }
    public int getR() { return r; }
    public int getReplicationDelayMaxMs() { return replicationDelayMaxMs; }

    public List<String> getPeers() {
        if (peersRaw == null || peersRaw.isBlank()) return List.of();
        return Arrays.stream(peersRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
