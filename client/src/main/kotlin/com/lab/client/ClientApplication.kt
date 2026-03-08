package com.lab.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class ValueVersion(val value: Int = 0, val version: Int = 0)

/**
 * Leaderless replication client.
 *
 * Env var:
 *   REPLICAS — comma-separated base URLs, e.g. http://replica1:8080,http://replica2:8080
 */
fun main() {
    val replicasEnv = System.getenv("REPLICAS")
        ?: error("REPLICAS environment variable is not set")

    val replicas = replicasEnv.split(",").map { it.trim() }.filter { it.isNotBlank() }
    check(replicas.isNotEmpty()) { "No replicas configured" }

    val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    val mapper = jacksonObjectMapper()
    val json = "application/json".toMediaType()

    var ok = 0L
    var inconsistent = 0L
    var errors = 0L

    // Hide cursor for clean in-place output
    print("\u001B[?25l")
    Runtime.getRuntime().addShutdownHook(Thread { print("\u001B[?25h\n") })

    while (true) {
        // 1. Write to a random replica
        val writtenValue = Random.nextInt(Int.MAX_VALUE)
        val writeReplica = replicas.random()

        val writeBody = mapper.writeValueAsString(mapOf("value" to writtenValue))
            .toRequestBody(json)
        val writeReq = Request.Builder()
            .url("$writeReplica/value")
            .post(writeBody)
            .build()

        val writeOk = try {
            http.newCall(writeReq).execute().use { it.code == 200 }
        } catch (_: Exception) { false }

        if (!writeOk) {
            errors++
            printStats(ok, inconsistent, errors)
            continue
        }

        // 2. Read from a (possibly different) random replica
        val readReplica = replicas.random()
        val readReq = Request.Builder().url("$readReplica/value").get().build()

        val readValue = try {
            http.newCall(readReq).execute().use { resp ->
                if (resp.code == 200) {
                    mapper.readValue<ValueVersion>(resp.body!!.string()).value
                } else null
            }
        } catch (_: Exception) { null }

        if (readValue == null) {
            errors++
            printStats(ok, inconsistent, errors)
            continue
        }

        // 3. Compare
        if (readValue == writtenValue) ok++ else inconsistent++
        printStats(ok, inconsistent, errors)
    }
}

private fun printStats(ok: Long, inconsistent: Long, errors: Long) {
    print("\rok: $ok | inconsistent: $inconsistent | errors: $errors   ")
    System.out.flush()
}
