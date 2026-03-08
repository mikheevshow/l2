package com.lab.replica.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class ReplicaConfig {

    @Value("\${replica.n:3}")
    var n: Int = 3

    @Value("\${replica.w:2}")
    var w: Int = 2

    @Value("\${replica.r:2}")
    var r: Int = 2

    @Value("\${replica.replication-delay-max-ms:1000}")
    var replicationDelayMaxMs: Int = 1000

    @Value("\${replica.peers:}")
    private var peersRaw: String = ""

    val peers: List<String>
        get() = peersRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
}
