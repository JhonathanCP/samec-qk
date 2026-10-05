package com.essalud.samec.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final AtomicLong ahora = new AtomicLong(1_000_000_000L);
    private final RateLimiter limiter = new RateLimiter(ahora::get);
    private final RateLimiter.Limite cincoPorMinuto = new RateLimiter.Limite("test", 5, Duration.ofMinutes(1));

    private void avanzar(Duration d) {
        ahora.addAndGet(d.toNanos());
    }

    @Test
    void permiteHastaLaCapacidad_yLuegoRechazaIndicandoCuandoReintentar() {
        for (int i = 0; i < 5; i++) assertThat(limiter.consumir("a", cincoPorMinuto).permitido()).isTrue();

        RateLimiter.Resultado r = limiter.consumir("a", cincoPorMinuto);
        assertThat(r.permitido()).isFalse();
        // Se repone 1 ficha cada 12 s (5 por minuto).
        assertThat(r.reintentarEnSegundos()).isBetween(11L, 12L);
    }

    @Test
    void reponeFichasConElTiempo() {
        for (int i = 0; i < 5; i++) limiter.consumir("a", cincoPorMinuto);
        assertThat(limiter.consumir("a", cincoPorMinuto).permitido()).isFalse();

        avanzar(Duration.ofSeconds(12));
        assertThat(limiter.consumir("a", cincoPorMinuto).permitido()).isTrue();
        assertThat(limiter.consumir("a", cincoPorMinuto).permitido()).isFalse();

        // Tras un periodo completo vuelve a la capacidad, nunca más allá.
        avanzar(Duration.ofMinutes(10));
        for (int i = 0; i < 5; i++) assertThat(limiter.consumir("a", cincoPorMinuto).permitido()).isTrue();
        assertThat(limiter.consumir("a", cincoPorMinuto).permitido()).isFalse();
    }

    @Test
    void cadaClaveYCadaReglaTienenSuPropioBalde() {
        for (int i = 0; i < 5; i++) limiter.consumir("a", cincoPorMinuto);
        assertThat(limiter.consumir("a", cincoPorMinuto).permitido()).isFalse();

        assertThat(limiter.consumir("b", cincoPorMinuto).permitido()).isTrue();
        RateLimiter.Limite otra = new RateLimiter.Limite("otra", 5, Duration.ofMinutes(1));
        assertThat(limiter.consumir("a", otra).permitido()).isTrue();
    }
}
