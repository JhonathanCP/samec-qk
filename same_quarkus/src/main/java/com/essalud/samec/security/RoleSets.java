package com.essalud.samec.security;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Única fuente de verdad de los roles de SAMEC y de los conjuntos que usan las reglas de
 * autorización. Para agregar un rol del core: declararlo aquí (código, descripción y alcance),
 * darle permisos en {@link Permisos} y, si corresponde, decir quién lo administra
 * ({@link #ADMINISTRA}). El frontend recibe los permisos ya resueltos en la sesión.
 */
public final class RoleSets {

    private RoleSets() {
    }

    public static final String ADMIN_SISTEMA = "ADMIN_SISTEMA";
    public static final String GESTOR_CALIDAD_SEDE = "GESTOR_CALIDAD_SEDE";
    public static final String GESTOR_CALIDAD_RED = "GESTOR_CALIDAD_RED";
    public static final String GESTOR_CALIDAD_IPRES = "GESTOR_CALIDAD_IPRES";
    /** Califica las evidencias de su IPRESS según el LEA. */
    public static final String EVALUADOR = "EVALUADOR";
    /** Registra las evidencias (fuentes auditables) de su IPRESS. */
    public static final String EVALUADO = "EVALUADO";

    /** Códigos anteriores → actuales; DataInitializer renombra los que encuentre en la BD. */
    public static final Map<String, String> CODIGOS_ANTERIORES = Map.of(
            "EVALUADOR_INTERNO", EVALUADOR,
            "DIRECTOR_IPRES", EVALUADO);

    /** Rol → descripción, en el orden en que se muestran. Lo siembra DataInitializer. */
    public static final Map<String, String> DESCRIPCIONES = new LinkedHashMap<>();

    static {
        DESCRIPCIONES.put(ADMIN_SISTEMA, "Administrador del sistema.");
        DESCRIPCIONES.put(GESTOR_CALIDAD_SEDE, "Gestor de Calidad de Sede Central (alcance nacional).");
        DESCRIPCIONES.put(GESTOR_CALIDAD_RED, "Gestor de Calidad de una Red Asistencial.");
        DESCRIPCIONES.put(GESTOR_CALIDAD_IPRES, "Gestor de Calidad de una IPRESS.");
        DESCRIPCIONES.put(EVALUADOR, "Evaluador de una IPRESS: valida los sustentos y califica los criterios de acreditación.");
        DESCRIPCIONES.put(EVALUADO, "Evaluado de una IPRESS: realiza la carga documental de las fuentes auditables.");
    }

    /** Roles cuyo alcance de datos es su propia IPRESS. */
    public static final Set<String> IPRESS_SCOPE_ROLES = Set.of(GESTOR_CALIDAD_IPRES, EVALUADOR, EVALUADO);

    /** Roles cuyo alcance de datos es su propia Red. */
    public static final Set<String> RED_SCOPE_ROLES = Set.of(GESTOR_CALIDAD_RED);

    /** Roles que ven el listado de usuarios (cada uno dentro de su alcance). */
    public static final Set<String> VEN_USUARIOS = Set.of(
            ADMIN_SISTEMA, GESTOR_CALIDAD_SEDE, GESTOR_CALIDAD_RED, GESTOR_CALIDAD_IPRES);

    /**
     * Qué roles puede crear/editar/activar cada rol administrador. El Gestor de Red solo dentro de
     * las IPRESS de su Red y el Gestor de IPRESS solo en la suya (ver UsuarioService).
     */
    public static final Map<String, Set<String>> ADMINISTRA = Map.of(
            ADMIN_SISTEMA, Set.copyOf(DESCRIPCIONES.keySet()),
            GESTOR_CALIDAD_SEDE, Set.of(GESTOR_CALIDAD_SEDE, GESTOR_CALIDAD_RED, GESTOR_CALIDAD_IPRES,
                    EVALUADOR, EVALUADO),
            GESTOR_CALIDAD_RED, Set.of(GESTOR_CALIDAD_IPRES, EVALUADOR, EVALUADO),
            GESTOR_CALIDAD_IPRES, Set.of(EVALUADOR, EVALUADO));

    /**
     * Roles que una persona puede pedir para sí misma desde /auth/registro: quedan PENDIENTES hasta
     * que Sede Central los apruebe. Admin y Sede quedan fuera: solo los crea otro administrador.
     */
    public static final Set<String> SELF_REGISTER_ROLES =
            Set.of(GESTOR_CALIDAD_RED, GESTOR_CALIDAD_IPRES, EVALUADOR, EVALUADO);

    /** Roles que reciben el aviso de una solicitud de registro nueva. */
    public static final Set<String> REVISAN_SOLICITUDES = Set.of(GESTOR_CALIDAD_SEDE);

    /** Alcance resultante de un conjunto de roles: IPRESS tiene precedencia sobre RED, y RED sobre NACIONAL. */
    public static Alcance alcanceDe(Collection<String> roles) {
        if (roles.stream().anyMatch(IPRESS_SCOPE_ROLES::contains)) return Alcance.IPRESS;
        if (roles.stream().anyMatch(RED_SCOPE_ROLES::contains)) return Alcance.RED;
        return Alcance.NACIONAL;
    }

    /** Posición del rol en {@link #DESCRIPCIONES}, para ordenar listas. */
    public static int orden(String codigo) {
        int i = 0;
        for (String c : DESCRIPCIONES.keySet()) {
            if (c.equals(codigo)) return i;
            i++;
        }
        return Integer.MAX_VALUE;
    }

    /** Roles que administra quien tiene estos roles (unión de cada uno). */
    public static Set<String> administrablesPor(Collection<String> roles) {
        return roles.stream()
                .flatMap(r -> ADMINISTRA.getOrDefault(r, Set.of()).stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
