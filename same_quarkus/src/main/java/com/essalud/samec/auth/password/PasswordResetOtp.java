package com.essalud.samec.auth.password;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Código de un solo uso de "olvidé mi contraseña" (8 dígitos, vence en pocos minutos). Lo único
 * que autoriza el cambio es haberlo recibido en el correo institucional. Se guarda su SHA-256.
 */
@Getter
@Setter
@Entity
@Table(name = "password_reset_otp")
public class PasswordResetOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_otp")
    private Long id;

    @Column(name = "id_usuario", nullable = false)
    private Long idUsuario;

    @Column(name = "otp_hash", length = 64, nullable = false)
    private String otpHash;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "usado", nullable = false)
    private Boolean usado = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
