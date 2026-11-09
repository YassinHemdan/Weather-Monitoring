package org.example.adapter.outbound.bitcask.segment;

public record AppendResult(String segmentId, long startOffset, long length){}
