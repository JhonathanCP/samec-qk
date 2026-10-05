package com.essalud.samec.auth.acceso;

import java.time.LocalDateTime;

import com.essalud.samec.auth.acceso.AccesoRegistro.MotivoAcceso;
import com.essalud.samec.shared.paginacion.Pagina;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AccesoService implements PanacheRepositoryBase<AccesoRegistro, Long> {

    public record AccesoDto(Long id, String username, Long idUsuario, boolean exito, MotivoAcceso motivo,
                            String ip, String userAgent, LocalDateTime fecha) {
    }

    /** En su propia transacción: el intento queda registrado aunque el login falle. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void registrar(String username, Long idUsuario, MotivoAcceso motivo, String ip, String userAgent) {
        AccesoRegistro fila = new AccesoRegistro();
        fila.setUsername(recortar(username == null ? "" : username.trim().toLowerCase(), 180));
        fila.setIdUsuario(idUsuario);
        fila.setExito(motivo == MotivoAcceso.OK);
        fila.setMotivo(motivo);
        fila.setIp(recortar(ip, 45));
        fila.setUserAgent(recortar(userAgent, 255));
        fila.setFecha(LocalDateTime.now());
        persist(fila);
    }

    @Transactional
    public Pagina<AccesoDto> listar(String username, Boolean exito, int page, int size) {
        StringBuilder filtro = new StringBuilder("1 = 1");
        Parameters params = new Parameters();
        if (username != null && !username.isBlank()) {
            filtro.append(" and lower(username) like :u");
            params.and("u", "%" + username.trim().toLowerCase() + "%");
        }
        if (exito != null) {
            filtro.append(" and exito = :e");
            params.and("e", exito);
        }
        return Pagina.de(find(filtro + " order by fecha desc", params), page, size,
                a -> new AccesoDto(a.getId(), a.getUsername(), a.getIdUsuario(), a.getExito(), a.getMotivo(),
                        a.getIp(), a.getUserAgent(), a.getFecha()));
    }

    private static String recortar(String valor, int max) {
        return valor == null || valor.length() <= max ? valor : valor.substring(0, max);
    }
}
