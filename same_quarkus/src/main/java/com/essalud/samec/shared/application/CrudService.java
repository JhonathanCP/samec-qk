package com.essalud.samec.shared.application;

import java.util.List;

import com.essalud.samec.shared.application.port.in.CrudUseCase;
import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;
import com.essalud.samec.shared.domain.port.CrudRepository;

import jakarta.transaction.Transactional;

/**
 * Generic implementation of {@link CrudUseCase} over a {@link CrudRepository} (the generic
 * "ServiceImpl"). A concrete service supplies the repository, how to put the id into a model, and
 * optionally {@link #validate} for references the database cannot explain in user terms.
 */
@Transactional
public abstract class CrudService<T, ID> implements CrudUseCase<T, ID> {

    protected abstract CrudRepository<T, ID> repository();

    /** The model with this id (used by update, so the path id wins over the body). */
    protected abstract T withId(T model, ID id);

    /** Rules to check before create and update; throws DomainException. */
    protected void validate(T model) {
    }

    @Override
    public List<T> list() {
        return repository().findAll();
    }

    @Override
    public Page<T> list(PageRequest pageRequest) {
        return repository().findAll(pageRequest);
    }

    @Override
    public T get(ID id) {
        return repository().getById(id);
    }

    @Override
    public T create(T model) {
        T fresh = withId(model, null);
        validate(fresh);
        return repository().save(fresh);
    }

    @Override
    public T update(ID id, T model) {
        repository().getById(id); // 404 when it does not exist
        T updated = withId(model, id);
        validate(updated);
        return repository().save(updated);
    }

    @Override
    public void delete(ID id) {
        repository().deleteById(id);
    }
}
