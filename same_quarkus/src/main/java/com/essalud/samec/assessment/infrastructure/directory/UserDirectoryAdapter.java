package com.essalud.samec.assessment.infrastructure.directory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.essalud.samec.assessment.application.port.out.UserDirectory;
import com.essalud.samec.usuarios.Usuario;
import com.essalud.samec.usuarios.UsuarioRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UserDirectoryAdapter implements UserDirectory {

    @Inject
    UsuarioRepository usuarios;

    @Override
    public Map<Long, String> fullNames(Collection<Long> userIds) {
        List<Long> ids = userIds == null ? List.of() : userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return usuarios.list("id in ?1", ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::nombreCompleto));
    }
}
