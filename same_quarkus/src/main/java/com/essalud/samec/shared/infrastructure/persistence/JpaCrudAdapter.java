package com.essalud.samec.shared.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.essalud.samec.shared.domain.Page;
import com.essalud.samec.shared.domain.PageRequest;
import com.essalud.samec.shared.domain.port.CrudRepository;
import com.essalud.samec.shared.error.RecursoNoEncontradoException;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * JPA implementation of {@link CrudRepository}, written once for every module (the equivalent of
 * Spring's SimpleJpaRepository). A concrete adapter only says how to map between its domain model
 * and its entity, and adds its own queries. Entities must have an "id" attribute.
 *
 * <p>Runs in the caller's transaction or opens one. {@code save} flushes, so constraint
 * violations surface at the call and generated ids (also of children) are already in the result.
 *
 * @param <D>  domain model
 * @param <E>  JPA entity
 * @param <ID> identifier type
 */
@Transactional
public abstract class JpaCrudAdapter<D, E, ID> implements CrudRepository<D, ID> {

    @Inject
    protected EntityManager em;

    private final Class<E> entityClass;
    private final Supplier<E> entityFactory;
    private final String resourceName;

    protected JpaCrudAdapter(Class<E> entityClass, Supplier<E> entityFactory, String resourceName) {
        this.entityClass = entityClass;
        this.entityFactory = entityFactory;
        this.resourceName = resourceName;
    }

    /** Id of the domain model; null when it is new. */
    protected abstract ID idOf(D model);

    protected abstract D toDomain(E entity);

    /** Copies the domain model into a new or managed entity (children included). */
    protected abstract void copyToEntity(D model, E entity);

    @Override
    public D save(D model) {
        ID id = idOf(model);
        E entity;
        if (id == null) {
            entity = entityFactory.get();
            copyToEntity(model, entity);
            em.persist(entity);
        } else {
            entity = findEntity(id);
            copyToEntity(model, entity);
        }
        em.flush();
        return toDomain(entity);
    }

    @Override
    public Optional<D> findById(ID id) {
        return id == null ? Optional.empty() : Optional.ofNullable(em.find(entityClass, id)).map(this::toDomain);
    }

    @Override
    public D getById(ID id) {
        return toDomain(findEntity(id));
    }

    @Override
    public List<D> findAllById(Collection<ID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return list("where e.id in :ids order by e.id", Map.of("ids", ids));
    }

    @Override
    public List<D> findAll() {
        return list("order by e.id", Map.of());
    }

    @Override
    public Page<D> findAll(PageRequest pageRequest) {
        return page("", Map.of(), "e.id", pageRequest);
    }

    @Override
    public boolean existsById(ID id) {
        return id != null && em.createQuery("select count(e) from " + entityName() + " e where e.id = :id", Long.class)
                .setParameter("id", id).getSingleResult() > 0;
    }

    @Override
    public long count() {
        return em.createQuery("select count(e) from " + entityName() + " e", Long.class).getSingleResult();
    }

    @Override
    public void deleteById(ID id) {
        em.remove(findEntity(id));
        em.flush();
    }

    // ── Helpers for the concrete adapters ──

    protected E findEntity(ID id) {
        E entity = id == null ? null : em.find(entityClass, id);
        if (entity == null) throw RecursoNoEncontradoException.de(resourceName, id);
        return entity;
    }

    /** Reference to another entity by id, without loading it (for foreign keys). */
    protected <R> R reference(Class<R> type, Object id) {
        return id == null ? null : em.getReference(type, id);
    }

    protected String entityName() {
        return em.getMetamodel().entity(entityClass).getName();
    }

    /** Query over the entity with alias {@code e}; {@code clause} is what follows "from Entity e". */
    protected List<D> list(String clause, Map<String, ?> params) {
        var query = em.createQuery("select e from " + entityName() + " e " + clause, entityClass);
        params.forEach(query::setParameter);
        return query.getResultList().stream().map(this::toDomain).toList();
    }

    protected Optional<D> first(String clause, Map<String, ?> params) {
        var query = em.createQuery("select e from " + entityName() + " e " + clause, entityClass).setMaxResults(1);
        params.forEach(query::setParameter);
        return query.getResultList().stream().findFirst().map(this::toDomain);
    }

    /** Paged query; {@code where} may be empty, {@code orderBy} is a JPQL expression over {@code e}. */
    protected Page<D> page(String where, Map<String, ?> params, String orderBy, PageRequest request) {
        var query = em.createQuery("select e from " + entityName() + " e " + where + " order by " + orderBy, entityClass)
                .setFirstResult(request.offset()).setMaxResults(request.size());
        var count = em.createQuery("select count(e) from " + entityName() + " e " + where, Long.class);
        params.forEach((k, v) -> {
            query.setParameter(k, v);
            count.setParameter(k, v);
        });
        List<D> content = query.getResultList().stream().map(this::toDomain).toList();
        return Page.of(content, count.getSingleResult(), request);
    }
}
