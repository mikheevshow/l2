package com.lab.replica.controller

import com.lab.replica.config.ReplicaConfig
import com.lab.replica.model.ValueVersion
import com.lab.replica.service.ReplicaStore
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import kotlin.random.Random

/**
 * Internal API — used by peer replicas for replication.
 */
@RestController
@RequestMapping("/internal")
class InternalController(
    private val store: ReplicaStore,
    private val config: ReplicaConfig,
) {

    /** GET /internal/value — return local value and version immediately. */
    @GetMapping("/value")
    fun getLocal(): ResponseEntity<ValueVersion> = ResponseEntity.ok(store.get())

    /**
     * POST /internal/value — sleep a random delay, then persist if version is newer.
     */
    @PostMapping("/value")
    fun applyReplication(@RequestBody incoming: ValueVersion): ResponseEntity<Void> {
        val delayMs = config.replicationDelayMaxMs
        if (delayMs > 0) Thread.sleep(Random.nextLong(delayMs.toLong() + 1))
        store.applyIfNewer(incoming.value, incoming.version)
        return ResponseEntity.ok().build()
    }
}
