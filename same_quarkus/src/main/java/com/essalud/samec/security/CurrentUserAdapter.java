package com.essalud.samec.security;

import com.essalud.samec.organizacion.Ipress;
import com.essalud.samec.organizacion.IpressRepository;
import com.essalud.samec.shared.application.port.CurrentUser;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** {@link CurrentUser} backed by the session that SesionAugmentor builds from the access token. */
@ApplicationScoped
public class CurrentUserAdapter implements CurrentUser {

    @Inject
    UsuarioActual usuario;

    @Inject
    IpressRepository ipress;

    @Override
    public Long id() {
        return usuario.id();
    }

    @Override
    public boolean hasPermission(String permission) {
        return usuario.tienePermiso(permission);
    }

    @Override
    public Long facilityId() {
        return usuario.alcance() == Alcance.IPRESS ? usuario.idIpress() : null;
    }

    @Override
    public boolean hasNationalScope() {
        return usuario.autenticado() && usuario.alcance() == Alcance.NACIONAL;
    }

    @Override
    public boolean canAccessFacility(Long facilityId) {
        if (facilityId == null || !usuario.autenticado()) return false;
        return switch (usuario.alcance()) {
            case NACIONAL -> true;
            case IPRESS -> facilityId.equals(usuario.idIpress());
            case RED -> {
                Ipress facility = ipress.findById(facilityId);
                yield facility != null && usuario.cubreIpress(facilityId, facility.getRed().getId());
            }
        };
    }
}
