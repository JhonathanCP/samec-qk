package com.essalud.samec.catalog;

import static com.essalud.samec.TestSupport.token;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.essalud.samec.shared.domain.NaturalOrder;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

/** Maintenance of the catalog through the API: tree, usages and deletes that explain why not. */
@QuarkusTest
class CatalogAdminApiTest {

    private static long id(io.restassured.response.Response r) {
        return r.jsonPath().getLong("id");
    }

    @Test
    void codesAreOrderedAsPeopleReadThem() {
        assertThat(List.of("DIR1-10", "DIR1-2", "DIR1-1", "dir1-3").stream().sorted(NaturalOrder.INSTANCE).toList())
                .containsExactly("DIR1-1", "DIR1-2", "dir1-3", "DIR1-10");
    }

    @Test
    void treeUsagesAndExplainedDeletes() {
        String admin = token("admin");
        long macro = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("code", "ADM-M", "name", "Macro admin", "category", "SUPPORT", "scoreWeight", 2.5, "displayOrder", 30))
                .post("/catalog/macro-processes").then().statusCode(201).extract().response());
        long standard = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("macroProcessId", macro, "code", "ADM-1", "statement", "Estándar"))
                .post("/catalog/standards").then().statusCode(201).extract().response());
        long process = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("name", "ADM Proceso")).post("/catalog/institutional-processes").then().statusCode(201).extract().response());
        long subprocess = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("name", "ADM Subproceso", "parentId", process))
                .post("/catalog/institutional-processes").then().statusCode(201).extract().response());
        long norm = id(given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("title", "ADM Directiva 01")).post("/catalog/normative-documents").then().statusCode(201).extract().response());

        for (String code : List.of("ADM1-10", "ADM1-2")) {
            given().auth().oauth2(admin).contentType(ContentType.JSON)
                    .body(Map.of("standardId", standard, "code", code, "statement", "Criterio " + code, "type", "PROCESS",
                            "applicableCategories", List.of("I_1"),
                            "conditions", List.of(Map.of("description", "Condición", "scoreWeight", 2, "technique", "DOCUMENT_REVIEW",
                                    "displayOrder", 1, "institutionalProcessId", subprocess,
                                    "normativeReferences", List.of(Map.of("documentId", norm)),
                                    "sources", List.of(Map.of("name", "Informe", "availability", "PROVIDED_BY_FACILITY", "displayOrder", 1))))))
                    .post("/catalog/criteria").then().statusCode(201);
        }

        // Tree: natural order of codes and summaries
        JsonPath tree = given().auth().oauth2(admin).get("/catalog/tree").then().statusCode(200).extract().jsonPath();
        String nodo = "find { it.code == 'ADM-M' }.standards[0]";
        assertThat(tree.getList(nodo + ".criteria.code")).containsExactly("ADM1-2", "ADM1-10");
        assertThat(tree.getInt(nodo + ".criteria[0].weightTotal")).isEqualTo(2);
        assertThat(tree.getInt(nodo + ".criteria[0].sourcesToUpload")).isEqualTo(1);

        // Where the sub-process and the norm are used
        given().auth().oauth2(admin).get("/catalog/institutional-processes/" + subprocess + "/usage").then().statusCode(200)
                .body("criterionCode", equalTo(List.of("ADM1-2", "ADM1-10")));
        given().auth().oauth2(admin).get("/catalog/normative-documents/" + norm + "/usage").then().statusCode(200)
                .body("", hasSize(2));

        // Deletes that explain why they are not possible
        given().auth().oauth2(admin).delete("/catalog/macro-processes/" + macro).then().statusCode(400)
                .body("message", containsString("tiene 1 estándar"));
        given().auth().oauth2(admin).delete("/catalog/standards/" + standard).then().statusCode(400)
                .body("message", containsString("tiene 2 criterio"));
        given().auth().oauth2(admin).delete("/catalog/institutional-processes/" + process).then().statusCode(400)
                .body("message", containsString("subproceso"));
        given().auth().oauth2(admin).delete("/catalog/institutional-processes/" + subprocess).then().statusCode(400)
                .body("message", containsString("ADM1-2, ADM1-10"));
        given().auth().oauth2(admin).delete("/catalog/normative-documents/" + norm).then().statusCode(400)
                .body("message", containsString("citada en 2 criterio"));
        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("macroProcessId", macro, "code", "ADM-1", "statement", "Duplicado"))
                .post("/catalog/standards").then().statusCode(409).body("message", equalTo("Ya existe un estándar con ese código."));

        // Only the catalog permission may change it
        given().auth().oauth2(token("evaluador")).delete("/catalog/standards/" + standard).then().statusCode(403);
    }
}
