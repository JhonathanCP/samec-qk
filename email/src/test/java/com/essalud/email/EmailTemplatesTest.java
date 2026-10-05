package com.essalud.email;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test puro (sin @SpringBootTest): app.mail.from exige la variable de entorno
 * EMAIL_USERNAME sin valor por defecto, así que levantar el contexto completo aquí fallaría
 * fuera de un despliegue real. Como lo único que hay que probar es que los templates Thymeleaf
 * del core de SAMEC (same_quarkus) parsean y renderizan bien con las variables
 * que ese controlador envía, alcanza con un TemplateEngine standalone apuntando a los mismos
 * templates/ del classpath — el mismo mecanismo que usa ExchangeSmtpEmailService.sendEmail en
 * producción.
 *
 * Se usa SpringStandardDialect (SpringEL) en vez del StandardDialect por defecto (OGNL): es el
 * dialecto que Spring Boot realmente conecta cuando arma el bean TemplateEngine vía
 * spring-boot-starter-thymeleaf, así que es el que reproduce fielmente el comportamiento en
 * producción — y evita depender de la librería externa "ognl", que este módulo nunca declara
 * como dependencia directa (solo llegaría de forma transitiva si Thymeleaf se usara sin Spring).
 */
class EmailTemplatesTest {

    private TemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        templateEngine.setDialect(new SpringStandardDialect());
        return templateEngine;
    }

    @Test
    void aprobado_muestraMensajeDeAprobacionYBotonDeLogin() {
        Context ctx = new Context();
        ctx.setVariable("nombre", "Juana");
        ctx.setVariable("apellido", "Pérez");
        ctx.setVariable("rolLabel", "Evaluador interno");
        ctx.setVariable("estadoLabel", "aprobada");
        ctx.setVariable("aprobado", true);
        ctx.setVariable("comentario", null);
        ctx.setVariable("appUrl", "http://localhost:4200");

        String html = engine().process("registro-resuelto.html", ctx);

        assertThat(html).contains("Juana");
        assertThat(html).contains("Evaluador interno");
        assertThat(html).contains("aprobada");
        assertThat(html).contains("Iniciar sesión");
        assertThat(html).contains("http://localhost:4200/auth/login");
    }

    @Test
    void rechazado_muestraComentarioYOmiteElBotonDeLogin() {
        Context ctx = new Context();
        ctx.setVariable("nombre", "Juana");
        ctx.setVariable("apellido", "Pérez");
        ctx.setVariable("rolLabel", "GESTOR CALIDAD IPRES");
        ctx.setVariable("estadoLabel", "rechazada");
        ctx.setVariable("aprobado", false);
        ctx.setVariable("comentario", "Datos insuficientes");
        ctx.setVariable("appUrl", "http://localhost:4200");

        String html = engine().process("registro-resuelto.html", ctx);

        assertThat(html).contains("rechazada");
        assertThat(html).contains("Datos insuficientes");
        assertThat(html).doesNotContain("Iniciar sesión");
    }

    @Test
    void rechazado_sinComentario_noRompeYNoMuestraElBloqueDeComentario() {
        Context ctx = new Context();
        ctx.setVariable("nombre", "Juana");
        ctx.setVariable("apellido", "Pérez");
        ctx.setVariable("rolLabel", "AUDITOR");
        ctx.setVariable("estadoLabel", "rechazada");
        ctx.setVariable("aprobado", false);
        ctx.setVariable("comentario", null);
        ctx.setVariable("appUrl", "http://localhost:4200");

        String html = engine().process("registro-resuelto.html", ctx);

        assertThat(html).contains("rechazada");
        assertThat(html).doesNotContain("<b>Comentario:</b>");
    }

    // ── registro-pendiente.html: aviso a Sede Central de una solicitud nueva ─────────────────

    @Test
    void solicitudPendiente_conIpresYRed_muestraAmbas() {
        Context ctx = new Context();
        ctx.setVariable("nombreSolicitante", "Juana Pérez");
        ctx.setVariable("emailSolicitante", "jperez@essalud.gob.pe");
        ctx.setVariable("rolLabel", "AUDITOR");
        ctx.setVariable("ipresNombre", "IPRESS Los Olivos");
        ctx.setVariable("redNombre", "Red Sabogal");
        ctx.setVariable("appUrl", "http://localhost:4200");

        String html = engine().process("registro-pendiente.html", ctx);

        assertThat(html).contains("Juana Pérez");
        assertThat(html).contains("jperez@essalud.gob.pe");
        assertThat(html).contains("IPRESS Los Olivos");
        assertThat(html).contains("Red Sabogal");
        assertThat(html).contains("http://localhost:4200/usuarios/solicitudes");
    }

    @Test
    void solicitudPendiente_gestorDeRed_sinIpres_noRompe() {
        Context ctx = new Context();
        ctx.setVariable("nombreSolicitante", "Carlos Ruiz");
        ctx.setVariable("emailSolicitante", "cruiz@essalud.gob.pe");
        ctx.setVariable("rolLabel", "GESTOR CALIDAD RED");
        ctx.setVariable("ipresNombre", null);
        ctx.setVariable("redNombre", "Red Almenara");
        ctx.setVariable("appUrl", "http://localhost:4200");

        String html = engine().process("registro-pendiente.html", ctx);

        assertThat(html).contains("Carlos Ruiz");
        assertThat(html).contains("Red Almenara");
        assertThat(html).doesNotContain("IPRESS</td>");
    }

    // ── password-reset-otp.html: código de "olvidé mi contraseña" ────────────────────────────

    @Test
    void passwordResetOtp_muestraNombreYCodigo() {
        Context ctx = new Context();
        ctx.setVariable("nombre", "Juana Pérez");
        ctx.setVariable("otp", "12345678");
        ctx.setVariable("minutos", 5);

        String html = engine().process("password-reset-otp.html", ctx);

        assertThat(html).contains("Juana Pérez");
        assertThat(html).contains("12345678");
        assertThat(html).contains("5 minutos");
        assertThat(html).contains("SAMEC");
    }

    // ── new-user / reset-password / change-password: ningún correo lleva la contraseña ───────

    @Test
    void cuentaCreada_noIncluyeContrasenaYEnlazaAEstablecerla() {
        Context ctx = new Context();
        ctx.setVariable("username", "jperez");
        ctx.setVariable("password", "NoDebeAparecer123");
        ctx.setVariable("appUrl", "http://localhost:4200");
        ctx.setVariable("olvidePasswordUrl", "http://localhost:4200/auth/olvide-password?usuario=jperez");

        String html = engine().process("new-user.html", ctx);

        assertThat(html).contains("jperez");
        assertThat(html).doesNotContain("NoDebeAparecer123");
        assertThat(html).contains("http://localhost:4200/auth/olvide-password?usuario=jperez");
    }

    @Test
    void contrasenaRestablecida_noIncluyeContrasenaYEnlazaAEstablecerla() {
        Context ctx = new Context();
        ctx.setVariable("username", "jperez");
        ctx.setVariable("password", "NoDebeAparecer123");
        ctx.setVariable("appUrl", "http://localhost:4200");
        ctx.setVariable("olvidePasswordUrl", "http://localhost:4200/auth/olvide-password?usuario=jperez");

        String html = engine().process("reset-password.html", ctx);

        assertThat(html).doesNotContain("NoDebeAparecer123");
        assertThat(html).contains("http://localhost:4200/auth/olvide-password?usuario=jperez");
    }

    @Test
    void contrasenaCambiada_noIncluyeContrasena() {
        Context ctx = new Context();
        ctx.setVariable("username", "jperez");
        ctx.setVariable("password", "NoDebeAparecer123");
        ctx.setVariable("appUrl", "http://localhost:4200");
        ctx.setVariable("olvidePasswordUrl", "http://localhost:4200/auth/olvide-password?usuario=jperez");

        String html = engine().process("change-password.html", ctx);

        assertThat(html).contains("jperez");
        assertThat(html).doesNotContain("NoDebeAparecer123");
    }

    // ── notificacion.html: plantilla genérica para los avisos del core ───────────────────────

    @Test
    void notificacion_conDetalleYBoton() {
        Context ctx = new Context();
        ctx.setVariable("titulo", "Autoevaluación enviada");
        ctx.setVariable("nombre", "Roberto Huanca");
        ctx.setVariable("mensaje", "La autoevaluación 2026 quedó pendiente de su revisión.");
        ctx.setVariable("detalle", new java.util.LinkedHashMap<>(java.util.Map.of("IPRESS", "H.N. REBAGLIATI")));
        ctx.setVariable("textoBoton", "Revisar");
        ctx.setVariable("ruta", "/autoevaluaciones/1");
        ctx.setVariable("appUrl", "http://localhost:4200");

        String html = engine().process("notificacion.html", ctx);

        assertThat(html).contains("Autoevaluación enviada");
        assertThat(html).contains("Roberto Huanca");
        assertThat(html).contains("H.N. REBAGLIATI");
        assertThat(html).contains("http://localhost:4200/autoevaluaciones/1");
    }

    @Test
    void notificacion_minima_sinOpcionales() {
        Context ctx = new Context();
        ctx.setVariable("titulo", "Aviso");
        ctx.setVariable("mensaje", "Texto");
        ctx.setVariable("appUrl", "http://localhost:4200");

        String html = engine().process("notificacion.html", ctx);

        assertThat(html).contains("Aviso");
        assertThat(html).doesNotContain("Hola");
    }
}
