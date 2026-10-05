package com.essalud.samec.organizacion;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RedRepository implements PanacheRepositoryBase<Red, Long> {

    public Optional<Red> porCodigo(String codigo) {
        return find("codigo", codigo).firstResultOptional();
    }

    public List<Red> listar(boolean soloActivas) {
        return soloActivas ? list("activo = true order by nombre") : list("order by nombre");
    }
}
