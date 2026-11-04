package org.example.adapter.outbound.bitcask.segment;

import org.example.adapter.outbound.bitcask.ValueLocation;

public record RecoveredEntry(
        String key,
        ValueLocation location
) {}
