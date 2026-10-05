package com.essalud.samec.shared.domain;

/** Zero-based page number and size; the size is clamped to 1..{@value #MAX_SIZE}. */
public record PageRequest(int page, int size) {

    public static final int MAX_SIZE = 200;

    public PageRequest {
        page = Math.max(0, page);
        size = Math.max(1, Math.min(size, MAX_SIZE));
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size);
    }

    public int offset() {
        return page * size;
    }
}
