package com.lab.replica.model;

public class ValueVersion {
    private int value;
    private int version;

    public ValueVersion() {}

    public ValueVersion(int value, int version) {
        this.value = value;
        this.version = version;
    }

    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
