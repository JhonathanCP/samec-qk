package com.essalud.samec.security.ratelimit;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Cuenta intentos fallidos por clave (una cuenta de usuario, un código OTP…) y bloquea la clave
 * temporalmente al llegar al máximo dentro de la ventana. A diferencia del límite por IP, frena la
 * fuerza bruta aunque el atacante reparta los intentos entre muchas IP, y no castiga a los demás
 * usuarios que comparten la misma IP (redes de hospitales detrás de un NAT).
 */
@ApplicationScoped
public class IntentosFallidos {

    public static final int MAX_INTENTOS = 5;
    public static final Duration VENTANA = Duration.ofMinutes(15);
    public static final Duration BLOQUEO = Duration.ofMinutes(15);

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();
    private final LongSupplier reloj;

    public IntentosFallidos() {
        this(System::nanoTime);
    }

    IntentosFallidos(LongSupplier reloj) {
        this.reloj = reloj;
    }

    /** Segundos que faltan para que la clave se desbloquee; 0 si no está bloqueada. */
    public long segundosDeBloqueo(String clave) {
        Registro r = registros.get(clave);
        if (r == null) return 0;
        long ahora = reloj.getAsLong();
        synchronized (r) {
            if (r.bloqueadoHasta == 0) return 0;
            long restante = r.bloqueadoHasta - ahora;
            if (restante <= 0) {
                registros.remove(clave, r);
                return 0;
            }
            return Math.max(1, (long) Math.ceil(restante / 1_000_000_000.0));
        }
    }

    /** Registra un fallo; devuelve true si con este fallo la clave quedó bloqueada. */
    public boolean registrarFallo(String clave) {
        long ahora = reloj.getAsLong();
        if (registros.size() > 50_000) limpiar(ahora);
        Registro r = registros.computeIfAbsent(clave, k -> new Registro(ahora));
        synchronized (r) {
            if (ahora - r.inicioVentana > VENTANA.toNanos()) {
                r.inicioVentana = ahora;
                r.fallos = 0;
            }
            r.fallos++;
            if (r.fallos >= MAX_INTENTOS && r.bloqueadoHasta == 0) {
                r.bloqueadoHasta = ahora + BLOQUEO.toNanos();
                return true;
            }
            return r.bloqueadoHasta != 0;
        }
    }

    /** Olvida los fallos de la clave (tras un acceso correcto o un código nuevo). */
    public void limpiar(String clave) {
        registros.remove(clave);
    }

    private void limpiar(long ahora) {
        long limite = Math.max(VENTANA.toNanos(), BLOQUEO.toNanos());
        registros.entrySet().removeIf(e -> {
            Registro r = e.getValue();
            return ahora - r.inicioVentana > limite && (r.bloqueadoHasta == 0 || r.bloqueadoHasta < ahora);
        });
    }

    private static final class Registro {
        long inicioVentana;
        int fallos;
        long bloqueadoHasta;

        Registro(long ahora) {
            this.inicioVentana = ahora;
        }
    }
}
