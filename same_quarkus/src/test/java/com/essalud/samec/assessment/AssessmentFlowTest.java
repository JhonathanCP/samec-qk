package com.essalud.samec.assessment;

import static com.essalud.samec.TestSupport.token;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/**
 * The whole core through the REST API with real access tokens: the admin loads the LEA catalog,
 * the evaluator opens the self-assessment, the evaluatee uploads evidence (several files), the
 * evaluator observes and approves it, verifies on site, scores and closes. Also checks what each
 * role must NOT be able to do.
 */
@QuarkusTest
class AssessmentFlowTest {

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF_2 = "%PDF-1.7\nsegundo documento\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    private static long id(io.restassured.response.Response r) {
        return r.jsonPath().getLong("id");
    }

    @Test
    void fullSelfAssessmentFlow() {
        String admin = token("admin");
        String evaluator = token("evaluador");
        String evaluatee = token("evaluado");
        long facilityId = given().auth().oauth2(evaluatee).get("/auth/me").then().statusCode(200)
                .extract().jsonPath().getLong("idIpress");

        // ── 1. Admin loads the catalog ──
        long macroId = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("code", "E2E-DIR", "name", "Direccionamiento", "category", "MANAGEMENT",
                        "scoreWeight", 8, "displayOrder", 1))
                .post("/catalog/macro-processes").then().statusCode(201).extract().response());
        long standardId = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("macroProcessId", macroId, "code", "E2E-DIR-1", "statement", "Planeamiento estratégico"))
                .post("/catalog/standards").then().statusCode(201).extract().response());
        long normId = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("title", "E2E NTS 050", "url", "https://www.gob.pe/nts-050"))
                .post("/catalog/normative-documents").then().statusCode(201).extract().response());
        long wrongYearId = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("code", "E2E-WRONG-YEAR", "name", "Documento de otro año"))
                .post("/catalog/observation-types").then().statusCode(201).extract().response());

        JsonPath criterion = given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("standardId", standardId, "code", "E2E-DIR-1-1", "statement", "Cuenta con plan estratégico",
                        "type", "PROCESS", "applicableCategories", List.of("II_1"),
                        "scoreLevels", List.of(Map.of("score", 0, "description", "No cuenta"),
                                Map.of("score", 1, "description", "Cuenta pero no lo difunde"),
                                Map.of("score", 2, "description", "Cuenta y lo difunde")),
                        "conditions", List.of(
                                Map.of("description", "Plan aprobado", "scoreWeight", 1, "technique", "DOCUMENT_REVIEW",
                                        "displayOrder", 1,
                                        "normativeReferences", List.of(Map.of("documentId", normId, "applicableExcerpt", "6.2")),
                                        "sources", List.of(Map.of("name", "Plan aprobado con resolución",
                                                "availability", "PROVIDED_BY_FACILITY", "displayOrder", 1))),
                                Map.of("description", "El personal conoce el plan", "scoreWeight", 1,
                                        "technique", "INTERVIEW", "displayOrder", 2))))
                .post("/catalog/criteria").then().statusCode(201).extract().jsonPath();
        long criterionId = criterion.getLong("id");
        long sourceId = criterion.getLong("conditions[0].sources[0].id");
        long interviewId = criterion.getLong("conditions[1].id");

        long predefinedCriterionId = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("standardId", standardId, "code", "E2E-DIR-1-2", "statement", "Directiva institucional",
                        "type", "STRUCTURE", "applicableCategories", List.of("II_1"),
                        "conditions", List.of(Map.of("description", "Directiva vigente", "scoreWeight", 2,
                                "technique", "DOCUMENT_REVIEW", "displayOrder", 1,
                                "sources", List.of(Map.of("name", "Directiva EsSalud", "availability", "PREDEFINED",
                                        "referenceUrl", "https://essalud.gob.pe/directiva.pdf", "displayOrder", 1))))))
                .post("/catalog/criteria").then().statusCode(201).extract().response());

        // ── 2. Evaluator opens the self-assessment of their facility ──
        int year = Year.now().getValue();
        JsonPath started = given().auth().oauth2(evaluator).contentType(ContentType.JSON)
                .body(Map.of("facilityId", facilityId, "periodYear", year, "facilityCategory", "II_1"))
                .post("/self-assessments").then().statusCode(201).extract().jsonPath();
        long assessmentId = started.getLong("id");
        assertThat(started.getString("status")).isEqualTo("OPEN");
        assertThat(started.getString("facilityName")).isNotBlank();
        given().auth().oauth2(evaluator).contentType(ContentType.JSON)
                .body(Map.of("facilityId", facilityId, "periodYear", year, "facilityCategory", "II_1"))
                .post("/self-assessments").then().statusCode(400).body("message", containsString("ya tiene"));

        // ── 3. Evaluatee uploads two files for the source ──
        JsonPath submitted = given().auth().oauth2(evaluatee)
                .multiPart("auditSourceId", String.valueOf(sourceId))
                .multiPart("notes", "Plan 2026")
                .multiPart("files", "plan.pdf", PDF, "application/pdf")
                .multiPart("files", "resolucion.pdf", PDF_2, "application/pdf")
                .post("/self-assessments/" + assessmentId + "/evidence").then().statusCode(201).extract().jsonPath();
        long evidenceId = submitted.getLong("id");
        assertThat(submitted.getList("files.name")).containsExactly("plan.pdf", "resolucion.pdf");
        assertThat(submitted.getString("status")).isEqualTo("PENDING_REVIEW");
        long planFileId = submitted.getLong("files[0].id");

        // a second submission for the same source is rejected; so is a renamed executable
        given().auth().oauth2(evaluatee).multiPart("auditSourceId", String.valueOf(sourceId))
                .multiPart("files", "otro.pdf", PDF, "application/pdf")
                .post("/self-assessments/" + assessmentId + "/evidence").then().statusCode(400)
                .body("message", containsString("Ya registró evidencia"));
        given().auth().oauth2(evaluatee).multiPart("files", "falso.pdf", new byte[] {'M', 'Z', 0, 0}, "application/pdf")
                .post("/evidence/" + evidenceId + "/files").then().statusCode(400)
                .body("message", containsString("no corresponde"));
        given().auth().oauth2(evaluator).multiPart("auditSourceId", String.valueOf(sourceId))
                .multiPart("files", "otro.pdf", PDF, "application/pdf")
                .post("/self-assessments/" + assessmentId + "/evidence").then().statusCode(403);

        // ── 4. Evaluator observes it ──
        given().auth().oauth2(evaluator).get("/self-assessments/" + assessmentId + "/evidence?status=PENDING_REVIEW")
                .then().statusCode(200).body("content", hasSize(1)).body("content[0].criterionCode", equalTo("E2E-DIR-1-1"));
        given().auth().oauth2(evaluatee).contentType(ContentType.JSON).body(Map.of("verdict", "APPROVED"))
                .post("/evidence/" + evidenceId + "/review").then().statusCode(403);
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("verdict", "OBSERVED"))
                .post("/evidence/" + evidenceId + "/review").then().statusCode(400);
        given().auth().oauth2(evaluator).contentType(ContentType.JSON)
                .body(Map.of("verdict", "OBSERVED", "observationTypeId", wrongYearId, "comment", "El plan es de 2024"))
                .post("/evidence/" + evidenceId + "/review").then().statusCode(200).body("status", equalTo("OBSERVED"));

        // ── 5. Evaluatee replaces a file and resubmits ──
        given().auth().oauth2(evaluatee).multiPart("files", "plan-2026.pdf", PDF_2, "application/pdf")
                .post("/evidence/" + evidenceId + "/files").then().statusCode(200).body("files", hasSize(3));
        given().auth().oauth2(evaluatee).delete("/evidence/" + evidenceId + "/files/" + planFileId)
                .then().statusCode(200).body("files.name", equalTo(List.of("resolucion.pdf", "plan-2026.pdf")));
        given().auth().oauth2(evaluatee).post("/evidence/" + evidenceId + "/resubmit")
                .then().statusCode(200).body("status", equalTo("PENDING_REVIEW"));

        // ── 6. Evaluator approves, verifies the interview on site and scores ──
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("verdict", "APPROVED"))
                .post("/evidence/" + evidenceId + "/review").then().statusCode(200)
                .body("status", equalTo("APPROVED")).body("reviews", hasSize(2))
                .body("reviews[0].observationTypeName", equalTo("Documento de otro año"));
        given().auth().oauth2(evaluatee).multiPart("files", "otro.pdf", PDF, "application/pdf")
                .post("/evidence/" + evidenceId + "/files").then().statusCode(400).body("message", containsString("aprobada"));

        long photoId = given().auth().oauth2(evaluator)
                .multiPart("met", "true").multiPart("comment", "Tres trabajadores lo conocen")
                .multiPart("files", "foto.jpg", JPEG, "image/jpeg")
                .put("/self-assessments/" + assessmentId + "/conditions/" + interviewId + "/review")
                .then().statusCode(200).body("met", equalTo(true)).body("attachments", hasSize(1))
                .extract().jsonPath().getLong("attachments[0].id");

        JsonPath checklist = given().auth().oauth2(evaluatee).get("/self-assessments/" + assessmentId + "/checklist")
                .then().statusCode(200).extract().jsonPath();
        String node = "macroProcesses.standards.flatten().criteria.flatten().find { it.code == '%s' }";
        assertThat(checklist.getInt(node.formatted("E2E-DIR-1-1") + ".suggestedScore")).isEqualTo(2);
        assertThat(checklist.getString(node.formatted("E2E-DIR-1-2") + ".score.origin")).isEqualTo("PRE_SCORED");
        assertThat(checklist.getInt("progress.sourcesApproved")).isEqualTo(1);
        assertThat(checklist.getInt("progress.onSiteConditionsReviewed")).isEqualTo(1);

        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("score", 1))
                .put("/self-assessments/" + assessmentId + "/criteria/" + criterionId + "/score")
                .then().statusCode(400).body("message", containsString("sugerido es 2"));
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("score", 2))
                .put("/self-assessments/" + assessmentId + "/criteria/" + criterionId + "/score")
                .then().statusCode(200).body("origin", equalTo("MANUAL")).body("scoredByName", containsString("Juan"));

        // ── 7. Files: the evaluatee downloads them from the database ──
        long resolutionId = given().auth().oauth2(evaluatee).get("/evidence/" + evidenceId).then().statusCode(200)
                .extract().jsonPath().getLong("files[0].id");
        byte[] downloaded = given().auth().oauth2(evaluatee).get("/files/" + resolutionId).then().statusCode(200)
                .header("Content-Type", containsString("application/pdf"))
                .header("Content-Disposition", containsString("resolucion.pdf"))
                .extract().asByteArray();
        assertThat(downloaded).isEqualTo(PDF_2);
        given().auth().oauth2(evaluatee).get("/files/" + photoId).then().statusCode(200);
        given().auth().oauth2(evaluatee).get("/files/" + planFileId).then().statusCode(404); // deleted with step 5

        // ── 8. Result and close ──
        given().auth().oauth2(evaluatee).get("/self-assessments/" + assessmentId + "/result").then().statusCode(200)
                .body("complete", equalTo(true)).body("finalScore", equalTo(100.0f))
                .body("classification", equalTo("APPROVED"));
        given().auth().oauth2(evaluatee).post("/self-assessments/" + assessmentId + "/close").then().statusCode(403);
        given().auth().oauth2(evaluator).post("/self-assessments/" + assessmentId + "/close").then().statusCode(200)
                .body("status", equalTo("CLOSED")).body("finalScore", equalTo(100.0f))
                .body("classificationLabel", containsString("Aprobado"));
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("score", 0, "justification", "x"))
                .put("/self-assessments/" + assessmentId + "/criteria/" + predefinedCriterionId + "/score")
                .then().statusCode(400).body("message", containsString("cerrada"));

        // ── 9. The catalog in use cannot be deleted ──
        given().auth().oauth2(admin).delete("/catalog/criteria/" + criterionId).then().statusCode(409)
                .body("message", containsString("autoevaluaciones"));
    }

    @Test
    void tokenAndPermissionsAreRequired() {
        given().get("/self-assessments").then().statusCode(401);
        given().get("/catalog/criteria").then().statusCode(401);
        given().get("/files/1").then().statusCode(401);

        // reading the catalog: any user with a session; changing it: only the catalog permission
        given().auth().oauth2(token("evaluado")).get("/catalog/macro-processes").then().statusCode(200);
        given().auth().oauth2(token("evaluador")).contentType(ContentType.JSON)
                .body(Map.of("code", "X", "name", "X", "category", "SUPPORT", "scoreWeight", 1, "displayOrder", 1))
                .post("/catalog/macro-processes").then().statusCode(403);
        // only the evaluator and the admin start an assessment
        given().auth().oauth2(token("evaluado")).contentType(ContentType.JSON)
                .body(Map.of("facilityId", 1, "periodYear", 2030, "facilityCategory", "II_1"))
                .post("/self-assessments").then().statusCode(403);
    }

    @Test
    void facilityUsersOnlySeeTheirOwnFacility() {
        String admin = token("admin");
        String evaluatee = token("evaluado");
        long ownFacility = given().auth().oauth2(evaluatee).get("/auth/me").then().extract().jsonPath().getLong("idIpress");
        List<Integer> facilities = given().get("/ipress").then().extract().jsonPath().getList("id");
        long otherFacility = facilities.stream().mapToLong(Integer::longValue).filter(id -> id != ownFacility).findFirst()
                .orElseThrow();

        ensureCatalogForCategory(admin, "I_1");
        long otherAssessment = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("facilityId", otherFacility, "periodYear", 2025, "facilityCategory", "I_1"))
                .post("/self-assessments").then().statusCode(201).extract().response());

        given().auth().oauth2(evaluatee).get("/self-assessments/" + otherAssessment).then().statusCode(403);
        given().auth().oauth2(evaluatee).get("/self-assessments/" + otherAssessment + "/checklist").then().statusCode(403);
        given().auth().oauth2(evaluatee).get("/self-assessments?facilityId=" + otherFacility).then().statusCode(403);
        given().auth().oauth2(token("evaluador")).contentType(ContentType.JSON)
                .body(Map.of("facilityId", otherFacility, "periodYear", 2024, "facilityCategory", "I_1"))
                .post("/self-assessments").then().statusCode(403);
        // without filter a facility user gets only their own facility
        given().auth().oauth2(evaluatee).get("/self-assessments").then().statusCode(200)
                .body("content.findAll { it.facilityId != " + ownFacility + " }", hasSize(0));
        given().auth().oauth2(admin).get("/self-assessments?facilityId=" + otherFacility).then().statusCode(200)
                .body("content", hasSize(1));
    }

    /**
     * The evaluator closes the document upload (candado, as in samec-old): the evaluatee can no longer
     * change evidence, the evaluator keeps reviewing, criteria with no evidence are scored automatically,
     * and reopening undoes those scores.
     */
    @Test
    void evaluatorClosesTheDocumentUpload() {
        String admin = token("admin");
        String evaluator = token("evaluador");
        String evaluatee = token("evaluado");
        long facilityId = given().auth().oauth2(evaluatee).get("/auth/me").then().extract().jsonPath().getLong("idIpress");

        long macroId = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("code", "LCK", "name", "Macro candado", "category", "MANAGEMENT", "scoreWeight", 5, "displayOrder", 40))
                .post("/catalog/macro-processes").then().statusCode(201).extract().response());
        long standardId = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("macroProcessId", macroId, "code", "LCK-1", "statement", "Estándar"))
                .post("/catalog/standards").then().statusCode(201).extract().response());
        // C1: one source the facility uploads
        JsonPath c1 = given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("standardId", standardId, "code", "LCK1-1", "statement", "Con sustento", "type", "STRUCTURE",
                        "applicableCategories", List.of("I_4"),
                        "conditions", List.of(Map.of("description", "Plan", "scoreWeight", 2, "technique", "DOCUMENT_REVIEW",
                                "displayOrder", 1, "sources", List.of(Map.of("name", "Plan", "availability", "PROVIDED_BY_FACILITY", "displayOrder", 1))))))
                .post("/catalog/criteria").then().statusCode(201).extract().jsonPath();
        // C2: an institutional source (weight 1) and one the facility never uploads (weight 1)
        JsonPath c2 = given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("standardId", standardId, "code", "LCK1-2", "statement", "Sin sustento", "type", "STRUCTURE",
                        "applicableCategories", List.of("I_4"),
                        "conditions", List.of(
                                Map.of("description", "Directiva", "scoreWeight", 1, "technique", "DOCUMENT_REVIEW", "displayOrder", 1,
                                        "sources", List.of(Map.of("name", "Directiva", "availability", "PREDEFINED",
                                                "referenceUrl", "https://essalud.gob.pe/d.pdf", "displayOrder", 1))),
                                Map.of("description", "Acta", "scoreWeight", 1, "technique", "DOCUMENT_REVIEW", "displayOrder", 2,
                                        "sources", List.of(Map.of("name", "Acta", "availability", "PROVIDED_BY_FACILITY", "displayOrder", 1))))))
                .post("/catalog/criteria").then().statusCode(201).extract().jsonPath();

        long assessmentId = id(given().auth().oauth2(evaluator).contentType(ContentType.JSON)
                .body(Map.of("facilityId", facilityId, "periodYear", 2023, "facilityCategory", "I_4"))
                .post("/self-assessments").then().statusCode(201).extract().response());
        long evidenceId = id(given().auth().oauth2(evaluatee).multiPart("auditSourceId", String.valueOf(c1.getLong("conditions[0].sources[0].id")))
                .multiPart("files", "plan.pdf", PDF, "application/pdf")
                .post("/self-assessments/" + assessmentId + "/evidence").then().statusCode(201).extract().response());

        // Only the evaluator holds the key
        given().auth().oauth2(evaluatee).post("/self-assessments/" + assessmentId + "/evidence-lock").then().statusCode(403);
        given().auth().oauth2(evaluator).post("/self-assessments/" + assessmentId + "/evidence-lock").then().statusCode(200)
                .body("autoScored", equalTo(1))
                .body("assessment.evidenceLocked", equalTo(true))
                .body("assessment.evidenceLockedByName", containsString("Juan"));

        // The evaluatee can no longer upload or change anything
        given().auth().oauth2(evaluatee).multiPart("auditSourceId", String.valueOf(c2.getLong("conditions[1].sources[0].id")))
                .multiPart("files", "acta.pdf", PDF, "application/pdf")
                .post("/self-assessments/" + assessmentId + "/evidence").then().statusCode(400)
                .body("message", containsString("cerrada por el evaluador"));
        given().auth().oauth2(evaluatee).multiPart("files", "otro.pdf", PDF_2, "application/pdf")
                .post("/evidence/" + evidenceId + "/files").then().statusCode(400);
        given().auth().oauth2(evaluatee).delete("/evidence/" + evidenceId).then().statusCode(400);

        // The evaluator keeps reviewing; C2 got its automatic score: 1 (institutional met, upload missing)
        given().auth().oauth2(evaluator).contentType(ContentType.JSON).body(Map.of("verdict", "APPROVED"))
                .post("/evidence/" + evidenceId + "/review").then().statusCode(200);
        String node = "macroProcesses.standards.flatten().criteria.flatten().find { it.code == 'LCK1-2' }";
        JsonPath checklist = given().auth().oauth2(evaluator).get("/self-assessments/" + assessmentId + "/checklist")
                .then().statusCode(200).extract().jsonPath();
        assertThat(checklist.getInt(node + ".score.score")).isEqualTo(1);
        assertThat(checklist.getString(node + ".score.justification")).contains("cerrar la carga documental del evaluado");
        assertThat(checklist.getString(node + ".conditions[1].status")).isEqualTo("NOT_MET");

        // Reopening undoes the automatic score and lets the evaluatee upload again
        given().auth().oauth2(evaluator).delete("/self-assessments/" + assessmentId + "/evidence-lock").then().statusCode(200)
                .body("evidenceLocked", equalTo(false));
        checklist = given().auth().oauth2(evaluator).get("/self-assessments/" + assessmentId + "/checklist").then().extract().jsonPath();
        assertThat(checklist.getString(node + ".score")).isNull();
        given().auth().oauth2(evaluatee).multiPart("auditSourceId", String.valueOf(c2.getLong("conditions[1].sources[0].id")))
                .multiPart("files", "acta.pdf", PDF, "application/pdf")
                .post("/self-assessments/" + assessmentId + "/evidence").then().statusCode(201);
    }

    /** A minimal criterion for a category, so an assessment can be started for it. */
    private static void ensureCatalogForCategory(String admin, String category) {
        long macroId = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("code", "E2E-" + category, "name", "Macro " + category, "category", "SUPPORT",
                        "scoreWeight", 2.5, "displayOrder", 9))
                .post("/catalog/macro-processes").then().statusCode(201).extract().response());
        long standardId = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("macroProcessId", macroId, "code", "E2E-" + category + "-1", "statement", "Estándar"))
                .post("/catalog/standards").then().statusCode(201).extract().response());
        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("standardId", standardId, "code", "E2E-" + category + "-1-1", "statement", "Criterio",
                        "type", "STRUCTURE", "applicableCategories", List.of(category),
                        "conditions", List.of(Map.of("description", "Se observa", "scoreWeight", 2,
                                "technique", "OBSERVATION", "displayOrder", 1))))
                .post("/catalog/criteria").then().statusCode(201);
    }
}
