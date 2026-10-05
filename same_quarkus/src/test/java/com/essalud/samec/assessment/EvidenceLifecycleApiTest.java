package com.essalud.samec.assessment;

import static com.essalud.samec.TestSupport.token;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;

/**
 * The rules of the evidence and the evaluation that the happy path does not cover: what cannot be
 * uploaded, link-only evidence, details, withdrawal, on-site photos, scores out of place and the
 * close. Uses its own catalog (category I-2, year 2022) so it does not depend on other tests.
 */
@QuarkusTest
class EvidenceLifecycleApiTest {

    private static byte[] pdf(String text) {
        return ("%PDF-1.4\n" + text + "\n%%EOF").getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] jpeg(int seed) {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', (byte) seed};
    }

    private static long id(io.restassured.response.Response r) {
        return r.jsonPath().getLong("id");
    }

    @Test
    void evidenceAndEvaluationRules() {
        String admin = token("admin");
        String evaluator = token("evaluador");
        String evaluatee = token("evaluado");
        long facilityId = given().auth().oauth2(evaluatee).get("/auth/me").then().extract().jsonPath().getLong("idIpress");

        // ── Catalog: EVL1-1 (upload + institutional), EVL1-2 (on site), EVL1-3 (other category) ──
        long macroId = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("code", "EVL", "name", "Macro ciclo", "category", "SUPPORT", "scoreWeight", 2.5, "displayOrder", 50))
                .post("/catalog/macro-processes").then().statusCode(201).extract().response());
        long standardId = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("macroProcessId", macroId, "code", "EVL-1", "statement", "Estándar"))
                .post("/catalog/standards").then().statusCode(201).extract().response());
        JsonPath c1 = given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("standardId", standardId, "code", "EVL1-1", "statement", "Documentado", "type", "STRUCTURE",
                        "applicableCategories", List.of("I_2"),
                        "conditions", List.of(
                                Map.of("description", "Informe", "scoreWeight", 1, "technique", "DOCUMENT_REVIEW", "displayOrder", 1,
                                        "sources", List.of(Map.of("name", "Informe", "availability", "PROVIDED_BY_FACILITY", "displayOrder", 1))),
                                Map.of("description", "Directiva", "scoreWeight", 1, "technique", "DOCUMENT_REVIEW", "displayOrder", 2,
                                        "sources", List.of(Map.of("name", "Directiva", "availability", "PREDEFINED",
                                                "referenceUrl", "https://essalud.gob.pe/evl.pdf", "displayOrder", 1))))))
                .post("/catalog/criteria").then().statusCode(201).extract().jsonPath();
        long c1Id = c1.getLong("id");
        long uploadSource = c1.getLong("conditions[0].sources[0].id");
        long predefinedSource = c1.getLong("conditions[1].sources[0].id");
        long docConditionId = c1.getLong("conditions[0].id");
        JsonPath c2 = given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("standardId", standardId, "code", "EVL1-2", "statement", "En campo", "type", "PROCESS",
                        "applicableCategories", List.of("I_2"),
                        "conditions", List.of(Map.of("description", "Se observa", "scoreWeight", 2, "technique", "OBSERVATION",
                                "displayOrder", 1))))
                .post("/catalog/criteria").then().statusCode(201).extract().jsonPath();
        long c2Id = c2.getLong("id");
        long onSiteConditionId = c2.getLong("conditions[0].id");
        JsonPath c3 = given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("standardId", standardId, "code", "EVL1-3", "statement", "Otra categoría", "type", "STRUCTURE",
                        "applicableCategories", List.of("I_3"),
                        "conditions", List.of(Map.of("description", "Acta", "scoreWeight", 2, "technique", "DOCUMENT_REVIEW", "displayOrder", 1,
                                "sources", List.of(Map.of("name", "Acta", "availability", "PROVIDED_BY_FACILITY", "displayOrder", 1))))))
                .post("/catalog/criteria").then().statusCode(201).extract().jsonPath();
        long observationType = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("code", "EVL-ILEGIBLE", "name", "Documento ilegible"))
                .post("/catalog/observation-types").then().statusCode(201).extract().response());

        long assessmentId = id(given().auth().oauth2(evaluator).contentType(ContentType.JSON)
                .body(Map.of("facilityId", facilityId, "periodYear", 2022, "facilityCategory", "I_2"))
                .post("/self-assessments").then().statusCode(201).extract().response());
        String evidenceUrl = "/self-assessments/" + assessmentId + "/evidence";

        // ── What cannot be uploaded ──
        given().auth().oauth2(evaluatee).multiPart("auditSourceId", String.valueOf(predefinedSource))
                .multiPart("files", "d.pdf", pdf("d"), "application/pdf")
                .post(evidenceUrl).then().statusCode(400).body("message", containsString("no requiere evidencia"));
        given().auth().oauth2(evaluatee).multiPart("auditSourceId", String.valueOf(c3.getLong("conditions[0].sources[0].id")))
                .multiPart("files", "a.pdf", pdf("a"), "application/pdf")
                .post(evidenceUrl).then().statusCode(400).body("message", containsString("no aplica"));
        given().auth().oauth2(evaluatee).multiPart("auditSourceId", String.valueOf(uploadSource))
                .post(evidenceUrl).then().statusCode(400).body("message", containsString("al menos un archivo o un enlace"));
        RequestSpecification eleven = given().auth().oauth2(evaluatee).multiPart("auditSourceId", String.valueOf(uploadSource));
        for (int i = 0; i < 11; i++) eleven.multiPart("files", "f" + i + ".pdf", pdf("f" + i), "application/pdf");
        eleven.post(evidenceUrl).then().statusCode(400).body("message", containsString("hasta 10 archivos"));

        // ── Link-only evidence, its details, and withdrawal ──
        long linkOnly = id(given().auth().oauth2(evaluatee).multiPart("auditSourceId", String.valueOf(uploadSource))
                .multiPart("externalUrl", "https://drive.essalud.gob.pe/informe")
                .post(evidenceUrl).then().statusCode(201).body("files", hasSize(0)).extract().response());
        given().auth().oauth2(evaluatee).contentType(ContentType.JSON)
                .body(Map.of("externalUrl", "https://drive.essalud.gob.pe/informe-v2", "notes", "Versión firmada"))
                .patch("/evidence/" + linkOnly).then().statusCode(200).body("notes", equalTo("Versión firmada"));
        given().auth().oauth2(evaluatee).contentType(ContentType.JSON).body(Map.of("externalUrl", "", "notes", "x"))
                .patch("/evidence/" + linkOnly).then().statusCode(400).body("message", containsString("al menos un archivo o un enlace"));
        given().auth().oauth2(evaluatee).post("/evidence/" + linkOnly + "/resubmit").then().statusCode(400)
                .body("message", containsString("Solo se reenvía una evidencia observada"));
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("externalUrl", "https://x"))
                .patch("/evidence/" + linkOnly).then().statusCode(403);
        given().auth().oauth2(evaluatee).delete("/evidence/" + linkOnly).then().statusCode(204);
        given().auth().oauth2(evaluatee).get("/evidence/" + linkOnly).then().statusCode(404);

        // ── Upload again with a file; an approved evidence is frozen ──
        JsonPath evidence = given().auth().oauth2(evaluatee).multiPart("auditSourceId", String.valueOf(uploadSource))
                .multiPart("files", "informe.pdf", pdf("informe"), "application/pdf")
                .post(evidenceUrl).then().statusCode(201).extract().jsonPath();
        long evidenceId = evidence.getLong("id");
        long fileId = evidence.getLong("files[0].id");
        given().auth().oauth2(evaluatee).delete("/evidence/" + evidenceId + "/files/" + (fileId + 100_000))
                .then().statusCode(400).body("message", containsString("no pertenece"));
        // without a status the evaluator gets the review queue
        given().auth().oauth2(evaluator).get(evidenceUrl).then().statusCode(200)
                .body("content", hasSize(1)).body("content[0].id", equalTo((int) evidenceId));
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("verdict", "OBSERVED", "observationTypeId", 999_999))
                .post("/evidence/" + evidenceId + "/review").then().statusCode(400).body("message", containsString("tipo de observación"));
        given().auth().oauth2(evaluator).contentType(ContentType.JSON)
                .body(Map.of("verdict", "OBSERVED", "observationTypeId", observationType, "comment", "No se lee la firma"))
                .post("/evidence/" + evidenceId + "/review").then().statusCode(200).body("status", equalTo("OBSERVED"));
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("verdict", "APPROVED"))
                .post("/evidence/" + evidenceId + "/review").then().statusCode(400).body("message", containsString("pendiente de revisión"));
        given().auth().oauth2(evaluator).get(evidenceUrl + "?status=OBSERVED").then().statusCode(200)
                .body("content", hasSize(1)).body("content[0].reviews[0].observationTypeName", equalTo("Documento ilegible"));
        given().auth().oauth2(evaluatee).post("/evidence/" + evidenceId + "/resubmit").then().statusCode(200)
                .body("status", equalTo("PENDING_REVIEW"));
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("verdict", "APPROVED"))
                .post("/evidence/" + evidenceId + "/review").then().statusCode(200).body("status", equalTo("APPROVED"));
        given().auth().oauth2(evaluatee).delete("/evidence/" + evidenceId).then().statusCode(400).body("message", containsString("aprobada"));
        given().auth().oauth2(evaluatee).contentType(ContentType.JSON).body(Map.of("notes", "cambio"))
                .patch("/evidence/" + evidenceId).then().statusCode(400).body("message", containsString("aprobada"));

        // ── On-site verification with photos ──
        String reviewUrl = "/self-assessments/" + assessmentId + "/conditions/%d/review";
        given().auth().oauth2(evaluator).multiPart("met", "true")
                .put(reviewUrl.formatted(docConditionId)).then().statusCode(400).body("message", containsString("no en campo"));
        given().auth().oauth2(evaluatee).multiPart("met", "true")
                .put(reviewUrl.formatted(onSiteConditionId)).then().statusCode(403);
        JsonPath review = given().auth().oauth2(evaluator).multiPart("met", "true").multiPart("comment", "Cumple")
                .multiPart("files", "foto1.jpg", jpeg(1), "image/jpeg")
                .multiPart("files", "foto2.jpg", jpeg(2), "image/jpeg")
                .put(reviewUrl.formatted(onSiteConditionId)).then().statusCode(200).body("attachments", hasSize(2))
                .extract().jsonPath();
        long reviewId = review.getLong("id");
        long photo1 = review.getLong("attachments[0].id");
        given().auth().oauth2(evaluator).delete("/condition-reviews/" + reviewId + "/files/" + photo1).then().statusCode(200)
                .body("attachments", hasSize(1)).body("attachments[0].name", equalTo("foto2.jpg"));
        given().auth().oauth2(evaluator).delete("/condition-reviews/" + reviewId + "/files/" + photo1).then().statusCode(400)
                .body("message", containsString("no pertenece"));
        given().auth().oauth2(evaluatee).get("/files/" + photo1).then().statusCode(404);
        // a second verification keeps the photos already there
        given().auth().oauth2(evaluator).multiPart("met", "true").multiPart("files", "foto3.jpg", jpeg(3), "image/jpeg")
                .put(reviewUrl.formatted(onSiteConditionId)).then().statusCode(200).body("attachments", hasSize(2))
                .body("id", equalTo((int) reviewId));

        // ── Scores ──
        String scoreUrl = "/self-assessments/" + assessmentId + "/criteria/%d/score";
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("score", 2))
                .put(scoreUrl.formatted(c3.getLong("id"))).then().statusCode(400).body("message", containsString("no aplica"));
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("score", 3, "justification", "x"))
                .put(scoreUrl.formatted(c1Id)).then().statusCode(400);
        given().auth().oauth2(evaluatee).contentType(ContentType.JSON).body(Map.of("score", 2))
                .put(scoreUrl.formatted(c1Id)).then().statusCode(403);
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("score", 1, "justification", "Falta la firma del director"))
                .put(scoreUrl.formatted(c1Id)).then().statusCode(200).body("score", equalTo(1))
                .body("justification", equalTo("Falta la firma del director"));
        // scoring again replaces the score (one per criterion)
        long scoreId = given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("score", 2))
                .put(scoreUrl.formatted(c1Id)).then().statusCode(200).body("justification", nullValue())
                .extract().jsonPath().getLong("id");
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("score", 2))
                .put(scoreUrl.formatted(c1Id)).then().statusCode(200).body("id", equalTo((int) scoreId));

        // ── Close: every criterion must be scored ──
        String result = "/self-assessments/" + assessmentId + "/result";
        given().auth().oauth2(evaluator).get(result).then().statusCode(200)
                .body("complete", equalTo(false)).body("criteriaCount", equalTo(2)).body("scoredCount", equalTo(1));
        given().auth().oauth2(evaluator).post("/self-assessments/" + assessmentId + "/close").then().statusCode(400)
                .body("message", containsString("Faltan calificar 1"));
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("score", 2))
                .put(scoreUrl.formatted(c2Id)).then().statusCode(200);
        given().auth().oauth2(evaluator).post("/self-assessments/" + assessmentId + "/close").then().statusCode(200)
                .body("status", equalTo("CLOSED")).body("finalScore", equalTo(100.0f));

        // ── Closed: nothing changes any more ──
        given().auth().oauth2(evaluatee).multiPart("files", "x.pdf", pdf("x"), "application/pdf")
                .post("/evidence/" + evidenceId + "/files").then().statusCode(400).body("message", containsString("cerrada"));
        given().auth().oauth2(evaluator).post("/self-assessments/" + assessmentId + "/evidence-lock").then().statusCode(400)
                .body("message", containsString("cerrada"));
        given().auth().oauth2(evaluator).post("/self-assessments/" + assessmentId + "/close").then().statusCode(400);
        given().auth().oauth2(evaluatee).get(result).then().statusCode(200).body("finalScore", equalTo(100.0f))
                .body("classification", equalTo("APPROVED"));
    }
}
