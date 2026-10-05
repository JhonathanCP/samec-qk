package com.essalud.samec.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class IntentosFallidosTest {

    private final AtomicLong ahora = new AtomicLong(1_000_000_000L);
    private final IntentosFallidos intentos = new IntentosFallidos(ahora::get);

    private void avanzar(Duration d) {
        ahora.addAndGet(d.toNanos());
    }

    @Test
    void bloqueaAlQuintoFallo_duranteQuinceMinutos() {
        for (int i = 1; i < IntentosFallidos.MAX_INTENTOS; i++) {
            assertThat(intentos.registrarFallo("login:ana")).isFalse();
        }
        assertThat(intentos.segundosDeBloqueo("login:ana")).isZero();

        assertThat(intentos.registrarFallo("login:ana")).isTrue();
        assertThat(intentos.segundosDeBloqueo("login:ana")).isBetween(899L, 900L);

        avanzar(Duration.ofMinutes(15).plusSeconds(1));
        assertThat(intentos.segundosDeBloqueo("login:ana")).isZero();
    }

    @Test
    void unAccesoCorrectoOlvidaLosFallos() {
        for (int i = 1; i < IntentosFallidos.MAX_INTENTOS; i++) intentos.registrarFallo("login:ana");
        intentos.limpiar("login:ana");
        assertThat(intentos.registrarFallo("login:ana")).isFalse();
    }

    @Test
    void fallosSeparadosPorMasDeLaVentanaNoSeAcumulan() {
        for (int i = 1; i < IntentosFallidos.MAX_INTENTOS; i++) intentos.registrarFallo("login:ana");
        avanzar(Duration.ofMinutes(16));
        assertThat(intentos.registrarFallo("login:ana")).isFalse();
    }

    @Test
    void lasClavesSonIndependientes() {
        for (int i = 0; i < IntentosFallidos.MAX_INTENTOS; i++) intentos.registrarFallo("login:ana");
        assertThat(intentos.segundosDeBloqueo("login:ana")).isPositive();
        assertThat(intentos.segundosDeBloqueo("login:luis")).isZero();
    }
}
