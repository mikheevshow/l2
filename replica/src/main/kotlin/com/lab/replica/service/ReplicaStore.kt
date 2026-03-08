package com.lab.replica.service

import com.lab.replica.model.ValueVersion
import org.springframework.stereotype.Component
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe local register: one (value, version) pair.
 */
@Component
class ReplicaStore {

    private var current = ValueVersion(0, 0)
    private val lock = ReentrantLock()

    fun get(): ValueVersion = lock.withLock { current }

    /** Persist only if incoming version is >= current (last-write-wins). */
    fun applyIfNewer(value: Int, version: Int) = lock.withLock {
        if (version >= current.version) {
            current = ValueVersion(value, version)
        }
    }
}
