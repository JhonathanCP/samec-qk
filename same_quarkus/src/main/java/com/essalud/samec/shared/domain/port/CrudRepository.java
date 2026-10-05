package com.essalud.samec.shared.domain.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;

/**
 * Generic CRUD port (the equivalent of Spring's CrudRepository), expressed with domain models.
 * Every module's repository port extends it and adds its own queries; the JPA implementation is
 * written once in JpaCrudAdapter.
 *
 * @param <T>  domain model
 * @param <ID> identifier type
 */
public interface CrudRepository<T, ID> {

    /** Inserts when the model has no id, updates otherwise. Returns the stored model with its id. */
    T save(T model);

    Optional<T> findById(ID id);

    /** Same as {@link #findById} but fails (not found) when it does not exist. */
    T getById(ID id);

    List<T> findAllById(Collection<ID> ids);

    List<T> findAll();

    Page<T> findAll(PageRequest pageRequest);

    boolean existsById(ID id);

    long count();

    /** Fails (not found) when it does not exist. */
    void deleteById(ID id);
}
