package com.essalud.samec.security;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.essalud.samec.security.RoleSets.ADMIN_SISTEMA;
import static com.essalud.samec.security.RoleSets.EVALUADO;
import static com.essalud.samec.security.RoleSets.EVALUADOR;
import static com.essalud.samec.security.RoleSets.GESTOR_CALIDAD_IPRES;
import static com.essalud.samec.security.RoleSets.GESTOR_CALIDAD_RED;
import static com.essalud.samec.security.RoleSets.GESTOR_CALIDAD_SEDE;

/**
 * Permisos de acción y qué roles los tienen. Los endpoints los exigen con
 * {@code @PermissionsAllowed(Permisos.X)} (SesionAugmentor resuelve el chequeo) y el frontend los
 * recibe en la sesión para mostrar u ocultar opciones: es la única fuente de verdad.
 *
 * Para el core: agregar aquí los permisos del dominio (p. ej. "autoevaluacion.registrar") con sus
 * roles; nada más que tocar en seguridad.
 */
public final class Permisos {

    private Permisos() {
    }

    // ── Usuarios ──
    /** Administrar usuarios de todo el país. */
    public static final String USUARIOS_CRUD = "usuarios.crud";
    /** Administrar usuarios de las IPRESS de su Red. */
    public static final String USUARIOS_CRUD_RED = "usuarios.crudRed";
    /** Administrar usuarios de su IPRESS. */
    public static final String USUARIOS_CRUD_IPRESS = "usuarios.crudIpress";
    /** Revisar solicitudes de autorregistro. */
    public static final String USUARIOS_SOLICITUDES = "usuarios.solicitudes";

    // ── Administración ──
    public static final String CATALOGOS_CRUD = "catalogos.crud";
    public static final String CONFIG_ADMIN = "config.admin";
    /** Historial de inicios de sesión. */
    public static final String ACCESOS_VER = "accesos.ver";

    // ── Autoevaluación LEA (core) ──
    /** Ver autoevaluaciones, su checklist y resultados (dentro de su alcance). */
    public static final String AUTOEVALUACION_VER = "autoevaluacion.ver";
    /** Iniciar y cerrar la autoevaluación de una IPRESS. */
    public static final String AUTOEVALUACION_GESTIONAR = "autoevaluacion.gestionar";
    /** Registrar evidencias: subir archivos, corregir observaciones. */
    public static final String EVIDENCIAS_REGISTRAR = "evidencias.registrar";
    /** Evaluar: aprobar u observar evidencias, verificar condiciones en campo y calificar criterios. */
    public static final String EVIDENCIAS_EVALUAR = "evidencias.evaluar";

    // ── Tableros ──
    public static final String DASHBOARD_GLOBAL = "dashboard.global";
    public static final String DASHBOARD_RED = "dashboard.red";
    public static final String DASHBOARD_IPRESS = "dashboard.ipress";

    private static final Map<String, Set<String>> ROLES_POR_PERMISO = Map.ofEntries(
            Map.entry(USUARIOS_CRUD, Set.of(ADMIN_SISTEMA, GESTOR_CALIDAD_SEDE)),
            Map.entry(USUARIOS_CRUD_RED, Set.of(ADMIN_SISTEMA, GESTOR_CALIDAD_RED)),
            Map.entry(USUARIOS_CRUD_IPRESS, Set.of(ADMIN_SISTEMA, GESTOR_CALIDAD_IPRES)),
            Map.entry(USUARIOS_SOLICITUDES, Set.of(ADMIN_SISTEMA, GESTOR_CALIDAD_SEDE)),
            Map.entry(CATALOGOS_CRUD, Set.of(ADMIN_SISTEMA, GESTOR_CALIDAD_SEDE)),
            Map.entry(CONFIG_ADMIN, Set.of(ADMIN_SISTEMA, GESTOR_CALIDAD_SEDE)),
            Map.entry(ACCESOS_VER, Set.of(ADMIN_SISTEMA)),
            Map.entry(AUTOEVALUACION_VER, Set.of(ADMIN_SISTEMA, GESTOR_CALIDAD_SEDE, GESTOR_CALIDAD_RED,
                    GESTOR_CALIDAD_IPRES, EVALUADOR, EVALUADO)),
            Map.entry(AUTOEVALUACION_GESTIONAR, Set.of(ADMIN_SISTEMA, EVALUADOR)),
            Map.entry(EVIDENCIAS_REGISTRAR, Set.of(EVALUADO)),
            Map.entry(EVIDENCIAS_EVALUAR, Set.of(EVALUADOR)),
            Map.entry(DASHBOARD_GLOBAL, Set.of(ADMIN_SISTEMA, GESTOR_CALIDAD_SEDE)),
            Map.entry(DASHBOARD_RED, Set.of(ADMIN_SISTEMA, GESTOR_CALIDAD_SEDE, GESTOR_CALIDAD_RED)),
            Map.entry(DASHBOARD_IPRESS, Set.of(ADMIN_SISTEMA, GESTOR_CALIDAD_RED, GESTOR_CALIDAD_IPRES,
                    EVALUADOR, EVALUADO)));

    /** Permisos que otorgan estos roles. */
    public static Set<String> de(Collection<String> roles) {
        return ROLES_POR_PERMISO.entrySet().stream()
                .filter(e -> roles.stream().anyMatch(e.getValue()::contains))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Todos los permisos definidos (para la documentación y los tests). */
    public static Set<String> todos() {
        return ROLES_POR_PERMISO.keySet();
    }
}
