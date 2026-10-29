package org.example.adapter.outbound.bitcask;

public record EntryLocation(String fileId, long offset, long entrySize) {}
