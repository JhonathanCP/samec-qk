package com.essalud.samec.shared.config;

import java.util.List;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Configuración propia de SAMEC (prefijo {@code samec.} en application.properties). */
@ConfigMapping(prefix = "samec")
public interface SamecConfig {

    /** URL pública del frontend: se usa en los enlaces de los correos. */
    String appUrl();

    Cookie cookie();

    Recaptcha recaptcha();

    Auth auth();

    RateLimit ratelimit();

    Registro registro();

    Seed seed();

    interface Cookie {
        /** true en cuanto el despliegue tenga TLS. */
        @WithDefault("false")
        boolean secure();
    }

    interface Recaptcha {
        @WithDefault("false")
        boolean enabled();

        Optional<String> secret();

        /** reCAPTCHA v3 devuelve un puntaje de 0.0 a 1.0; por debajo se considera bot. */
        @WithDefault("0.5")
        double puntajeMinimo();
    }

    interface Auth {
        @WithDefault("30")
        int accessTokenMinutos();

        @WithDefault("24")
        int refreshTokenHoras();

        /** Ventana en que reusar un refresh token recién rotado no se trata como robo (pestañas). */
        @WithDefault("30")
        int refreshGraciaSegundos();

        @WithDefault("5")
        int otpMinutos();

        @WithDefault("8")
        int passwordLongitudMinima();
    }

    interface RateLimit {
        @WithDefault("true")
        boolean enabled();

        /** IPs o redes CIDR cuya cabecera X-Real-IP se acepta como IP del cliente. */
        @WithDefault("127.0.0.1,::1")
        List<String> proxiesConfiables();
    }

    interface Registro {
        /** Dominio obligatorio del correo en el autorregistro. */
        @WithDefault("essalud.gob.pe")
        String dominioCorreo();
    }

    interface Seed {
        /**
         * Carga el catálogo LEA (seed/lea.json, generado desde el Excel del manual) y los tipos de
         * observación al arrancar, solo si el catálogo está vacío.
         */
        @WithDefault("true")
        boolean lea();

        /** Siembra un usuario de prueba por rol (solo dev/test). */
        @WithDefault("false")
        boolean usuariosDemo();

        @WithDefault("Samec#Dev2026")
        String passwordDemo();

        AdminInicial adminInicial();

        interface AdminInicial {
            Optional<String> username();

            Optional<String> email();

            Optional<String> password();
        }
    }
}
