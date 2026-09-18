package org.example.model;

public enum Status {
    LOW, MEDIUM, HIGH;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
