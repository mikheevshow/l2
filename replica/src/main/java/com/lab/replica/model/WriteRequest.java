package com.lab.replica.model;

public class WriteRequest {
    private int value;

    public WriteRequest() {}

    public WriteRequest(int value) {
        this.value = value;
    }

    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
}
