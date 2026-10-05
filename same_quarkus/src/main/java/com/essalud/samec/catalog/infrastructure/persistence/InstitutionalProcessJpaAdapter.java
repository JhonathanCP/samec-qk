package com.essalud.samec.catalog.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.essalud.samec.catalog.domain.model.InstitutionalProcess;
import com.essalud.samec.catalog.domain.port.out.InstitutionalProcessRepository;
import com.essalud.samec.shared.domain.DomainException;
import com.essalud.samec.shared.infrastructure.persistence.JpaCrudAdapter;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InstitutionalProcessJpaAdapter
        extends JpaCrudAdapter<InstitutionalProcess, InstitutionalProcessEntity, Long>
        implements InstitutionalProcessRepository {

    public InstitutionalProcessJpaAdapter() {
        super(InstitutionalProcessEntity.class, InstitutionalProcessEntity::new, "Proceso institucional");
    }

    @Override
    protected Long idOf(InstitutionalProcess model) {
        return model.id();
    }

    @Override
    protected InstitutionalProcess toDomain(InstitutionalProcessEntity e) {
        return new InstitutionalProcess(e.getId(), e.getParent() == null ? null : e.getParent().getId(), e.getName());
    }

    @Override
    protected void copyToEntity(InstitutionalProcess m, InstitutionalProcessEntity e) {
        if (m.parentId() != null && m.parentId().equals(m.id())) {
            throw new DomainException("Un proceso no puede ser su propio padre");
        }
        e.setParent(reference(InstitutionalProcessEntity.class, m.parentId()));
        e.setName(m.name());
    }

    @Override
    public List<InstitutionalProcess> findRoots() {
        return list("where e.parent is null order by e.name", Map.of());
    }

    @Override
    public List<InstitutionalProcess> findByParentId(Long parentId) {
        if (parentId == null) return findRoots();
        return list("where e.parent.id = :id order by e.name", Map.of("id", parentId));
    }

    @Override
    public Optional<InstitutionalProcess> findByParentIdAndName(Long parentId, String name) {
        if (name == null) return Optional.empty();
        return parentId == null
                ? first("where e.parent is null and e.name = :name", Map.of("name", name))
                : first("where e.parent.id = :id and e.name = :name", Map.of("id", parentId, "name", name));
    }
}
