package com.lab.replica.controller;

import com.lab.replica.config.ReplicaConfig;
import com.lab.replica.model.ValueVersion;
import com.lab.replica.service.ReplicaStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Internal API used by peer replicas for replication.
 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    private final ReplicaStore store;
    private final ReplicaConfig config;

    public InternalController(ReplicaStore store, ReplicaConfig config) {
        this.store = store;
        this.config = config;
    }

    /**
     * GET /internal/value  — return local value and version (no delay).
     */
    @GetMapping("/value")
    public ResponseEntity<ValueVersion> getLocal() {
        return ResponseEntity.ok(store.get());
    }

    /**
     * POST /internal/value  — apply random delay then update local register.
     */
    @PostMapping("/value")
    public ResponseEntity<Void> applyReplication(@RequestBody ValueVersion incoming) {
        int delayMax = config.getReplicationDelayMaxMs();
        if (delayMax > 0) {
            int delay = ThreadLocalRandom.current().nextInt(delayMax + 1);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        store.applyIfNewer(incoming.getValue(), incoming.getVersion());
        return ResponseEntity.ok().build();
    }
}
