package com.lab.replica.service;

import com.lab.replica.model.ValueVersion;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe local storage for a single register (value + version).
 */
@Component
public class ReplicaStore {

    private int value = 0;
    private int version = 0;
    private final ReentrantLock lock = new ReentrantLock();

    public ValueVersion get() {
        lock.lock();
        try {
            return new ValueVersion(value, version);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Apply update only if incoming version >= current version.
     */
    public void applyIfNewer(int newValue, int newVersion) {
        lock.lock();
        try {
            if (newVersion >= this.version) {
                this.value = newValue;
                this.version = newVersion;
            }
        } finally {
            lock.unlock();
        }
    }
}
