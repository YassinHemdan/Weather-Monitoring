package org.example.domain.weather;

public enum Status {
    LOW, MEDIUM, HIGH;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
