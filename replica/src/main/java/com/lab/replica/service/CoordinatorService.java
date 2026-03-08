package com.lab.replica.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.replica.config.ReplicaConfig;
import com.lab.replica.model.ValueVersion;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Coordinator logic for quorum reads and writes.
 */
@Service
public class CoordinatorService {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final ReplicaConfig config;
    private final ReplicaStore store;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CoordinatorService(ReplicaConfig config, ReplicaStore store) {
        this.config = config;
        this.store = store;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .executor(executor)
                .build();
    }

    /**
     * Coordinate a quorum write.
     *
     * @param value value to write
     * @return true if quorum acknowledged
     */
    public boolean coordinateWrite(int value) throws InterruptedException {
        List<String> peers = config.getPeers();
        int w = config.getW();

        // Phase 1: read current version from all replicas, wait for W responses
        List<Future<ValueVersion>> readFutures = new ArrayList<>();
        for (String peer : peers) {
            String url = peer + "/internal/value";
            readFutures.add(executor.submit(() -> fetchInternalValue(url)));
        }

        List<ValueVersion> readResults = collectAtLeast(readFutures, w);
        if (readResults.size() < w) {
            cancelAll(readFutures);
            return false;
        }

        int maxVersion = readResults.stream()
                .mapToInt(ValueVersion::getVersion)
                .max()
                .orElse(0);
        int newVersion = maxVersion + 1;

        // Phase 2: send new value+version to all replicas, wait for W confirmations
        List<Future<Boolean>> writeFutures = new ArrayList<>();
        for (String peer : peers) {
            String url = peer + "/internal/value";
            final int v = newVersion;
            writeFutures.add(executor.submit(() -> postInternalValue(url, value, v)));
        }

        List<Boolean> writeResults = collectAtLeast(writeFutures, w);
        long confirmed = writeResults.stream().filter(b -> b).count();

        cancelAll(writeFutures);
        return confirmed >= w;
    }

    /**
     * Coordinate a quorum read.
     *
     * @return the ValueVersion with the highest version, or null if quorum not reached
     */
    public ValueVersion coordinateRead() throws InterruptedException {
        List<String> peers = config.getPeers();
        int r = config.getR();

        List<Future<ValueVersion>> futures = new ArrayList<>();
        for (String peer : peers) {
            String url = peer + "/internal/value";
            futures.add(executor.submit(() -> fetchInternalValue(url)));
        }

        List<ValueVersion> results = collectAtLeast(futures, r);
        cancelAll(futures);

        if (results.size() < r) {
            return null;
        }

        return results.stream()
                .max((a, b) -> Integer.compare(a.getVersion(), b.getVersion()))
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private ValueVersion fetchInternalValue(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(HTTP_TIMEOUT)
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), ValueVersion.class);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean postInternalValue(String url, int value, int version) {
        try {
            String body = objectMapper.writeValueAsString(new ValueVersion(value, version));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .timeout(HTTP_TIMEOUT)
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception ignored) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Quorum collection: wait until we have `needed` non-null results or all done
    // -------------------------------------------------------------------------

    private <T> List<T> collectAtLeast(List<Future<T>> futures, int needed)
            throws InterruptedException {
        List<T> results = new ArrayList<>();
        // Use a completion service pattern via polling with small sleep
        long deadline = System.currentTimeMillis() + HTTP_TIMEOUT.toMillis();

        List<Future<T>> remaining = new ArrayList<>(futures);

        while (results.size() < needed && !remaining.isEmpty()
                && System.currentTimeMillis() < deadline) {
            List<Future<T>> done = new ArrayList<>();
            for (Future<T> f : remaining) {
                if (f.isDone()) {
                    done.add(f);
                    try {
                        T val = f.get();
                        if (val != null) {
                            results.add(val);
                        }
                    } catch (ExecutionException ignored) {
                    }
                }
            }
            remaining.removeAll(done);
            if (results.size() < needed && !remaining.isEmpty()) {
                Thread.sleep(10);
            }
        }
        return results;
    }

    private <T> void cancelAll(List<Future<T>> futures) {
        futures.forEach(f -> f.cancel(true));
    }
}
