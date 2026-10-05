package com.essalud.samec.catalog.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.essalud.samec.catalog.domain.model.MacroProcess;
import com.essalud.samec.catalog.domain.port.out.MacroProcessRepository;
import com.essalud.samec.shared.infrastructure.persistence.JpaCrudAdapter;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MacroProcessJpaAdapter extends JpaCrudAdapter<MacroProcess, MacroProcessEntity, Long>
        implements MacroProcessRepository {

    public MacroProcessJpaAdapter() {
        super(MacroProcessEntity.class, MacroProcessEntity::new, "Macroproceso");
    }

    @Override
    protected Long idOf(MacroProcess model) {
        return model.id();
    }

    @Override
    protected MacroProcess toDomain(MacroProcessEntity e) {
        return new MacroProcess(e.getId(), e.getCode(), e.getName(), e.getCategory(), e.getScoreWeight(),
                e.getDisplayOrder());
    }

    @Override
    protected void copyToEntity(MacroProcess m, MacroProcessEntity e) {
        e.setCode(m.code());
        e.setName(m.name());
        e.setCategory(m.category());
        e.setScoreWeight(m.scoreWeight());
        e.setDisplayOrder(m.displayOrder());
    }

    @Override
    public Optional<MacroProcess> findByCode(String code) {
        return code == null ? Optional.empty() : first("where e.code = :code", Map.of("code", code));
    }

    @Override
    public List<MacroProcess> findAllOrdered() {
        return list("order by e.displayOrder, e.id", Map.of());
    }
}
