package com.essalud.samec.assessment;

import static com.essalud.samec.TestSupport.token;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * The lock of the evaluatee's document upload (candado) beyond the basic case: who may use it,
 * which criteria get the automatic score, what the evaluatee can no longer do, that manual scores
 * survive the reopening, and that it shows in the views. Category III-1, year 2021.
 */
@QuarkusTest
class EvidenceLockApiTest {

    private static final byte[] PDF = "%PDF-1.4\nacta\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final String NODE = "macroProcesses.standards.flatten().criteria.flatten().find { it.code == '%s' }";

    private static long id(io.restassured.response.Response r) {
        return r.jsonPath().getLong("id");
    }

    private static JsonPath criterion(String admin, long standardId, String code, Map<String, Object> condition) {
        return given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("standardId", standardId, "code", code, "statement", "Criterio " + code, "type", "STRUCTURE",
                        "applicableCategories", List.of("III_1"), "conditions", List.of(condition)))
                .post("/catalog/criteria").then().statusCode(201).extract().jsonPath();
    }

    private static Map<String, Object> upload(String name) {
        return Map.of("description", name, "scoreWeight", 2, "technique", "DOCUMENT_REVIEW", "displayOrder", 1,
                "sources", List.of(Map.of("name", name, "availability", "PROVIDED_BY_FACILITY", "displayOrder", 1)));
    }

    @Test
    void lockRules() {
        String admin = token("admin");
        String evaluator = token("evaluador");
        String evaluatee = token("evaluado");
        long facilityId = given().auth().oauth2(evaluatee).get("/auth/me").then().extract().jsonPath().getLong("idIpress");

        long macroId = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("code", "LKX", "name", "Macro candado 2", "category", "CARE_PROVISION", "scoreWeight", 7, "displayOrder", 60))
                .post("/catalog/macro-processes").then().statusCode(201).extract().response());
        long standardId = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("macroProcessId", macroId, "code", "LKX-1", "statement", "Estándar"))
                .post("/catalog/standards").then().statusCode(201).extract().response());
        JsonPath observed = criterion(admin, standardId, "LKX1-1", upload("Informe observado"));     // gets evidence, observed
        JsonPath empty = criterion(admin, standardId, "LKX1-2", upload("Acta nunca subida"));         // no evidence → automatic 0
        JsonPath onSite = criterion(admin, standardId, "LKX1-3", Map.of("description", "Entrevista", "scoreWeight", 2,
                "technique", "INTERVIEW", "displayOrder", 1));                                       // on site: never automatic
        criterion(admin, standardId, "LKX1-4", Map.of("description", "Directiva", "scoreWeight", 2, "technique", "DOCUMENT_REVIEW",
                "displayOrder", 1, "sources", List.of(Map.of("name", "Directiva", "availability", "PREDEFINED",
                        "referenceUrl", "https://essalud.gob.pe/lkx.pdf", "displayOrder", 1))));   // pre-scored at start
        long observationType = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("code", "LKX-VENCIDO", "name", "Documento vencido"))
                .post("/catalog/observation-types").then().statusCode(201).extract().response());

        long assessmentId = id(given().auth().oauth2(evaluator).contentType(ContentType.JSON)
                .body(Map.of("facilityId", facilityId, "periodYear", 2021, "facilityCategory", "III_1"))
                .post("/self-assessments").then().statusCode(201).extract().response());
        String base = "/self-assessments/" + assessmentId;
        long evidenceId = id(given().auth().oauth2(evaluatee)
                .multiPart("auditSourceId", String.valueOf(observed.getLong("conditions[0].sources[0].id")))
                .multiPart("files", "informe.pdf", PDF, "application/pdf")
                .post(base + "/evidence").then().statusCode(201).extract().response());
        given().auth().oauth2(evaluator).contentType(ContentType.JSON)
                .body(Map.of("verdict", "OBSERVED", "observationTypeId", observationType))
                .post("/evidence/" + evidenceId + "/review").then().statusCode(200);

        // Before closing, the missing upload is still pending (no suggestion)
        JsonPath checklist = given().auth().oauth2(evaluator).get(base + "/checklist").then().extract().jsonPath();
        assertThat(checklist.getString(NODE.formatted("LKX1-2") + ".conditions[0].status")).isEqualTo("PENDING");
        assertThat(checklist.getString(NODE.formatted("LKX1-2") + ".suggestedScore")).isNull();

        // Only the evaluator holds the key (not even the admin), and only while it is open
        given().auth().oauth2(admin).post(base + "/evidence-lock").then().statusCode(403);
        given().auth().oauth2(evaluator).delete(base + "/evidence-lock").then().statusCode(400)
                .body("message", containsString("ya está abierta"));
        given().auth().oauth2(evaluator).post(base + "/evidence-lock").then().statusCode(200)
                .body("autoScored", equalTo(1))
                .body("assessment.evidenceLockedAt", notNullValue());
        given().auth().oauth2(evaluator).post(base + "/evidence-lock").then().statusCode(400)
                .body("message", containsString("ya está cerrada"));

        // Every view tells it
        given().auth().oauth2(evaluatee).get(base).then().statusCode(200)
                .body("evidenceLocked", equalTo(true)).body("evidenceLockedByName", containsString("Juan"));
        given().auth().oauth2(evaluatee).get("/self-assessments?year=2021").then().statusCode(200)
                .body("content.find { it.id == " + assessmentId + " }.evidenceLocked", equalTo(true));

        // The evaluatee cannot correct the observed evidence any more
        given().auth().oauth2(evaluatee).post("/evidence/" + evidenceId + "/resubmit").then().statusCode(400)
                .body("message", containsString("carga documental del evaluado está cerrada"));
        given().auth().oauth2(evaluatee).contentType(ContentType.JSON).body(Map.of("notes", "corregido"))
                .patch("/evidence/" + evidenceId).then().statusCode(400);
        long fileId = given().auth().oauth2(evaluatee).get("/evidence/" + evidenceId).then().extract().jsonPath().getLong("files[0].id");
        given().auth().oauth2(evaluatee).delete("/evidence/" + evidenceId + "/files/" + fileId).then().statusCode(400);
        given().auth().oauth2(evaluatee).get("/files/" + fileId).then().statusCode(200); // still readable

        // Automatic score only for the document criterion without any evidence
        checklist = given().auth().oauth2(evaluator).get(base + "/checklist").then().extract().jsonPath();
        assertThat(checklist.getInt(NODE.formatted("LKX1-2") + ".score.score")).isZero();
        assertThat(checklist.getString(NODE.formatted("LKX1-2") + ".score.origin")).isEqualTo("PRE_SCORED");
        assertThat(checklist.getString(NODE.formatted("LKX1-1") + ".score")).isNull();         // it has (observed) evidence
        assertThat(checklist.getInt(NODE.formatted("LKX1-1") + ".suggestedScore")).isZero();   // observed and no longer correctable
        assertThat(checklist.getString(NODE.formatted("LKX1-3") + ".score")).isNull();         // on site
        assertThat(checklist.getInt(NODE.formatted("LKX1-4") + ".score.score")).isEqualTo(2);  // institutional, from the start

        // The evaluator keeps working: verifies on site and overrides the automatic score
        given().auth().oauth2(evaluator).multiPart("met", "true")
                .put(base + "/conditions/" + onSite.getLong("conditions[0].id") + "/review").then().statusCode(200);
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("score", 2, "justification", "Acta vista en físico"))
                .put(base + "/criteria/" + empty.getLong("id") + "/score").then().statusCode(200).body("origin", equalTo("MANUAL"));

        // Reopening keeps the evaluator's manual score
        given().auth().oauth2(evaluator).delete(base + "/evidence-lock").then().statusCode(200)
                .body("evidenceLocked", equalTo(false)).body("evidenceLockedByName", equalTo(null));
        checklist = given().auth().oauth2(evaluator).get(base + "/checklist").then().extract().jsonPath();
        assertThat(checklist.getInt(NODE.formatted("LKX1-2") + ".score.score")).isEqualTo(2);
        assertThat(checklist.getString(NODE.formatted("LKX1-2") + ".score.origin")).isEqualTo("MANUAL");

        // Closing again finds nothing new to score; the evaluatee can correct once reopened
        given().auth().oauth2(evaluator).post(base + "/evidence-lock").then().statusCode(200).body("autoScored", equalTo(0));
        given().auth().oauth2(evaluator).delete(base + "/evidence-lock").then().statusCode(200);
        given().auth().oauth2(evaluatee).post("/evidence/" + evidenceId + "/resubmit").then().statusCode(200)
                .body("status", equalTo("PENDING_REVIEW"));
    }
}
