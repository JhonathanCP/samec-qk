package com.essalud.samec.catalog.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Length;

import com.essalud.samec.catalog.domain.model.EvaluationTechnique;
import com.essalud.samec.catalog.domain.model.SourceLogic;
import com.essalud.samec.shared.trazabilidad.EntidadAuditable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Specific condition of a criterion; its weight is the part of the score it contributes. */
@Getter
@Setter
@Entity
@Table(name = "criterion_conditions",
        uniqueConstraints = @UniqueConstraint(name = "uk_criterion_conditions_criterion_order",
                columnNames = {"criterion_id", "display_order"}),
        indexes = @Index(name = "ix_criterion_conditions_institutional_process_id", columnList = "institutional_process_id"),
        check = @CheckConstraint(name = "ck_criterion_conditions_score_weight", constraint = "score_weight between 0 and 2"))
public class CriterionConditionEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_criterion_conditions_criterion"))
    private CriterionEntity criterion;

    @Column(name = "description", length = Length.LONG32, nullable = false)
    private String description;

    @Column(name = "score_weight", nullable = false)
    private Integer scoreWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "technique", length = 40, nullable = false)
    private EvaluationTechnique technique;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institutional_process_id",
            foreignKey = @ForeignKey(name = "fk_criterion_conditions_institutional_process"))
    private InstitutionalProcessEntity institutionalProcess;

    @Column(name = "facility_function", length = 1000)
    private String facilityFunction;

    @Column(name = "responsible_role", length = 500)
    private String responsibleRole;

    /** Required when the condition has more than one source. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_logic", length = 10)
    private SourceLogic sourceLogic;

    /** What to check on site (observation, interview, medical record audit). */
    @Column(name = "verification_guide", length = Length.LONG32)
    private String verificationGuide;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @OneToMany(mappedBy = "condition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder")
    private List<AuditSourceEntity> sources = new ArrayList<>();

    @OneToMany(mappedBy = "condition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConditionNormativeReferenceEntity> normativeReferences = new ArrayList<>();

    public void addSource(AuditSourceEntity source) {
        source.setCondition(this);
        sources.add(source);
    }

    public void addNormativeReference(ConditionNormativeReferenceEntity reference) {
        reference.setCondition(this);
        normativeReferences.add(reference);
    }
}
