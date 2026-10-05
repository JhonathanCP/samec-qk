package com.essalud.samec.auth.acceso;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Un intento de inicio de sesión, exitoso o no. */
@Getter
@Setter
@Entity
@Table(name = "auditoria_accesos", indexes = {
        @Index(name = "ix_accesos_fecha", columnList = "fecha"),
        @Index(name = "ix_accesos_username", columnList = "username") })
public class AccesoRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_acceso")
    private Long id;

    /** Tal como se escribió (puede no existir). */
    @Column(name = "username", length = 180, nullable = false)
    private String username;

    /** null si el usuario no existe. */
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "exito", nullable = false)
    private Boolean exito;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", length = 30, nullable = false)
    private MotivoAcceso motivo;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    /** Por qué terminó así el intento. */
    public enum MotivoAcceso {
        OK,
        CREDENCIALES_INVALIDAS,
        CUENTA_BLOQUEADA,
        RECAPTCHA_INVALIDO,
        REGISTRO_PENDIENTE,
        REGISTRO_RECHAZADO,
        CUENTA_DESACTIVADA
    }
}
