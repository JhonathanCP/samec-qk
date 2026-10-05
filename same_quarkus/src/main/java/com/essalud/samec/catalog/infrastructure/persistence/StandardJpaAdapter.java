package com.essalud.samec.catalog.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.essalud.samec.catalog.domain.model.Standard;
import com.essalud.samec.catalog.domain.port.out.StandardRepository;
import com.essalud.samec.shared.infrastructure.persistence.JpaCrudAdapter;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StandardJpaAdapter extends JpaCrudAdapter<Standard, StandardEntity, Long> implements StandardRepository {

    public StandardJpaAdapter() {
        super(StandardEntity.class, StandardEntity::new, "Estándar");
    }

    @Override
    protected Long idOf(Standard model) {
        return model.id();
    }

    @Override
    protected Standard toDomain(StandardEntity e) {
        return new Standard(e.getId(), e.getMacroProcess().getId(), e.getCode(), e.getStatement());
    }

    @Override
    protected void copyToEntity(Standard m, StandardEntity e) {
        e.setMacroProcess(reference(MacroProcessEntity.class, m.macroProcessId()));
        e.setCode(m.code());
        e.setStatement(m.statement());
    }

    @Override
    public Optional<Standard> findByCode(String code) {
        return code == null ? Optional.empty() : first("where e.code = :code", Map.of("code", code));
    }

    @Override
    public List<Standard> findByMacroProcessId(Long macroProcessId) {
        if (macroProcessId == null) return List.of();
        return list("where e.macroProcess.id = :id order by e.code", Map.of("id", macroProcessId));
    }
}
