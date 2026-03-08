package com.lab.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Leaderless replication client.
 *
 * Env vars:
 *   REPLICAS  – comma-separated list of replica base URLs
 *               e.g. http://replica1:8080,http://replica2:8080,http://replica3:8080
 */
public class ClientApplication {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public static void main(String[] args) throws Exception {
        String replicasEnv = System.getenv("REPLICAS");
        if (replicasEnv == null || replicasEnv.isBlank()) {
            System.err.println("REPLICAS environment variable is not set.");
            System.exit(1);
        }

        List<String> replicas = Arrays.stream(replicasEnv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        if (replicas.isEmpty()) {
            System.err.println("No replicas configured.");
            System.exit(1);
        }

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        ObjectMapper mapper = new ObjectMapper();

        long ok = 0;
        long inconsistent = 0;
        long errors = 0;

        // Hide cursor and prepare for in-place output
        System.out.print("\033[?25l");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.print("\033[?25h\n")));

        while (true) {
            // Step 1: pick a random replica, write a random value
            int writtenValue = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
            String writeReplica = randomPick(replicas);

            boolean writeOk = doWrite(http, mapper, writeReplica, writtenValue);
            if (!writeOk) {
                errors++;
                printStats(ok, inconsistent, errors);
                continue;
            }

            // Step 2: pick a random replica (possibly different), read back
            String readReplica = randomPick(replicas);
            Integer readValue = doRead(http, mapper, readReplica);
            if (readValue == null) {
                errors++;
                printStats(ok, inconsistent, errors);
                continue;
            }

            // Step 3: compare
            if (readValue == writtenValue) {
                ok++;
            } else {
                inconsistent++;
            }
            printStats(ok, inconsistent, errors);
        }
    }

    // -------------------------------------------------------------------------

    private static boolean doWrite(HttpClient http, ObjectMapper mapper,
                                   String replicaUrl, int value) {
        try {
            String body = mapper.writeValueAsString(new WriteBody(value));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(replicaUrl + "/value"))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static Integer doRead(HttpClient http, ObjectMapper mapper, String replicaUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(replicaUrl + "/value"))
                    .GET()
                    .timeout(TIMEOUT)
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode node = mapper.readTree(resp.body());
                return node.get("value").asInt();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String randomPick(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private static void printStats(long ok, long inconsistent, long errors) {
        // \r moves to start of line to overwrite previous output
        System.out.printf("\rok: %d | inconsistent: %d | errors: %d   ", ok, inconsistent, errors);
        System.out.flush();
    }

    // Simple POJO for JSON serialisation
    static class WriteBody {
        public int value;
        WriteBody(int v) { this.value = v; }
    }
}
