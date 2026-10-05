package com.essalud.samec.auth;

import static com.essalud.samec.TestSupport.PASSWORD_DEMO;
import static com.essalud.samec.TestSupport.login;
import static com.essalud.samec.TestSupport.token;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import com.essalud.samec.TestSupport;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

@QuarkusTest
class AuthFlujoTest {

    private static final String COOKIE = AuthResource.COOKIE_REFRESH;

    @Test
    void loginCorrecto_devuelveTokenUsuarioConPermisosYCookieHttpOnly() {
        Response r = login("admin", PASSWORD_DEMO);

        r.then().statusCode(200)
                .body("tokenType", equalTo("Bearer"))
                .body("user.username", equalTo("admin"))
                .body("user.alcance", equalTo("NACIONAL"))
                .body("user.roles", hasItem("ADMIN_SISTEMA"))
                .body("user.permisos", hasItem("usuarios.crud"));
        assertThat(r.jsonPath().getString("accessToken")).isNotBlank();
        assertThat(r.getDetailedCookie(COOKIE).isHttpOnly()).isTrue();
        assertThat(r.getDetailedCookie(COOKIE).getValue()).isNotBlank();
    }

    @Test
    void usuarioInexistenteYContrasenaIncorrecta_respondenIgual() {
        String inexistente = login("no_existe_" + System.nanoTime(), "Cualquiera123").then()
                .statusCode(401).extract().path("message");
        String incorrecta = login("evaluado", "ClaveIncorrecta1").then()
                .statusCode(401).extract().path("message");
        assertThat(inexistente).isEqualTo(incorrecta).isEqualTo(AuthService.MSJ_CREDENCIALES);
    }

    @Test
    void cincoIntentosFallidos_bloqueanLaCuenta() {
        String usuario = "bloqueo_" + System.nanoTime();
        for (int i = 0; i < 4; i++) login(usuario, "Mala12345").then().statusCode(401);
        login(usuario, "Mala12345").then().statusCode(429).header("Retry-After", not(equalTo("0")));
        login(usuario, "Mala12345").then().statusCode(429).body("message", containsString("Demasiados intentos"));
    }

    @Test
    void endpointsProtegidos_exigenToken_yLosPublicosNo() {
        given().get("/auth/me").then().statusCode(401);
        given().get("/usuarios").then().statusCode(401);
        given().get("/macroregiones").then().statusCode(401);
        given().get("/redes").then().statusCode(200);
        given().get("/health").then().statusCode(200).body("status", equalTo("ok"));

        given().auth().oauth2(token("evaluador")).get("/auth/me").then().statusCode(200)
                .body("username", equalTo("evaluador"))
                .body("alcance", equalTo("IPRESS"))
                .body("ipressNombre", containsString("REBAGLIATI"));
    }

    @Test
    void refresh_rotaElToken_yReusarUnoRotadoCierraTodasLasSesiones() {
        String refresh1 = login("gestor_red", PASSWORD_DEMO).getCookie(COOKIE);

        Response renovado = given().cookie(COOKIE, refresh1).post("/auth/refresh");
        renovado.then().statusCode(200).body("user.username", equalTo("gestor_red"));
        String refresh2 = renovado.getCookie(COOKIE);
        assertThat(refresh2).isNotBlank().isNotEqualTo(refresh1);

        // El token ya rotado vuelve a aparecer: se trata como robo y se revocan todas las sesiones.
        given().cookie(COOKIE, refresh1).post("/auth/refresh").then().statusCode(401);
        given().cookie(COOKIE, refresh2).post("/auth/refresh").then().statusCode(401);
    }

    @Test
    void logout_revocaSoloEsaSesion() {
        String otraSesion = login("gestor_sede", PASSWORD_DEMO).getCookie(COOKIE);
        String refresh = login("gestor_sede", PASSWORD_DEMO).getCookie(COOKIE);
        given().cookie(COOKIE, refresh).post("/auth/logout").then().statusCode(200);
        given().cookie(COOKIE, refresh).post("/auth/refresh").then().statusCode(401);
        // Usar el token cerrado no es "robo": la otra sesión sigue viva.
        given().cookie(COOKIE, otraSesion).post("/auth/refresh").then().statusCode(200);
    }

    @Test
    void sinPermiso_respondeProhibidoConMensaje() {
        given().auth().oauth2(token("evaluador")).contentType(ContentType.JSON)
                .body("{\"username\":\"x\",\"nombre\":\"x\",\"apellido\":\"x\",\"email\":\"x@x.pe\",\"roles\":[\"EVALUADOR\"]}")
                .post("/usuarios")
                .then().statusCode(403)
                .body("message", equalTo("No tiene permisos para realizar esta acción."));
        given().auth().oauth2(token("evaluado")).get("/accesos").then().statusCode(403);
        given().auth().oauth2(token("admin")).get("/accesos").then().statusCode(200);
    }

    @Test
    void validacion_devuelveMensajesEnEspanol() {
        given().contentType(ContentType.JSON).body(new TestSupport.LoginBody("", ""))
                .post("/auth/login")
                .then().statusCode(400)
                .body("message", containsString("Debe indicar su usuario."));
    }
}
