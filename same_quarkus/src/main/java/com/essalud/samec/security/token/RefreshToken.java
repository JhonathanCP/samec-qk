package com.essalud.samec.security.token;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Refresh token de una sesión. El valor viaja solo en una cookie httpOnly; aquí se guarda su
 * SHA-256, nunca el token en claro (si se filtra la BD, no sirve para entrar).
 */
@Getter
@Setter
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "ix_refresh_tokens_usuario", columnList = "id_usuario"))
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_refresh_token")
    private Long id;

    @Column(name = "id_usuario", nullable = false)
    private Long idUsuario;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    /** true al usarlo (rotación), al cerrar sesión o al desactivar la cuenta. */
    @Column(name = "revocado", nullable = false)
    private Boolean revocado = false;

    /** Cuándo se usó para renovar la sesión; null si se revocó por otro motivo (logout, etc.). */
    @Column(name = "rotado_en")
    private LocalDateTime rotadoEn;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
