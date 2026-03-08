package com.lab.replica.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lab.replica.config.ReplicaConfig
import com.lab.replica.model.ValueVersion
import kotlinx.coroutines.*
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Coordinator: quorum write and quorum read via coroutines.
 */
@Service
class CoordinatorService(
    private val config: ReplicaConfig,
    private val store: ReplicaStore,
) {
    private val mapper = jacksonObjectMapper()
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Two-phase quorum write.
     * Returns true if W acknowledgements were collected.
     */
    suspend fun coordinateWrite(value: Int): Boolean {
        val peers = config.peers
        val w = config.w

        // Phase 1 — collect versions from all replicas, wait for W responses
        val versions = collectAtLeast(peers, w) { peer ->
            getInternalValue("$peer/internal/value")
        }
        if (versions.size < w) return false

        val newVersion = versions.maxOf { it.version } + 1

        // Phase 2 — push new value to all replicas, wait for W confirmations
        val acks = collectAtLeast(peers, w) { peer ->
            postInternalValue("$peer/internal/value", value, newVersion)
                .takeIf { it }   // null when false → not counted
        }
        return acks.size >= w
    }

    /**
     * Quorum read.
     * Returns the entry with the highest version from R responses, or null.
     */
    suspend fun coordinateRead(): ValueVersion? {
        val peers = config.peers
        val r = config.r

        val results = collectAtLeast(peers, r) { peer ->
            getInternalValue("$peer/internal/value")
        }
        if (results.size < r) return null

        return results.maxByOrNull { it.version }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Launch one coroutine per peer, collect the first [needed] non-null results.
     * All remaining coroutines are cancelled once [needed] results arrive.
     */
    private suspend fun <T : Any> collectAtLeast(
        peers: List<String>,
        needed: Int,
        block: suspend (String) -> T?,
    ): List<T> = coroutineScope {
        val channel = kotlinx.coroutines.channels.Channel<T>(capacity = peers.size)
        val jobs = peers.map { peer ->
            launch(Dispatchers.IO) {
                try {
                    block(peer)?.let { channel.send(it) }
                } catch (_: Exception) {}
            }
        }
        val results = mutableListOf<T>()
        repeat(needed) {
            withTimeoutOrNull(5_000) { channel.receive() }
                ?.let { results.add(it) }
                ?: return@repeat  // timeout — stop waiting
        }
        jobs.forEach { it.cancel() }
        channel.close()
        results
    }

    private fun getInternalValue(url: String): ValueVersion? = runCatching {
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() == 200) mapper.readValue<ValueVersion>(resp.body()) else null
    }.getOrNull()

    private fun postInternalValue(url: String, value: Int, version: Int): Boolean = runCatching {
        val body = mapper.writeValueAsString(ValueVersion(value, version))
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(5))
            .build()
        http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200
    }.getOrDefault(false)
}
