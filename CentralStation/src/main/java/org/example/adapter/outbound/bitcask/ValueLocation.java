package org.example.adapter.outbound.bitcask;

public record ValueLocation(String fileId, long valueSize, long valuePos, long timestamp) {}
