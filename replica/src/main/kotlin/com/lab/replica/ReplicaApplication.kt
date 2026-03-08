package com.lab.replica

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ReplicaApplication

fun main(args: Array<String>) {
    runApplication<ReplicaApplication>(*args)
}
