package com.essalud.samec.catalog.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.essalud.samec.catalog.domain.model.ObservationType;
import com.essalud.samec.catalog.domain.port.out.ObservationTypeRepository;
import com.essalud.samec.shared.infrastructure.persistence.JpaCrudAdapter;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ObservationTypeJpaAdapter extends JpaCrudAdapter<ObservationType, ObservationTypeEntity, Long>
        implements ObservationTypeRepository {

    public ObservationTypeJpaAdapter() {
        super(ObservationTypeEntity.class, ObservationTypeEntity::new, "Tipo de observación");
    }

    @Override
    protected Long idOf(ObservationType model) {
        return model.id();
    }

    @Override
    protected ObservationType toDomain(ObservationTypeEntity e) {
        return new ObservationType(e.getId(), e.getCode(), e.getName(), Boolean.TRUE.equals(e.getActive()));
    }

    @Override
    protected void copyToEntity(ObservationType m, ObservationTypeEntity e) {
        e.setCode(m.code());
        e.setName(m.name());
        e.setActive(m.active());
    }

    @Override
    public Optional<ObservationType> findByCode(String code) {
        return code == null ? Optional.empty() : first("where e.code = :code", Map.of("code", code));
    }

    @Override
    public List<ObservationType> findActive() {
        return list("where e.active = true order by e.name", Map.of());
    }
}
