package com.essalud.samec.organizacion;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class IpressRepository implements PanacheRepositoryBase<Ipress, Long> {

    public Optional<Ipress> porCodigo(String codigo) {
        return find("codigo", codigo).firstResultOptional();
    }

    public List<Ipress> listar(Long idRed, boolean soloActivas) {
        String filtro = (idRed != null ? "red.id = :idRed" : "1 = 1") + (soloActivas ? " and activo = true" : "");
        Parameters params = idRed != null ? Parameters.with("idRed", idRed) : new Parameters();
        return list(filtro + " order by nombre", params);
    }

    /** ¿La IPRESS pertenece a esa Red? */
    public boolean perteneceARed(Long idIpress, Long idRed) {
        return idIpress != null && idRed != null && count("id = ?1 and red.id = ?2", idIpress, idRed) > 0;
    }
}
