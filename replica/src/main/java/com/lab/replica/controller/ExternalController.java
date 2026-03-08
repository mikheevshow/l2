package com.lab.replica.controller;

import com.lab.replica.model.ValueVersion;
import com.lab.replica.model.WriteRequest;
import com.lab.replica.service.CoordinatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * External API used by the client.
 */
@RestController
public class ExternalController {

    private final CoordinatorService coordinator;

    public ExternalController(CoordinatorService coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * POST /value  — coordinate a quorum write.
     */
    @PostMapping("/value")
    public ResponseEntity<Void> write(@RequestBody WriteRequest req) {
        try {
            boolean ok = coordinator.coordinateWrite(req.getValue());
            return ok
                    ? ResponseEntity.ok().build()
                    : ResponseEntity.status(503).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(503).build();
        }
    }

    /**
     * GET /value  — coordinate a quorum read and return the freshest value.
     */
    @GetMapping("/value")
    public ResponseEntity<ValueVersion> read() {
        try {
            ValueVersion result = coordinator.coordinateRead();
            return result != null
                    ? ResponseEntity.ok(result)
                    : ResponseEntity.status(503).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(503).build();
        }
    }
}
