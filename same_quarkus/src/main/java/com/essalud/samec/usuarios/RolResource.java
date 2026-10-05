package com.essalud.samec.usuarios;

import java.util.List;

import com.essalud.samec.security.RoleSets;
import com.essalud.samec.usuarios.dto.UsuarioDtos.RolDto;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/roles")
public class RolResource {

    @Inject
    RolRepository roles;

    @GET
    @Transactional
    public List<RolDto> listar() {
        return ordenados(roles.listAll());
    }

    /** Roles que se pueden pedir desde el formulario público de registro. */
    @GET
    @Path("/autorregistro")
    @PermitAll
    @Transactional
    public List<RolDto> autorregistro() {
        return ordenados(roles.porCodigos(RoleSets.SELF_REGISTER_ROLES));
    }

    private static List<RolDto> ordenados(List<Rol> lista) {
        return lista.stream()
                .sorted(java.util.Comparator.comparingInt(r -> RoleSets.orden(r.getCodigo())))
                .map(RolDto::de)
                .toList();
    }
}
