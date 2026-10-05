package com.essalud.samec.usuarios;

import static com.essalud.samec.TestSupport.PASSWORD_DEMO;
import static com.essalud.samec.TestSupport.esperarCorreo;
import static com.essalud.samec.TestSupport.login;
import static com.essalud.samec.TestSupport.token;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.essalud.samec.shared.correo.CorreoRequest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

@QuarkusTest
class UsuarioReglasTest {

    @Inject
    @Any
    InMemoryConnector connector;

    InMemorySink<CorreoRequest> correos;

    @BeforeEach
    void limpiarCorreos() {
        correos = connector.sink("correos");
        correos.clear();
    }

    private static Map<String, Object> usuario(String username, List<String> roles, Long idRed, Long idIpress) {
        var body = new java.util.HashMap<String, Object>();
        body.put("username", username);
        body.put("nombre", "Nombre");
        body.put("apellido", "Apellido");
        body.put("email", username + "@samec.local");
        body.put("roles", roles);
        body.put("idRed", idRed);
        body.put("idIpress", idIpress);
        return body;
    }

    private static Long idIpressDe(String token) {
        return given().auth().oauth2(token).get("/auth/me").then().extract().jsonPath().getLong("idIpress");
    }

    private static Long otraIpress(Long distintaDe) {
        List<Integer> ids = given().get("/ipress").then().extract().jsonPath().getList("id");
        return ids.stream().map(Long::valueOf).filter(id -> !id.equals(distintaDe)).findFirst().orElseThrow();
    }

    @Test
    void gestorIpress_creaEvaluadoresSoloEnSuIpress_yConUnEnlaceParaFijarLaContrasena() {
        String gestor = token("gestor_ipres");
        Long suIpress = idIpressDe(gestor);
        String username = "eval_" + System.nanoTime();

        // Aunque mande otra IPRESS, el usuario queda en la suya.
        given().auth().oauth2(gestor).contentType(ContentType.JSON)
                .body(usuario(username, List.of("EVALUADOR"), null, otraIpress(suIpress)))
                .post("/usuarios")
                .then().statusCode(201)
                .body("idIpress", equalTo(suIpress.intValue()))
                .body("alcance", equalTo("IPRESS"));

        CorreoRequest correo = esperarCorreo(correos, c -> c.to().equals(username + "@samec.local"));
        assertThat(correo.templateName()).isEqualTo("new-user.html");
        assertThat((String) correo.variables().get("olvidePasswordUrl")).contains("usuario=" + username);
        assertThat(correo.variables()).doesNotContainKey("password");
    }

    @Test
    void gestorIpress_noPuedeAsignarRolesFueraDeSuNivel() {
        given().auth().oauth2(token("gestor_ipres")).contentType(ContentType.JSON)
                .body(usuario("red_" + System.nanoTime(), List.of("GESTOR_CALIDAD_RED"), 1L, null))
                .post("/usuarios")
                .then().statusCode(400)
                .body("message", containsString("No puede asignar el rol GESTOR_CALIDAD_RED"));
    }

    @Test
    void gestorIpress_soloVeUsuariosDeSuIpress_yElEvaluadorNoVeElListado() {
        String gestor = token("gestor_ipres");
        Long suIpress = idIpressDe(gestor);
        given().auth().oauth2(gestor).get("/usuarios").then().statusCode(200)
                .body("idIpress", everyItem(equalTo(suIpress.intValue())))
                .body("username", hasItem("evaluado"));

        given().auth().oauth2(token("evaluador")).get("/usuarios").then().statusCode(403);
    }

    @Test
    void sedeCentral_noPuedeAdministrarAlAdministrador() {
        Long idAdmin = given().auth().oauth2(token("admin")).get("/auth/me").then().extract().jsonPath().getLong("id");
        given().auth().oauth2(token("gestor_sede")).contentType(ContentType.JSON)
                .body(Map.of("activo", false))
                .patch("/usuarios/" + idAdmin + "/estado")
                .then().statusCode(403);
    }

    @Test
    void desactivarUnaCuenta_invalidaSuTokenDeInmediato() {
        String admin = token("admin");
        String username = "temporal_" + System.nanoTime();
        Long id = given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(usuario(username, List.of("GESTOR_CALIDAD_SEDE"), null, null))
                .post("/usuarios").then().statusCode(201).extract().jsonPath().getLong("id");

        // Fija su contraseña como lo haría el usuario: con el código que le llega por correo.
        given().contentType(ContentType.JSON).body(Map.of("username", username)).post("/auth/olvide-password")
                .then().statusCode(200);
        String otp = (String) esperarCorreo(correos, c -> c.to().equals(username + "@samec.local")
                && c.templateName().equals("password-reset-otp.html")).variables().get("otp");
        given().contentType(ContentType.JSON)
                .body(Map.of("username", username, "otp", otp, "password", "NuevaClave2026"))
                .post("/auth/restablecer-password").then().statusCode(200);

        String suToken = token(username, "NuevaClave2026");
        given().auth().oauth2(suToken).get("/auth/me").then().statusCode(200);

        given().auth().oauth2(admin).contentType(ContentType.JSON).body(Map.of("activo", false))
                .patch("/usuarios/" + id + "/estado").then().statusCode(200).body("activo", equalTo(false));

        given().auth().oauth2(suToken).get("/auth/me").then().statusCode(401);
        login(username, "NuevaClave2026").then().statusCode(403)
                .body("message", containsString("desactivada"));
    }

    @Test
    void nadiePuedeDesactivarseASiMismo() {
        String admin = token("admin");
        Long id = given().auth().oauth2(admin).get("/auth/me").then().extract().jsonPath().getLong("id");
        given().auth().oauth2(admin).contentType(ContentType.JSON).body(Map.of("activo", false))
                .patch("/usuarios/" + id + "/estado").then().statusCode(400);
    }

    @Test
    void autorregistro_quedaPendiente_ySedeCentralLoAprueba() {
        String username = "solicitante" + System.nanoTime();
        String email = username + "@essalud.gob.pe";
        Long idIpress = given().get("/ipress").then().extract().jsonPath().getLong("[0].id");

        var solicitud = Map.of("nombre", "Ana", "apellido", "Torres", "email", email,
                "password", "MiClave2026", "rol", "EVALUADOR", "idIpress", idIpress);
        given().contentType(ContentType.JSON).body(solicitud).post("/usuarios/autorregistro").then().statusCode(200);
        esperarCorreo(correos, c -> c.templateName().equals("registro-pendiente.html")
                && c.to().equals("gestor_sede@samec.local"));

        login(username, "MiClave2026").then().statusCode(403).body("message", containsString("pendiente"));

        String sede = token("gestor_sede");
        Long id = given().auth().oauth2(sede).queryParam("estado", "PENDIENTE").get("/usuarios/solicitudes")
                .then().statusCode(200)
                .extract().jsonPath().getLong("find { it.username == '" + username + "' }.id");
        given().auth().oauth2(sede).contentType(ContentType.JSON).body(Map.of("estado", "APROBADO"))
                .patch("/usuarios/" + id + "/registro-estado")
                .then().statusCode(200).body("registroEstado", equalTo("APROBADO"))
                .body("registroRevisadoPorNombre", equalTo("Lucía Soto"));
        esperarCorreo(correos, c -> c.templateName().equals("registro-resuelto.html") && c.to().equals(email));

        login(username, "MiClave2026").then().statusCode(200);
    }

    @Test
    void autorregistro_exigeCorreoInstitucional() {
        Long idIpress = given().get("/ipress").then().extract().jsonPath().getLong("[0].id");
        given().contentType(ContentType.JSON)
                .body(Map.of("nombre", "Ana", "apellido", "Torres", "email", "ana@gmail.com",
                        "password", "MiClave2026", "rol", "EVALUADOR", "idIpress", idIpress))
                .post("/usuarios/autorregistro")
                .then().statusCode(400).body("message", containsString("@essalud.gob.pe"));
    }

    @Test
    void cambiarMiPassword_exigeLaActual() {
        String username = "cambio_" + System.nanoTime();
        given().auth().oauth2(token("admin")).contentType(ContentType.JSON)
                .body(usuario(username, List.of("GESTOR_CALIDAD_SEDE"), null, null))
                .post("/usuarios").then().statusCode(201);
        given().contentType(ContentType.JSON).body(Map.of("username", username)).post("/auth/olvide-password");
        String otp = (String) esperarCorreo(correos, c -> c.to().equals(username + "@samec.local")
                && c.templateName().equals("password-reset-otp.html")).variables().get("otp");
        given().contentType(ContentType.JSON).body(Map.of("username", username, "otp", "00000000", "password", "Otra2026x"))
                .post("/auth/restablecer-password").then().statusCode(400);
        given().contentType(ContentType.JSON).body(Map.of("username", username, "otp", otp, "password", "Primera2026"))
                .post("/auth/restablecer-password").then().statusCode(200);

        String t = token(username, "Primera2026");
        given().auth().oauth2(t).contentType(ContentType.JSON)
                .body(Map.of("passwordActual", "Equivocada1", "passwordNueva", "Segunda2026"))
                .post("/usuarios/me/password").then().statusCode(400)
                .body("message", equalTo("La contraseña actual no es correcta."));
        given().auth().oauth2(t).contentType(ContentType.JSON)
                .body(Map.of("passwordActual", "Primera2026", "passwordNueva", "Segunda2026"))
                .post("/usuarios/me/password").then().statusCode(200);
        login(username, "Segunda2026").then().statusCode(200);
    }
}
