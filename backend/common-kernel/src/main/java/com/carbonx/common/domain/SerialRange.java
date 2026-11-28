package com.carbonx.common.domain;

/** Immutable Value Object representing a contiguous carbon credit serial range [start, end]. */
public record SerialRange(long start, long end) {
    public SerialRange {
        if (start <= 0 || end < start) {
            throw new IllegalArgumentException("Invalid serial range: [" + start + ", " + end + "]");
        }
    }
    public long quantity() { return (end - start) + 1; }
    public String formatted(String prefix) { return String.format("%s-%06d..%06d", prefix, start, end); }
}
