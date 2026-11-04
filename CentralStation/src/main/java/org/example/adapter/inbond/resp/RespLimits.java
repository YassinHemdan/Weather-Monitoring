package org.example.adapter.inbond.resp;

public record RespLimits (
        int maxLineBytes,
        int maxArrElements,
        int maxBulkBytes,
        int maxArrDepth,
        long maxValueBytes
){
    public static final RespLimits DEFAULT  = new RespLimits(
            1024,
            1024,
            1024 * 1024,
            32,
            8L * 1024 * 1024
    );

    public RespLimits{
        requirePositive("maxArrDepth", maxArrDepth);
        requirePositive("maxLineBytes", maxLineBytes);
        requirePositive("maxBulkBytes", maxBulkBytes);
        requirePositive("maxArrElements", maxArrElements);
        requirePositive("maxValueBytes", maxValueBytes);
    }

    private static void requirePositive(String name, long value) {
        if (value <= 0)
            throw new IllegalArgumentException(name + " must be positive: " + value);
    }
}
