package com.essalud.samec.catalog.infrastructure.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.essalud.samec.catalog.domain.model.AuditSource;
import com.essalud.samec.catalog.domain.model.Criterion;
import com.essalud.samec.catalog.domain.model.CriterionCondition;
import com.essalud.samec.catalog.domain.model.CriterionType;
import com.essalud.samec.catalog.domain.model.EvaluationTechnique;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.catalog.domain.model.InstitutionalProcess;
import com.essalud.samec.catalog.domain.model.MacroProcess;
import com.essalud.samec.catalog.domain.model.MacroProcessCategory;
import com.essalud.samec.catalog.domain.model.NormativeDocument;
import com.essalud.samec.catalog.domain.model.NormativeReference;
import com.essalud.samec.catalog.domain.model.ObservationType;
import com.essalud.samec.catalog.domain.model.ScoreLevel;
import com.essalud.samec.catalog.domain.model.SourceAvailability;
import com.essalud.samec.catalog.domain.model.SourceLogic;
import com.essalud.samec.catalog.domain.model.Standard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies of the LEA catalog API; each one becomes a domain model (the id comes from the path). */
public final class CatalogRequests {

    private CatalogRequests() {
    }

    public record MacroProcessRequest(
            @NotBlank(message = "El código es obligatorio") @Size(max = 20, message = "Código: máximo 20 caracteres") String code,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 250, message = "Nombre: máximo 250 caracteres") String name,
            @NotNull(message = "La categoría es obligatoria") MacroProcessCategory category,
            @NotNull(message = "El peso es obligatorio") @DecimalMin(value = "0", message = "El peso no puede ser negativo")
            @DecimalMax(value = "100", message = "El peso no puede superar 100") BigDecimal scoreWeight,
            @NotNull(message = "El orden es obligatorio") Integer displayOrder) {

        MacroProcess toModel() {
            return new MacroProcess(null, code.strip(), name.strip(), category, scoreWeight, displayOrder);
        }
    }

    public record StandardRequest(
            @NotNull(message = "El macroproceso es obligatorio") Long macroProcessId,
            @NotBlank(message = "El código es obligatorio") @Size(max = 20, message = "Código: máximo 20 caracteres") String code,
            @NotBlank(message = "El enunciado es obligatorio") String statement) {

        Standard toModel() {
            return new Standard(null, macroProcessId, code.strip(), statement.strip());
        }
    }

    public record InstitutionalProcessRequest(
            Long parentId,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 250, message = "Nombre: máximo 250 caracteres") String name) {

        InstitutionalProcess toModel() {
            return new InstitutionalProcess(null, parentId, name.strip());
        }
    }

    public record NormativeDocumentRequest(
            @NotBlank(message = "El título es obligatorio") @Size(max = 1000, message = "Título: máximo 1000 caracteres") String title,
            @Size(max = 1000, message = "Enlace: máximo 1000 caracteres") String url) {

        NormativeDocument toModel() {
            return new NormativeDocument(null, title.strip(), blankToNull(url));
        }
    }

    public record ObservationTypeRequest(
            @NotBlank(message = "El código es obligatorio") @Size(max = 40, message = "Código: máximo 40 caracteres") String code,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 250, message = "Nombre: máximo 250 caracteres") String name,
            Boolean active) {

        ObservationType toModel() {
            return new ObservationType(null, code.strip(), name.strip(), active == null || active);
        }
    }

    // ── Criterion (whole aggregate) ──

    public record ScoreLevelRequest(
            @NotNull(message = "El puntaje del nivel es obligatorio") @Min(value = 0, message = "Puntaje mínimo 0")
            @Max(value = 2, message = "Puntaje máximo 2") Integer score,
            @NotBlank(message = "La descripción del nivel es obligatoria") String description) {
    }

    public record NormativeReferenceRequest(
            @NotNull(message = "El documento normativo es obligatorio") Long documentId,
            String applicableExcerpt) {
    }

    /** {@code id} only to keep an existing source on update. */
    public record AuditSourceRequest(
            Long id,
            @NotBlank(message = "El nombre de la fuente es obligatorio") @Size(max = 1000, message = "Fuente: máximo 1000 caracteres") String name,
            @Size(max = 1000, message = "Enlace: máximo 1000 caracteres") String referenceUrl,
            @NotNull(message = "La disponibilidad de la fuente es obligatoria") SourceAvailability availability,
            Boolean appliesSystemWide,
            @NotNull(message = "El orden de la fuente es obligatorio") Integer displayOrder) {

        AuditSource toModel() {
            return new AuditSource(id, name.strip(), blankToNull(referenceUrl), availability,
                    Boolean.TRUE.equals(appliesSystemWide), displayOrder);
        }
    }

    /** {@code id} only to keep an existing condition on update. */
    public record ConditionRequest(
            Long id,
            @NotBlank(message = "La descripción de la condición es obligatoria") String description,
            @NotNull(message = "El peso de la condición es obligatorio") @Min(value = 0, message = "Peso mínimo 0")
            @Max(value = 2, message = "Peso máximo 2") Integer scoreWeight,
            @NotNull(message = "La técnica de evaluación es obligatoria") EvaluationTechnique technique,
            Long institutionalProcessId,
            @Size(max = 1000, message = "Función de la IPRESS: máximo 1000 caracteres") String facilityFunction,
            @Size(max = 500, message = "Responsable: máximo 500 caracteres") String responsibleRole,
            SourceLogic sourceLogic,
            List<@Valid NormativeReferenceRequest> normativeReferences,
            List<@Valid AuditSourceRequest> sources,
            @NotNull(message = "El orden de la condición es obligatorio") Integer displayOrder,
            String verificationGuide) {

        CriterionCondition toModel() {
            return new CriterionCondition(id, description.strip(), scoreWeight, technique, institutionalProcessId,
                    blankToNull(facilityFunction), blankToNull(responsibleRole), sourceLogic,
                    normativeReferences == null ? List.of() : normativeReferences.stream()
                            .map(r -> new NormativeReference(r.documentId(), blankToNull(r.applicableExcerpt()))).toList(),
                    sources == null ? List.of() : sources.stream().map(AuditSourceRequest::toModel).toList(),
                    displayOrder, blankToNull(verificationGuide));
        }
    }

    public record CriterionRequest(
            @NotNull(message = "El estándar es obligatorio") Long standardId,
            @NotBlank(message = "El código es obligatorio") @Size(max = 20, message = "Código: máximo 20 caracteres") String code,
            @NotBlank(message = "El enunciado es obligatorio") String statement,
            @NotNull(message = "El tipo de criterio es obligatorio") CriterionType type,
            @NotEmpty(message = "Indique las categorías de IPRESS a las que aplica") Set<FacilityCategory> applicableCategories,
            List<@Valid ScoreLevelRequest> scoreLevels,
            @NotEmpty(message = "El criterio debe tener al menos una condición") List<@Valid ConditionRequest> conditions) {

        Criterion toModel() {
            return new Criterion(null, standardId, code.strip(), statement.strip(), type, applicableCategories,
                    scoreLevels == null ? List.of() : scoreLevels.stream()
                            .map(l -> new ScoreLevel(l.score(), l.description().strip())).toList(),
                    conditions.stream().map(ConditionRequest::toModel).toList());
        }
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
