package com.essalud.samec.catalog.infrastructure.persistence;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.hibernate.Length;

import com.essalud.samec.catalog.domain.model.CriterionType;
import com.essalud.samec.catalog.domain.model.FacilityCategory;
import com.essalud.samec.shared.trazabilidad.EntidadAuditable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

@Getter
@Setter
@Entity
@Table(name = "criteria",
        uniqueConstraints = @UniqueConstraint(name = "uk_criteria_code", columnNames = "code"),
        indexes = @Index(name = "ix_criteria_standard_id", columnList = "standard_id"))
public class CriterionEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "standard_id", nullable = false, foreignKey = @ForeignKey(name = "fk_criteria_standard"))
    private StandardEntity standard;

    @Column(name = "code", length = 20, nullable = false)
    private String code;

    @Column(name = "statement", length = Length.LONG32, nullable = false)
    private String statement;

    @Enumerated(EnumType.STRING)
    @Column(name = "criterion_type", length = 20, nullable = false)
    private CriterionType criterionType;

    /** Categories of facility the criterion applies to; the PK is (criterion_id, facility_category). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "criterion_facility_categories",
            joinColumns = @JoinColumn(name = "criterion_id", nullable = false),
            foreignKey = @ForeignKey(name = "fk_criterion_facility_categories_criterion"))
    @Enumerated(EnumType.STRING)
    @Column(name = "facility_category", length = 10, nullable = false)
    private Set<FacilityCategory> facilityCategories = EnumSet.noneOf(FacilityCategory.class);

    @OneToMany(mappedBy = "criterion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("score")
    private List<CriterionScoreLevelEntity> scoreLevels = new ArrayList<>();

    @OneToMany(mappedBy = "criterion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder")
    private List<CriterionConditionEntity> conditions = new ArrayList<>();

    public void addScoreLevel(CriterionScoreLevelEntity level) {
        level.setCriterion(this);
        scoreLevels.add(level);
    }

    public void addCondition(CriterionConditionEntity condition) {
        condition.setCriterion(this);
        conditions.add(condition);
    }
}
