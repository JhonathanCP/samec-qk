package com.essalud.samec;

import static io.restassured.RestAssured.given;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

import com.essalud.samec.shared.correo.CorreoRequest;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.smallrye.reactive.messaging.memory.InMemorySink;

/** Utilidades de los tests de integración (usuarios de prueba sembrados por DataInitializer). */
public final class TestSupport {

    /** samec.seed.password-demo por defecto. */
    public static final String PASSWORD_DEMO = "Samec#Dev2026";

    private TestSupport() {
    }

    public static Response login(String username, String password) {
        return given().contentType(ContentType.JSON)
                .body(new LoginBody(username, password))
                .post("/auth/login");
    }

    /** Access token de un usuario de prueba. */
    public static String token(String username) {
        return token(username, PASSWORD_DEMO);
    }

    public static String token(String username, String password) {
        Response r = login(username, password);
        if (r.statusCode() != 200) {
            throw new AssertionError("Login de " + username + " falló: " + r.statusCode() + " " + r.asString());
        }
        return r.jsonPath().getString("accessToken");
    }

    /** Espera (hasta 5 s) a que llegue al canal en memoria un correo que cumpla la condición. */
    public static CorreoRequest esperarCorreo(InMemorySink<CorreoRequest> sink, Predicate<CorreoRequest> condicion) {
        long limite = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < limite) {
            List<CorreoRequest> recibidos = sink.received().stream().map(m -> m.getPayload()).toList();
            for (CorreoRequest c : recibidos) {
                if (condicion.test(c)) return c;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("No llegó el correo esperado. Recibidos: "
                + sink.received().stream().map(m -> m.getPayload().templateName() + "→" + m.getPayload().to()).toList());
    }

    public record LoginBody(String username, String password) {
    }
}
