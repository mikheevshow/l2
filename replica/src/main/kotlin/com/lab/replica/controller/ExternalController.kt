package com.lab.replica.controller

import com.lab.replica.model.ValueVersion
import com.lab.replica.model.WriteRequest
import com.lab.replica.service.CoordinatorService
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * External API — used by the client.
 */
@RestController
class ExternalController(private val coordinator: CoordinatorService) {

    /** POST /value — coordinate a quorum write. */
    @PostMapping("/value")
    fun write(@RequestBody req: WriteRequest): ResponseEntity<Void> {
        val ok = runBlocking { coordinator.coordinateWrite(req.value) }
        return if (ok) ResponseEntity.ok().build() else ResponseEntity.status(503).build()
    }

    /** GET /value — coordinate a quorum read and return the freshest entry. */
    @GetMapping("/value")
    fun read(): ResponseEntity<ValueVersion> {
        val result = runBlocking { coordinator.coordinateRead() }
        return if (result != null) ResponseEntity.ok(result) else ResponseEntity.status(503).build()
    }
}
