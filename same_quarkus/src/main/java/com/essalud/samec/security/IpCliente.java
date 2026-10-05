package com.essalud.samec.security;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import com.essalud.samec.shared.config.SamecConfig;

import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * IP real del cliente. Solo se confía en X-Real-IP (lo fija Nginx con $remote_addr) cuando la
 * conexión viene de un proxy confiable (samec.ratelimit.proxies-confiables); si no, cualquiera
 * podría falsificar la cabecera. X-Forwarded-For se usa solo como respaldo y tomando la última
 * entrada (la que agregó nuestro proxy), nunca la primera.
 */
@ApplicationScoped
public class IpCliente {

    private final List<byte[][]> proxiesConfiables;

    @Inject
    public IpCliente(SamecConfig config) {
        this.proxiesConfiables = parsearRedes(config.ratelimit().proxiesConfiables());
    }

    public String de(HttpServerRequest request) {
        String remota = request.remoteAddress() != null ? request.remoteAddress().hostAddress() : "desconocida";
        if (!esProxyConfiable(remota)) return remota;
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] partes = xff.split(",");
            return partes[partes.length - 1].trim();
        }
        return remota;
    }

    private boolean esProxyConfiable(String ip) {
        byte[] dir = direccion(ip);
        if (dir == null) return false;
        for (byte[][] red : proxiesConfiables) {
            if (red[0].length == dir.length && coincide(dir, red[0], red[1][0] & 0xff)) return true;
        }
        return false;
    }

    /** Cada red como {dirección, {bits de prefijo}}; admite IPs sueltas y notación CIDR. */
    static List<byte[][]> parsearRedes(List<String> textos) {
        List<byte[][]> redes = new ArrayList<>();
        for (String parte : textos) {
            String p = parte.trim();
            if (p.isEmpty()) continue;
            String[] cidr = p.split("/");
            byte[] dir = direccion(cidr[0]);
            if (dir == null) continue;
            int bits = cidr.length > 1 ? Integer.parseInt(cidr[1].trim()) : dir.length * 8;
            redes.add(new byte[][] { dir, { (byte) bits } });
        }
        return redes;
    }

    private static boolean coincide(byte[] ip, byte[] red, int bits) {
        int bytesCompletos = bits / 8;
        for (int i = 0; i < bytesCompletos; i++) if (ip[i] != red[i]) return false;
        int resto = bits % 8;
        if (resto == 0) return true;
        int mascara = (0xff << (8 - resto)) & 0xff;
        return (ip[bytesCompletos] & mascara) == (red[bytesCompletos] & mascara);
    }

    /** Parsea solo literales IP (sin consultas DNS); null si no lo es. */
    private static byte[] direccion(String ip) {
        if (ip == null) return null;
        String s = ip.trim();
        if (s.isEmpty() || !(s.indexOf(':') >= 0 || s.matches("[0-9.]+"))) return null;
        try {
            return InetAddress.getByName(s).getAddress();
        } catch (Exception e) {
            return null;
        }
    }
}
