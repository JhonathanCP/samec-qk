package com.essalud.samec.usuarios;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UsuarioRepository implements PanacheRepositoryBase<Usuario, Long> {

    private static final String ORDEN = " order by u.apellido, u.nombre";

    public Optional<Usuario> porUsername(String username) {
        return username == null ? Optional.empty()
                : find("lower(username) = ?1", username.trim().toLowerCase()).firstResultOptional();
    }

    public Optional<Usuario> porEmail(String email) {
        return email == null ? Optional.empty()
                : find("lower(email) = ?1", email.trim().toLowerCase()).firstResultOptional();
    }

    /** Para login y "olvidé mi contraseña": acepta el usuario o el correo. */
    public Optional<Usuario> porUsernameOEmail(String valor) {
        if (valor == null || valor.isBlank()) return Optional.empty();
        return porUsername(valor).or(() -> porEmail(valor));
    }

    public List<Usuario> todos() {
        return find("select u from Usuario u" + ORDEN).list();
    }

    /** Usuarios de las IPRESS de la Red, más los que tienen esa Red como propia. */
    public List<Usuario> deRed(Long idRed) {
        return find("select u from Usuario u left join u.ipress i where i.red.id = :r or u.red.id = :r" + ORDEN,
                Parameters.with("r", idRed)).list();
    }

    public List<Usuario> deIpress(Long idIpress) {
        return find("select u from Usuario u where u.ipress.id = :i" + ORDEN, Parameters.with("i", idIpress)).list();
    }

    public List<Usuario> activosConRol(String codigoRol) {
        return find("select distinct u from Usuario u join u.roles r where r.codigo = ?1 and u.activo = true",
                codigoRol).list();
    }

    public boolean existeConRol(String codigoRol) {
        return find("select u from Usuario u join u.roles r where r.codigo = ?1", codigoRol).count() > 0;
    }
}
