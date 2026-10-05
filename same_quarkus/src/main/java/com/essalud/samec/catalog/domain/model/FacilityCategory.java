package com.essalud.samec.catalog.domain.model;

import com.essalud.samec.shared.domain.DomainException;

/** Health facility category; each criterion applies only to some of them. */
public enum FacilityCategory {
    I_1("I-1"), I_2("I-2"), I_3("I-3"), I_4("I-4"),
    II_1("II-1"), II_2("II-2"),
    III_1("III-1");

    private final String label;

    FacilityCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static FacilityCategory fromLabel(String label) {
        String value = label == null ? "" : label.strip();
        for (FacilityCategory category : values()) {
            if (category.label.equalsIgnoreCase(value)) return category;
        }
        throw new DomainException("Categoría de IPRESS desconocida: " + label);
    }
}
