package com.essalud.samec.usuarios;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RolRepository implements PanacheRepositoryBase<Rol, Long> {

    public Optional<Rol> porCodigo(String codigo) {
        return find("codigo", codigo).firstResultOptional();
    }

    public List<Rol> porCodigos(Collection<String> codigos) {
        return codigos.isEmpty() ? List.of() : list("codigo in ?1 and activo = true", codigos);
    }
}
