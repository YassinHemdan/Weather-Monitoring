package org.example.adapter.outbound.bitcask.segment;

public record SegmentInfo(String id, long size, SegmentState state){}