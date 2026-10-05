package com.essalud.samec.shared.infrastructure.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;

/**
 * Brings a child collection of an aggregate (mapped with cascade + orphanRemoval) in line with
 * the domain: children matched by key are updated, missing ones removed, the rest created.
 */
public final class ChildCollections {

    private ChildCollections() {
    }

    /**
     * @param em          used to flush removals before inserting, so a replaced child does not
     *                    collide with a unique key of the one it replaces
     * @param current     the entity's collection
     * @param desired     the domain children
     * @param entityKey   key of an entity child (its id or a natural key)
     * @param modelKey    key of a domain child; null means new
     * @param factory     creates an entity child
     * @param copy        copies a domain child into an entity child
     * @param attach      adds a new entity child to the parent (sets the back reference)
     */
    public static <E, M, K> void sync(EntityManager em, List<E> current, Collection<M> desired,
                                      Function<E, K> entityKey, Function<M, K> modelKey,
                                      Supplier<E> factory, BiConsumer<M, E> copy, Consumer<E> attach) {
        Set<K> wanted = new HashSet<>();
        for (M model : desired) {
            K key = modelKey.apply(model);
            if (key != null) wanted.add(key);
        }
        boolean removed = current.removeIf(e -> !wanted.contains(entityKey.apply(e)));
        if (removed) em.flush();

        Map<K, E> byKey = new HashMap<>();
        for (E entity : current) byKey.put(entityKey.apply(entity), entity);
        for (M model : desired) {
            K key = modelKey.apply(model);
            E entity = key == null ? null : byKey.get(key);
            if (entity == null) {
                entity = factory.get();
                copy.accept(model, entity);
                attach.accept(entity);
            } else {
                copy.accept(model, entity);
            }
        }
    }

    /** Same as {@link #sync} for children identified by their own id. */
    public static <E, M> void syncById(EntityManager em, List<E> current, Collection<M> desired,
                                       Function<E, Long> entityId, Function<M, Long> modelId,
                                       Supplier<E> factory, BiConsumer<M, E> copy, Consumer<E> attach) {
        sync(em, current, desired, entityId, modelId, factory, copy, attach);
    }
}
