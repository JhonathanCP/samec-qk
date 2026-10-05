package com.essalud.samec.security.ratelimit;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Limitador en memoria por "token bucket": cada clave (IP, usuario, cuenta…) dispone de
 * {@code capacidad} fichas que se reponen de forma continua a razón de {@code capacidad} por
 * {@code periodo}. Permite ráfagas cortas (hasta la capacidad) y frena el uso sostenido.
 *
 * En memoria a propósito: el core corre como una sola instancia (docker-compose) y así no
 * se agrega ninguna dependencia ni tabla. Si algún día se escala a varias réplicas, cada una
 * limitaría por separado (el límite efectivo se multiplica por el número de réplicas).
 */
@ApplicationScoped
public class RateLimiter {

    /** Regla de límite: {@code capacidad} solicitudes por {@code periodo}. */
    public record Limite(String nombre, int capacidad, Duration periodo) {
        public Limite {
            if (capacidad <= 0) throw new IllegalArgumentException("capacidad debe ser > 0");
            if (periodo == null || periodo.isZero() || periodo.isNegative())
                throw new IllegalArgumentException("periodo debe ser positivo");
        }
    }

    /** Resultado de consumir una ficha: si se permite y, si no, en cuántos segundos reintentar. */
    public record Resultado(boolean permitido, long reintentarEnSegundos) {}

    /** Limpieza oportunista de claves inactivas cada tantas llamadas (sin hilo programado). */
    private static final int LIMPIAR_CADA = 5_000;
    private static final long INACTIVIDAD_NANOS = Duration.ofHours(1).toNanos();

    private final Map<String, Balde> baldes = new ConcurrentHashMap<>();
    private final AtomicLong llamadas = new AtomicLong();
    private final LongSupplier reloj;

    public RateLimiter() {
        this(System::nanoTime);
    }

    /** Reloj inyectable (nanosegundos) para las pruebas. */
    RateLimiter(LongSupplier reloj) {
        this.reloj = reloj;
    }

    public Resultado consumir(String clave, Limite limite) {
        long ahora = reloj.getAsLong();
        if (llamadas.incrementAndGet() % LIMPIAR_CADA == 0) limpiar(ahora);
        Balde balde = baldes.computeIfAbsent(limite.nombre() + "|" + clave, k -> new Balde(limite.capacidad(), ahora));
        return balde.consumir(limite, ahora);
    }

    /** Cantidad de claves en memoria (para pruebas y monitoreo). */
    int tamanio() {
        return baldes.size();
    }

    private void limpiar(long ahora) {
        baldes.entrySet().removeIf(e -> ahora - e.getValue().ultimoUso > INACTIVIDAD_NANOS);
    }

    private static final class Balde {
        private double fichas;
        private long ultimaReposicion;
        private volatile long ultimoUso;

        Balde(int capacidad, long ahora) {
            this.fichas = capacidad;
            this.ultimaReposicion = ahora;
            this.ultimoUso = ahora;
        }

        synchronized Resultado consumir(Limite limite, long ahora) {
            double porNano = (double) limite.capacidad() / limite.periodo().toNanos();
            fichas = Math.min(limite.capacidad(), fichas + (ahora - ultimaReposicion) * porNano);
            ultimaReposicion = ahora;
            ultimoUso = ahora;
            if (fichas >= 1) {
                fichas -= 1;
                return new Resultado(true, 0);
            }
            long nanosHastaFicha = (long) Math.ceil((1 - fichas) / porNano);
            return new Resultado(false, Math.max(1, (long) Math.ceil(nanosHastaFicha / 1_000_000_000.0)));
        }
    }
}
