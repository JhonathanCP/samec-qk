package com.essalud.samec.shared.paginacion;

import java.util.List;
import java.util.function.Function;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;

/** Página de resultados con el mismo formato que espera el frontend (PagedResult). */
public record Pagina<T>(List<T> content, long totalElements, int totalPages, int page, int size) {

    public static final int TAMANIO_MAXIMO = 200;

    public static <E, T> Pagina<T> de(PanacheQuery<E> query, int page, int size, Function<E, T> mapper) {
        int tamanio = Math.max(1, Math.min(size, TAMANIO_MAXIMO));
        int pagina = Math.max(0, page);
        query.page(Page.of(pagina, tamanio));
        List<T> contenido = query.list().stream().map(mapper).toList();
        return new Pagina<>(contenido, query.count(), query.pageCount(), pagina, tamanio);
    }
}
