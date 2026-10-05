package com.essalud.samec.shared.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import com.essalud.samec.organizacion.Ipress;
import com.essalud.samec.organizacion.IpressRepository;
import com.essalud.samec.organizacion.MacroRegion;
import com.essalud.samec.organizacion.MacroRegionRepository;
import com.essalud.samec.organizacion.NivelComplejidad;
import com.essalud.samec.organizacion.Red;
import com.essalud.samec.organizacion.RedRepository;
import com.essalud.samec.security.PasswordHasher;
import com.essalud.samec.security.RoleSets;
import com.essalud.samec.security.token.TokenService;
import com.essalud.samec.usuarios.EstadoRegistro;
import com.essalud.samec.usuarios.Rol;
import com.essalud.samec.usuarios.RolRepository;
import com.essalud.samec.usuarios.Usuario;
import com.essalud.samec.usuarios.UsuarioRepository;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Datos base al arrancar. Idempotente y no destructivo: busca por código antes de crear, así que no
 * duplica ni pisa lo que se haya editado desde la aplicación.
 * <ul>
 *   <li>Roles (RoleSets), MacroRegiones, Redes e IPRESS reales de EsSalud (mismo maestro que Auditorías).</li>
 *   <li>Usuarios de prueba, uno por rol, solo si samec.seed.usuarios-demo=true (dev/test).</li>
 *   <li>Primer Administrador en producción, desde SAMEC_ADMIN_*, solo si no hay ninguno.</li>
 * </ul>
 */
@ApplicationScoped
public class DataInitializer {

    private static final Logger LOG = Logger.getLogger(DataInitializer.class);

    @Inject RolRepository roles;
    @Inject MacroRegionRepository macroRegiones;
    @Inject RedRepository redes;
    @Inject IpressRepository ipress;
    @Inject UsuarioRepository usuarios;
    @Inject PasswordHasher hasher;
    @Inject TokenService tokens;
    @Inject SamecConfig config;

    private static final String[][] MACROREGIONES = {
            { "NORTE", "Norte" }, { "CENTRO", "Centro" }, { "LIMA_ORIENTE", "Lima Oriente" }, { "SUR", "Sur" } };

    /** codigo, nombre, descripción, macroregión. */
    private static final String[][] REDES = {
            { "05", "RED ASISTENCIAL SABOGAL", "R.A. SABOGAL", "LIMA_ORIENTE" },
            { "06", "RED ASISTENCIAL ALMENARA", "R.A. ALMENARA", "LIMA_ORIENTE" },
            { "07", "RED ASISTENCIAL REBAGLIATI", "R.A. REBAGLIATI", "LIMA_ORIENTE" },
            { "08", "RED ASISTENCIAL TUMBES", "R.A. TUMBES", "NORTE" },
            { "09", "RED ASISTENCIAL PIURA", "R.A. PIURA", "NORTE" },
            { "10", "RED ASISTENCIAL LAMBAYEQUE", "R.A. LAMBAYEQUE", "NORTE" },
            { "12", "RED ASISTENCIAL CAJAMARCA", "R.A. CAJAMARCA", "NORTE" },
            { "13", "RED ASISTENCIAL AMAZONAS", "R.A. AMAZONAS", "NORTE" },
            { "15", "RED ASISTENCIAL LA LIBERTAD", "R.A. LA LIBERTAD", "NORTE" },
            { "16", "RED ASISTENCIAL ANCASH", "R.A. ANCASH", "NORTE" },
            { "17", "RED ASISTENCIAL HUARAZ", "R.A. HUARAZ", "LIMA_ORIENTE" },
            { "18", "RED ASISTENCIAL AREQUIPA", "R.A. AREQUIPA", "SUR" },
            { "20", "RED ASISTENCIAL MOQUEGUA", "R.A. MOQUEGUA", "SUR" },
            { "21", "RED ASISTENCIAL TACNA", "R.A. TACNA", "SUR" },
            { "22", "RED ASISTENCIAL PUNO", "R.A. PUNO", "SUR" },
            { "23", "RED ASISTENCIAL ICA", "R.A. ICA", "CENTRO" },
            { "24", "RED ASISTENCIAL CUSCO", "R.A. CUSCO", "SUR" },
            { "25", "RED ASISTENCIAL APURIMAC", "R.A. APURIMAC", "SUR" },
            { "26", "RED ASISTENCIAL MADRE DE DIOS", "R.A. MADRE DE DIOS", "SUR" },
            { "27", "RED ASISTENCIAL JUNIN", "R.A. JUNIN", "CENTRO" },
            { "28", "RED ASISTENCIAL PASCO", "R.A. PASCO", "CENTRO" },
            { "29", "RED ASISTENCIAL HUANUCO", "R.A. HUANUCO", "CENTRO" },
            { "30", "RED ASISTENCIAL AYACUCHO", "R.A. AYACUCHO", "CENTRO" },
            { "31", "RED ASISTENCIAL HUANCAVELICA", "R.A. HUANCAVELICA", "CENTRO" },
            { "32", "RED ASISTENCIAL LORETO", "R.A. LORETO", "LIMA_ORIENTE" },
            { "33", "RED ASISTENCIAL UCAYALI", "R.A. UCAYALI", "CENTRO" },
            { "34", "RED ASISTENCIAL JULIACA", "R.A. JULIACA", "SUR" },
            { "35", "MICRORED ASISTENCIAL TARAPOTO", "MR.A. TARAPOTO", "NORTE" },
            { "36", "MICRORED ASISTENCIAL MOYOBAMBA", "MR.A. MOYOBAMBA", "NORTE" },
            { "40", "RED ASISTENCIAL JAEN", "R.A. JAEN", "NORTE" } };

    /** Nombres legibles de los roles (la descripción larga está en RoleSets). */
    private static final Map<String, String> NOMBRES_ROL = Map.of(
            RoleSets.ADMIN_SISTEMA, "Administrador del sistema",
            RoleSets.GESTOR_CALIDAD_SEDE, "Gestor de Calidad de Sede Central",
            RoleSets.GESTOR_CALIDAD_RED, "Gestor de Calidad de Red",
            RoleSets.GESTOR_CALIDAD_IPRES, "Gestor de Calidad de IPRESS",
            RoleSets.EVALUADOR, "Evaluador",
            RoleSets.EVALUADO, "Evaluado");

    /** Usuarios de prueba (dev/test): username, nombre, apellido, rol. IPRESS 001 = H.N. Rebagliati (Red 07). */
    private static final String[][] USUARIOS_DEMO = {
            { "admin", "Admin", "Sistema", RoleSets.ADMIN_SISTEMA },
            { "gestor_sede", "Lucía", "Soto", RoleSets.GESTOR_CALIDAD_SEDE },
            { "gestor_red", "Elena", "Flores", RoleSets.GESTOR_CALIDAD_RED },
            { "gestor_ipres", "María", "Quispe", RoleSets.GESTOR_CALIDAD_IPRES },
            { "evaluador", "Juan", "Pérez", RoleSets.EVALUADOR },
            { "evaluado", "Roberto", "Huanca", RoleSets.EVALUADO } };
    private static final String DEMO_IPRESS = "001";
    private static final String DEMO_RED = "07";

    @Transactional
    void alArrancar(@Observes StartupEvent evento) {
        sembrarRoles();
        sembrarMacroRegiones();
        sembrarRedes();
        sembrarIpress();
        if (config.seed().usuariosDemo()) sembrarUsuariosDemo();
        crearAdminInicial();
        long vencidos = tokens.eliminarVencidos();
        if (vencidos > 0) LOG.infof("Refresh tokens vencidos eliminados: %d", vencidos);
    }

    private void sembrarRoles() {
        // Roles con código anterior: se renombran conservando sus usuarios.
        RoleSets.CODIGOS_ANTERIORES.forEach((anterior, actual) -> roles.porCodigo(anterior).ifPresent(rol -> {
            if (roles.porCodigo(actual).isPresent()) return;
            rol.setCodigo(actual);
            rol.setNombre(NOMBRES_ROL.get(actual));
            rol.setDescripcion(RoleSets.DESCRIPCIONES.get(actual));
            LOG.infof("Rol renombrado: %s → %s", anterior, actual);
        }));
        RoleSets.DESCRIPCIONES.forEach((codigo, descripcion) -> {
            if (roles.porCodigo(codigo).isPresent()) return;
            Rol rol = new Rol();
            rol.setCodigo(codigo);
            rol.setNombre(NOMBRES_ROL.getOrDefault(codigo, codigo));
            rol.setDescripcion(descripcion);
            roles.persist(rol);
            LOG.infof("Rol creado: %s", codigo);
        });
    }

    private void sembrarMacroRegiones() {
        for (String[] m : MACROREGIONES) {
            if (macroRegiones.porCodigo(m[0]).isPresent()) continue;
            MacroRegion mr = new MacroRegion();
            mr.setCodigo(m[0]);
            mr.setNombre(m[1]);
            macroRegiones.persist(mr);
        }
    }

    private void sembrarRedes() {
        int creadas = 0;
        for (String[] fila : REDES) {
            if (redes.porCodigo(fila[0]).isPresent()) continue;
            Red red = new Red();
            red.setCodigo(fila[0]);
            red.setNombre(fila[1]);
            red.setDescripcion(fila[2]);
            red.setMacroRegion(macroRegiones.porCodigo(fila[3]).orElse(null));
            redes.persist(red);
            creadas++;
        }
        if (creadas > 0) LOG.infof("Redes creadas: %d", creadas);
    }

    /** seed/ipress.csv: codigo|nombre|red_codigo|nivel|enabled (mismo maestro que Auditorías). */
    private void sembrarIpress() {
        if (ipress.count() > 0) return; // ya sembradas: altas posteriores se hacen desde la aplicación
        int creadas = 0;
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("seed/ipress.csv");
             BufferedReader lector = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            lector.readLine(); // encabezado
            String linea;
            while ((linea = lector.readLine()) != null) {
                if (linea.isBlank()) continue;
                String[] c = linea.split("\\|", -1);
                Red red = redes.porCodigo(c[2].trim()).orElse(null);
                if (red == null) {
                    LOG.warnf("IPRESS %s - %s: Red '%s' inexistente, se omite.", c[0], c[1], c[2]);
                    continue;
                }
                Ipress i = new Ipress();
                i.setCodigo(c[0].trim());
                i.setNombre(c[1].trim());
                i.setRed(red);
                i.setNivelComplejidad(NivelComplejidad.desde(c[3]));
                i.setActivo("1".equals(c[4].trim()));
                ipress.persist(i);
                creadas++;
            }
        } catch (IOException | NullPointerException e) {
            LOG.errorf("No se pudo leer seed/ipress.csv: %s", e.getMessage());
            return;
        }
        LOG.infof("IPRESS creadas: %d", creadas);
    }

    private void sembrarUsuariosDemo() {
        Ipress ipressDemo = ipress.porCodigo(DEMO_IPRESS).orElse(null);
        Red redDemo = redes.porCodigo(DEMO_RED).orElse(null);
        for (String[] d : USUARIOS_DEMO) {
            if (usuarios.porUsername(d[0]).isPresent()) continue;
            Usuario u = nuevoUsuario(d[0], d[1], d[2], d[0] + "@samec.local", config.seed().passwordDemo(), d[3]);
            if (RoleSets.IPRESS_SCOPE_ROLES.contains(d[3])) u.setIpress(ipressDemo);
            if (RoleSets.RED_SCOPE_ROLES.contains(d[3])) u.setRed(redDemo);
            usuarios.persist(u);
        }
        LOG.warn("Usuarios de prueba activos (samec.seed.usuarios-demo=true): no usar en producción.");
    }

    private void crearAdminInicial() {
        var admin = config.seed().adminInicial();
        if (admin.username().isEmpty() || admin.email().isEmpty() || admin.password().isEmpty()) return;
        if (usuarios.existeConRol(RoleSets.ADMIN_SISTEMA)) return;
        if (usuarios.porUsername(admin.username().get()).isPresent()) {
            LOG.warnf("Admin inicial: ya existe un usuario '%s' sin rol de administrador; no se crea.", admin.username().get());
            return;
        }
        usuarios.persist(nuevoUsuario(admin.username().get(), "Administrador", "SAMEC", admin.email().get(),
                admin.password().get(), RoleSets.ADMIN_SISTEMA));
        LOG.infof("Administrador inicial creado: %s. Cambie su contraseña tras el primer ingreso.", admin.username().get());
    }

    private Usuario nuevoUsuario(String username, String nombre, String apellido, String email, String password,
                                 String rol) {
        Usuario u = new Usuario();
        u.setUsername(username.trim().toLowerCase());
        u.setNombre(nombre);
        u.setApellido(apellido);
        u.setEmail(email.trim().toLowerCase());
        u.setPasswordHash(hasher.hash(password));
        u.setActivo(true);
        u.setAutoregistrado(false);
        u.setRegistroEstado(EstadoRegistro.APROBADO);
        Set<Rol> asignados = new HashSet<>();
        roles.porCodigo(rol).ifPresent(asignados::add);
        u.setRoles(asignados);
        return u;
    }
}
