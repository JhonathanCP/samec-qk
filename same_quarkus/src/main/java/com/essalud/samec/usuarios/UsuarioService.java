package com.essalud.samec.usuarios;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.essalud.samec.organizacion.Ipress;
import com.essalud.samec.organizacion.IpressRepository;
import com.essalud.samec.organizacion.Red;
import com.essalud.samec.organizacion.RedRepository;
import com.essalud.samec.security.Alcance;
import com.essalud.samec.security.PasswordHasher;
import com.essalud.samec.security.RecaptchaService;
import com.essalud.samec.security.RoleSets;
import com.essalud.samec.security.UsuarioActual;
import com.essalud.samec.security.token.TokenService;
import com.essalud.samec.shared.bitacora.BitacoraService;
import com.essalud.samec.shared.config.SamecConfig;
import com.essalud.samec.shared.correo.CorreoPublisher;
import com.essalud.samec.shared.correo.CorreoRequest;
import com.essalud.samec.shared.error.AccesoDenegadoException;
import com.essalud.samec.shared.error.RecursoNoEncontradoException;
import com.essalud.samec.shared.error.ReglaNegocioException;
import com.essalud.samec.usuarios.dto.UsuarioDtos.AutorregistroRequest;
import com.essalud.samec.usuarios.dto.UsuarioDtos.RolDto;
import com.essalud.samec.usuarios.dto.UsuarioDtos.UsuarioDto;
import com.essalud.samec.usuarios.dto.UsuarioDtos.UsuarioRequest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Administración de usuarios con las reglas de alcance de Auditorías:
 * <ul>
 *   <li>Admin y Sede Central ven y administran a todo el país (Sede, salvo a un Administrador);</li>
 *   <li>el Gestor de Red, a los de las IPRESS de su Red; el Gestor de IPRESS, a los de su IPRESS;</li>
 *   <li>qué roles puede asignar cada uno está en {@link RoleSets#ADMINISTRA};</li>
 *   <li>nadie se activa/desactiva/restablece a sí mismo ni cambia sus propios roles.</li>
 * </ul>
 * Las cuentas nuevas no reciben contraseña por correo: les llega un enlace para fijarla con el
 * código de "¿Olvidó su contraseña?".
 */
@ApplicationScoped
public class UsuarioService {

    public static final String BITACORA = "USUARIO";

    @Inject UsuarioRepository usuarios;
    @Inject RolRepository roles;
    @Inject RedRepository redes;
    @Inject IpressRepository ipressRepo;
    @Inject PasswordHasher hasher;
    @Inject PoliticaPassword politica;
    @Inject TokenService tokens;
    @Inject CorreoPublisher correos;
    @Inject RecaptchaService recaptcha;
    @Inject BitacoraService bitacora;
    @Inject UsuarioActual actual;
    @Inject SamecConfig config;

    // ══ Consulta ════════════════════════════════════════════════════════════════════════════

    @Transactional
    public List<UsuarioDto> listarVisibles() {
        if (!actual.tieneAlgunRol(RoleSets.VEN_USUARIOS))
            throw new AccesoDenegadoException("No tiene acceso al listado de usuarios.");
        List<Usuario> lista = switch (actual.alcance()) {
            case NACIONAL -> usuarios.todos();
            case RED -> actual.idRed() == null ? List.of() : usuarios.deRed(actual.idRed());
            case IPRESS -> actual.idIpress() == null ? List.of() : usuarios.deIpress(actual.idIpress());
        };
        return lista.stream().map(UsuarioDto::de).toList();
    }

    @Transactional
    public UsuarioDto obtener(Long id) {
        Usuario u = buscar(id);
        boolean esElMismo = u.getId().equals(actual.id());
        if (!esElMismo && !(actual.tieneAlgunRol(RoleSets.VEN_USUARIOS) && dentroDeAlcance(u)))
            throw new AccesoDenegadoException("No tiene acceso a este usuario.");
        return UsuarioDto.de(u);
    }

    /** Roles que el usuario de la sesión puede asignar (para el formulario). */
    @Transactional
    public List<RolDto> rolesAsignables() {
        Set<String> codigos = RoleSets.administrablesPor(actual.roles());
        return roles.porCodigos(codigos).stream()
                .sorted(Comparator.comparingInt(r -> RoleSets.orden(r.getCodigo())))
                .map(RolDto::de)
                .toList();
    }

    // ══ Administración ══════════════════════════════════════════════════════════════════════

    @Transactional
    public UsuarioDto crear(UsuarioRequest req) {
        Set<Rol> rolesNuevos = validarRolesAsignables(req.roles());
        Ubicacion ubicacion = resolverUbicacion(RoleSets.alcanceDe(req.roles()), req.idRed(), req.idIpress());

        String username = req.username().trim().toLowerCase();
        String email = req.email().trim().toLowerCase();
        validarUnicos(username, email, null);

        Usuario u = new Usuario();
        u.setUsername(username);
        u.setPasswordHash(hasher.hash(PasswordHasher.aleatoria()));
        u.setNombre(req.nombre().trim());
        u.setApellido(req.apellido().trim());
        u.setEmail(email);
        u.setRed(ubicacion.red());
        u.setIpress(ubicacion.ipress());
        u.setRoles(rolesNuevos);
        u.setActivo(true);
        u.setAutoregistrado(false);
        u.setRegistroEstado(EstadoRegistro.APROBADO);
        usuarios.persist(u);

        bitacora.registrar(BITACORA, u.getId(), "CREAR", "Roles: " + String.join(", ", u.codigosRol()));
        correos.enviar(new CorreoRequest(u.getEmail(), "Tu cuenta en SAMEC ha sido creada", "new-user.html",
                variablesCuenta(u)));
        return UsuarioDto.de(u);
    }

    @Transactional
    public UsuarioDto actualizar(Long id, UsuarioRequest req) {
        Usuario u = buscar(id);
        boolean esElMismo = u.getId().equals(actual.id());
        String email = req.email().trim().toLowerCase();
        validarUnicos(null, email, u.getId());

        if (esElMismo) {
            // Puede corregir sus datos, pero no darse ni quitarse roles ni cambiar su IPRESS/Red.
            Ubicacion propia = new Ubicacion(u.getRed(), u.getIpress());
            if (!new HashSet<>(req.roles()).equals(u.codigosRol())
                    || !Objects.equals(req.idIpress(), propia.idIpress()) || !Objects.equals(req.idRed(), propia.idRed()))
                throw new ReglaNegocioException("No puede cambiar sus propios roles ni su IPRESS o Red.");
        } else {
            validarPuedeAdministrar(u);
            Set<Rol> rolesNuevos = validarRolesAsignables(req.roles());
            Ubicacion ubicacion = resolverUbicacion(RoleSets.alcanceDe(req.roles()), req.idRed(), req.idIpress());
            u.setRoles(rolesNuevos);
            u.setRed(ubicacion.red());
            u.setIpress(ubicacion.ipress());
        }
        u.setNombre(req.nombre().trim());
        u.setApellido(req.apellido().trim());
        u.setEmail(email);

        bitacora.registrar(BITACORA, u.getId(), "MODIFICAR", "Roles: " + String.join(", ", u.codigosRol()));
        return UsuarioDto.de(u);
    }

    /** Desactivada, la persona no puede iniciar sesión y sus sesiones abiertas se cierran. */
    @Transactional
    public UsuarioDto cambiarEstado(Long id, boolean activo) {
        Usuario u = buscar(id);
        if (u.getId().equals(actual.id()))
            throw new ReglaNegocioException("No puede activar ni desactivar su propia cuenta.");
        validarPuedeAdministrar(u);
        validarSolicitudResuelta(u);

        u.setActivo(activo);
        if (!activo) tokens.revocarTodos(u.getId());
        bitacora.registrar(BITACORA, u.getId(), activo ? "ACTIVAR" : "DESACTIVAR", null);
        return UsuarioDto.de(u);
    }

    /** Invalida la contraseña actual y le envía el enlace para fijar una nueva. */
    @Transactional
    public void restablecerPassword(Long id) {
        Usuario u = buscar(id);
        if (u.getId().equals(actual.id()))
            throw new ReglaNegocioException("Para cambiar su propia contraseña use la opción Cambiar contraseña.");
        validarPuedeAdministrar(u);
        validarSolicitudResuelta(u);

        u.setPasswordHash(hasher.hash(PasswordHasher.aleatoria()));
        tokens.revocarTodos(u.getId());
        bitacora.registrar(BITACORA, u.getId(), "RESTABLECER_PASSWORD", null);
        correos.enviar(new CorreoRequest(u.getEmail(), "Tu contraseña de SAMEC ha sido restablecida",
                "reset-password.html", variablesCuenta(u)));
    }

    /** Cambio de la propia contraseña: exige la actual y cierra las demás sesiones. */
    @Transactional
    public void cambiarMiPassword(String passwordActual, String passwordNueva, String refreshTokenActual) {
        Usuario u = buscar(actual.id());
        if (!hasher.verificar(passwordActual, u.getPasswordHash()))
            throw new ReglaNegocioException("La contraseña actual no es correcta.");
        if (passwordActual.equals(passwordNueva))
            throw new ReglaNegocioException("La nueva contraseña debe ser distinta de la actual.");
        politica.validar(passwordNueva);

        u.setPasswordHash(hasher.hash(passwordNueva));
        tokens.revocarTodosExcepto(u.getId(), refreshTokenActual);
        correos.enviar(new CorreoRequest(u.getEmail(), "Tu contraseña de SAMEC ha sido cambiada",
                "change-password.html", variablesCuenta(u)));
    }

    // ══ Autorregistro ═══════════════════════════════════════════════════════════════════════

    /** Solicitud pública: queda PENDIENTE (sin poder entrar) hasta que Sede Central la revise. */
    @Transactional
    public void autorregistrar(AutorregistroRequest req) {
        if (!recaptcha.verificar(req.recaptchaToken()))
            throw new ReglaNegocioException("No se pudo verificar reCAPTCHA. Intente nuevamente.");
        if (!RoleSets.SELF_REGISTER_ROLES.contains(req.rol()))
            throw new ReglaNegocioException("El rol seleccionado no está disponible para autorregistro.");

        // El formulario fija el dominio, pero quien llame a la API directo podría mandar cualquiera.
        String dominio = config.registro().dominioCorreo();
        String email = req.email().trim().toLowerCase();
        String[] partes = email.split("@");
        if (partes.length != 2 || partes[0].isBlank() || !dominio.equals(partes[1]))
            throw new ReglaNegocioException("Debe indicar un correo institucional válido (@" + dominio + ").");
        politica.validar(req.password());

        Ubicacion ubicacion = resolverUbicacionAutorregistro(req);
        Rol rol = roles.porCodigo(req.rol())
                .orElseThrow(() -> new ReglaNegocioException("El rol indicado no existe."));

        // Un correo ya usado es un rechazo, salvo que sea la misma solicitud ya RECHAZADA: ahí se
        // reintenta sobre la misma fila en vez de dejar a la persona bloqueada para siempre.
        Usuario existente = usuarios.porEmail(email).orElse(null);
        if (existente != null && (!Boolean.TRUE.equals(existente.getAutoregistrado())
                || existente.getRegistroEstado() == EstadoRegistro.APROBADO))
            throw new ReglaNegocioException("Ya existe una cuenta registrada con ese correo institucional.");
        if (existente != null && existente.getRegistroEstado() == EstadoRegistro.PENDIENTE)
            throw new ReglaNegocioException("Ya tiene una solicitud pendiente de revisión con ese correo institucional.");

        // El usuario SIEMPRE se deriva del correo (lo anterior a la @), nunca del payload.
        String username = partes[0];
        usuarios.porUsername(username)
                .filter(otro -> existente == null || !otro.getId().equals(existente.getId()))
                .ifPresent(otro -> {
                    throw new ReglaNegocioException("Ya existe una cuenta con el usuario '" + username
                            + "'. Contacte al administrador del sistema.");
                });

        Usuario u = existente != null ? existente : new Usuario();
        u.setUsername(username);
        u.setPasswordHash(hasher.hash(req.password()));
        u.setNombre(req.nombre().trim());
        u.setApellido(req.apellido().trim());
        u.setEmail(email);
        u.setActivo(false);
        u.setAutoregistrado(true);
        u.setRegistroEstado(EstadoRegistro.PENDIENTE);
        u.setRegistroComentario(null);
        u.setRegistroRevisadoPor(null);
        u.setRegistroRevisadoPorNombre(null);
        u.setRegistroRevisadoAt(null);
        u.setRegistroSolicitadoAt(LocalDateTime.now());
        u.setRed(ubicacion.red());
        u.setIpress(ubicacion.ipress());
        u.setRoles(new HashSet<>(Set.of(rol)));
        if (existente == null) usuarios.persist(u);

        notificarSolicitudNueva(u, rol);
    }

    @Transactional
    public List<UsuarioDto> solicitudes(EstadoRegistro estado, Long idRed, Long idIpress) {
        return usuarios.list("autoregistrado = true order by registroSolicitadoAt desc").stream()
                .filter(u -> estado == null || estado == u.getRegistroEstado())
                .filter(u -> idRed == null || (u.redEfectiva() != null && idRed.equals(u.redEfectiva().getId())))
                .filter(u -> idIpress == null || (u.getIpress() != null && idIpress.equals(u.getIpress().getId())))
                .map(UsuarioDto::de)
                .toList();
    }

    /** Aprobar habilita el inicio de sesión; rechazar lo impide (la persona puede volver a solicitar). */
    @Transactional
    public UsuarioDto resolverSolicitud(Long id, EstadoRegistro estado, String comentario) {
        Usuario u = buscar(id);
        if (!Boolean.TRUE.equals(u.getAutoregistrado()))
            throw new ReglaNegocioException("Este usuario no proviene de una solicitud de autorregistro.");
        if (estado == EstadoRegistro.PENDIENTE)
            throw new ReglaNegocioException("El estado debe ser APROBADO o RECHAZADO.");
        if (u.getRegistroEstado() != EstadoRegistro.PENDIENTE)
            throw new ReglaNegocioException("La solicitud ya fue resuelta.");

        boolean aprobado = estado == EstadoRegistro.APROBADO;
        u.setRegistroEstado(estado);
        u.setRegistroComentario(comentario != null && !comentario.isBlank() ? comentario.strip() : null);
        u.setRegistroRevisadoPor(actual.id());
        u.setRegistroRevisadoPorNombre(actual.nombreCompleto());
        u.setRegistroRevisadoAt(LocalDateTime.now());
        u.setActivo(aprobado);
        bitacora.registrar(BITACORA, u.getId(), aprobado ? "APROBAR_REGISTRO" : "RECHAZAR_REGISTRO", u.getRegistroComentario());

        Map<String, Object> vars = new HashMap<>();
        vars.put("nombre", u.getNombre());
        vars.put("apellido", u.getApellido());
        vars.put("rolLabel", etiquetaRoles(u));
        vars.put("estadoLabel", aprobado ? "aprobada" : "rechazada");
        vars.put("aprobado", aprobado);
        vars.put("comentario", u.getRegistroComentario());
        vars.put("appUrl", config.appUrl());
        correos.enviar(new CorreoRequest(u.getEmail(), "Tu solicitud de registro en SAMEC fue "
                + (aprobado ? "aprobada" : "rechazada"), "registro-resuelto.html", vars));
        return UsuarioDto.de(u);
    }

    // ══ Reglas ══════════════════════════════════════════════════════════════════════════════

    private Usuario buscar(Long id) {
        Usuario u = id == null ? null : usuarios.findById(id);
        if (u == null) throw RecursoNoEncontradoException.de("Usuario", id);
        return u;
    }

    /** ¿El usuario objetivo está dentro del alcance de datos del usuario de la sesión? */
    private boolean dentroDeAlcance(Usuario objetivo) {
        return switch (actual.alcance()) {
            case NACIONAL -> true;
            case RED -> objetivo.redEfectiva() != null && objetivo.redEfectiva().getId().equals(actual.idRed());
            case IPRESS -> objetivo.getIpress() != null && objetivo.getIpress().getId().equals(actual.idIpress());
        };
    }

    /** Puede administrarlo si todos sus roles son administrables por él y está en su alcance. */
    private void validarPuedeAdministrar(Usuario objetivo) {
        Set<String> administrables = RoleSets.administrablesPor(actual.roles());
        if (administrables.isEmpty())
            throw new AccesoDenegadoException("No tiene permiso para administrar usuarios.");
        if (!administrables.containsAll(objetivo.codigosRol()))
            throw new AccesoDenegadoException("No puede administrar a un usuario con el rol "
                    + String.join(", ", objetivo.codigosRol()) + ".");
        if (!dentroDeAlcance(objetivo))
            throw new AccesoDenegadoException(actual.alcance() == Alcance.RED
                    ? "Solo puede administrar usuarios de las IPRESS de su Red."
                    : "Solo puede administrar usuarios de su propia IPRESS.");
    }

    private Set<Rol> validarRolesAsignables(List<String> codigos) {
        Set<String> administrables = RoleSets.administrablesPor(actual.roles());
        for (String codigo : codigos) {
            if (!administrables.contains(codigo))
                throw new ReglaNegocioException("No puede asignar el rol " + codigo + ".");
        }
        List<Rol> encontrados = roles.porCodigos(new HashSet<>(codigos));
        if (encontrados.size() != new HashSet<>(codigos).size())
            throw new ReglaNegocioException("Alguno de los roles indicados no existe o está inactivo.");
        return new HashSet<>(encontrados);
    }

    private record Ubicacion(Red red, Ipress ipress) {
        Long idRed() {
            return red != null ? red.getId() : (ipress != null ? ipress.getRed().getId() : null);
        }

        Long idIpress() {
            return ipress != null ? ipress.getId() : null;
        }
    }

    /**
     * Red/IPRESS del usuario según el alcance de sus roles, acotadas al alcance de quien lo crea:
     * un Gestor de IPRESS solo crea en su IPRESS y un Gestor de Red solo en IPRESS de su Red.
     */
    private Ubicacion resolverUbicacion(Alcance alcanceRoles, Long idRed, Long idIpress) {
        return switch (alcanceRoles) {
            case NACIONAL -> new Ubicacion(null, null);
            case RED -> {
                if (idRed == null) throw new ReglaNegocioException("Debe seleccionar la Red del usuario.");
                if (actual.alcance() != Alcance.NACIONAL && !idRed.equals(actual.idRed()))
                    throw new AccesoDenegadoException("Solo puede asignar su propia Red.");
                Red red = redes.findByIdOptional(idRed)
                        .orElseThrow(() -> new ReglaNegocioException("La Red seleccionada no existe."));
                yield new Ubicacion(red, null);
            }
            case IPRESS -> {
                Long idDestino = actual.alcance() == Alcance.IPRESS ? actual.idIpress() : idIpress;
                if (idDestino == null) throw new ReglaNegocioException("Debe seleccionar la IPRESS del usuario.");
                Ipress ipress = ipressRepo.findByIdOptional(idDestino)
                        .orElseThrow(() -> new ReglaNegocioException("La IPRESS seleccionada no existe."));
                if (actual.alcance() == Alcance.RED && !ipress.getRed().getId().equals(actual.idRed()))
                    throw new AccesoDenegadoException("La IPRESS seleccionada no pertenece a su Red.");
                yield new Ubicacion(null, ipress);
            }
        };
    }

    private Ubicacion resolverUbicacionAutorregistro(AutorregistroRequest req) {
        if (RoleSets.RED_SCOPE_ROLES.contains(req.rol())) {
            if (req.idRed() == null) throw new ReglaNegocioException("Debe seleccionar su Red Asistencial.");
            Red red = redes.findByIdOptional(req.idRed())
                    .filter(Red::getActivo)
                    .orElseThrow(() -> new ReglaNegocioException("La Red seleccionada no existe."));
            return new Ubicacion(red, null);
        }
        if (req.idIpress() == null) throw new ReglaNegocioException("Debe seleccionar su IPRESS.");
        Ipress ipress = ipressRepo.findByIdOptional(req.idIpress())
                .filter(Ipress::getActivo)
                .orElseThrow(() -> new ReglaNegocioException("La IPRESS seleccionada no existe."));
        if (req.idRed() != null && !ipress.getRed().getId().equals(req.idRed()))
            throw new ReglaNegocioException("La IPRESS seleccionada no pertenece a la Red indicada.");
        return new Ubicacion(null, ipress);
    }

    private void validarUnicos(String username, String email, Long idActual) {
        if (username != null) usuarios.porUsername(username)
                .filter(otro -> !otro.getId().equals(idActual))
                .ifPresent(otro -> {
                    throw new ReglaNegocioException("Ya existe un usuario con el nombre de usuario '" + username + "'.");
                });
        usuarios.porEmail(email)
                .filter(otro -> !otro.getId().equals(idActual))
                .ifPresent(otro -> {
                    throw new ReglaNegocioException("Ya existe un usuario con el correo '" + email + "'.");
                });
    }

    /**
     * Una cuenta de autorregistro PENDIENTE o RECHAZADA se resuelve en Solicitudes de Registro:
     * desde Usuarios no se puede activar ni restablecer (se saltaría ese flujo).
     */
    private static void validarSolicitudResuelta(Usuario u) {
        if (Boolean.TRUE.equals(u.getAutoregistrado()) && u.getRegistroEstado() != EstadoRegistro.APROBADO)
            throw new ReglaNegocioException("La cuenta tiene una solicitud de registro "
                    + (u.getRegistroEstado() == EstadoRegistro.RECHAZADO ? "rechazada" : "pendiente")
                    + ": resuélvala en Solicitudes de Registro.");
    }

    private void notificarSolicitudNueva(Usuario solicitante, Rol rol) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("nombreSolicitante", solicitante.nombreCompleto());
        vars.put("emailSolicitante", solicitante.getEmail());
        vars.put("rolLabel", rol.getNombre());
        vars.put("ipresNombre", solicitante.getIpress() != null ? solicitante.getIpress().getNombre() : null);
        vars.put("redNombre", solicitante.redEfectiva() != null ? solicitante.redEfectiva().getNombre() : null);
        vars.put("appUrl", config.appUrl());
        for (String rolRevisor : RoleSets.REVISAN_SOLICITUDES) {
            for (Usuario revisor : usuarios.activosConRol(rolRevisor)) {
                correos.enviar(new CorreoRequest(revisor.getEmail(),
                        "Nueva solicitud de registro en SAMEC — " + solicitante.nombreCompleto(),
                        "registro-pendiente.html", vars));
            }
        }
    }

    private Map<String, Object> variablesCuenta(Usuario u) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("username", u.getUsername());
        vars.put("nombre", u.nombreCompleto());
        vars.put("appUrl", config.appUrl());
        vars.put("olvidePasswordUrl", config.appUrl() + "/auth/olvide-password?usuario="
                + URLEncoder.encode(u.getUsername(), StandardCharsets.UTF_8));
        return vars;
    }

    private static String etiquetaRoles(Usuario u) {
        return u.getRoles().stream().map(Rol::getNombre).sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }
}
