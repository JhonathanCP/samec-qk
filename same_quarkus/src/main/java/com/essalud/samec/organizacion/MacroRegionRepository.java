package com.essalud.samec.organizacion;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MacroRegionRepository implements PanacheRepositoryBase<MacroRegion, Long> {

    public Optional<MacroRegion> porCodigo(String codigo) {
        return find("codigo", codigo).firstResultOptional();
    }

    public List<MacroRegion> todas() {
        return list("order by nombre");
    }
}
