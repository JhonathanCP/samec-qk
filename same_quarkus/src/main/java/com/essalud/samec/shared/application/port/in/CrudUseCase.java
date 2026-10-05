package com.essalud.samec.shared.application.port.in;

import java.util.List;

import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;

/**
 * Generic CRUD input port for master data (the generic "service" interface). Use cases with more
 * behavior extend it or are written apart.
 */
public interface CrudUseCase<T, ID> {

    List<T> list();

    Page<T> list(PageRequest pageRequest);

    T get(ID id);

    T create(T model);

    T update(ID id, T model);

    void delete(ID id);
}
