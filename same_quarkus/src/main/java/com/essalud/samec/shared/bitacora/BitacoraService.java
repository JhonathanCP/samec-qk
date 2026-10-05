package com.essalud.samec.shared.bitacora;

import java.time.LocalDateTime;
import java.util.List;

import com.essalud.samec.security.UsuarioActual;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Historial de acciones de cualquier entidad. Se registra al terminar cada acción, dentro de la
 * misma transacción: si la acción falla, tampoco queda en la bitácora.
 */
@ApplicationScoped
public class BitacoraService implements PanacheRepositoryBase<BitacoraCambio, Long> {

    public record Entrada(Long id, String accion, String detalle, String usuarioNombre, LocalDateTime fecha) {}

    @Transactional
    public void registrar(String entidad, Long idEntidad, String accion, String detalle) {
        BitacoraCambio fila = new BitacoraCambio();
        fila.setEntidad(entidad);
        fila.setIdEntidad(idEntidad);
        fila.setAccion(accion);
        fila.setDetalle(detalle != null && !detalle.isBlank() ? recortar(detalle.strip()) : null);
        fila.setIdUsuario(UsuarioActual.idEnContexto());
        fila.setUsuarioNombre(UsuarioActual.nombreEnContexto());
        fila.setCreatedAt(LocalDateTime.now());
        persist(fila);
    }

    /** Lo más reciente primero. */
    public List<Entrada> listar(String entidad, Long idEntidad) {
        return find("entidad = ?1 and idEntidad = ?2 order by createdAt desc, id desc", entidad, idEntidad)
                .stream()
                .map(f -> new Entrada(f.getId(), f.getAccion(), f.getDetalle(),
                        f.getUsuarioNombre() != null ? f.getUsuarioNombre() : "Sistema", f.getCreatedAt()))
                .toList();
    }

    private static String recortar(String texto) {
        return texto.length() > 4000 ? texto.substring(0, 3997) + "..." : texto;
    }
}
