package com.lab.replica.model

data class ValueVersion(val value: Int = 0, val version: Int = 0)

data class WriteRequest(val value: Int = 0)
