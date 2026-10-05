package com.essalud.samec.shared.domain;

import java.util.List;
import java.util.function.Function;

/** A page of results; same shape the frontend expects (PagedResult). */
public record Page<T>(List<T> content, long totalElements, int totalPages, int page, int size) {

    public Page {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static <T> Page<T> of(List<T> content, long totalElements, PageRequest request) {
        int totalPages = (int) ((totalElements + request.size() - 1) / request.size());
        return new Page<>(content, totalElements, totalPages, request.page(), request.size());
    }

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(content.stream().map(mapper).toList(), totalElements, totalPages, page, size);
    }
}
