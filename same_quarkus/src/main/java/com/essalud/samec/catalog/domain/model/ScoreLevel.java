package com.essalud.samec.catalog.domain.model;

import com.essalud.samec.shared.domain.DomainException;

/** What a given score (0, 1 or 2) means for a criterion. */
public record ScoreLevel(int score, String description) {

    public ScoreLevel {
        if (score < 0 || score > Criterion.MAX_SCORE) {
            throw new DomainException("Nivel de puntaje fuera de rango: " + score);
        }
        if (description == null || description.isBlank()) {
            throw new DomainException("La descripción del nivel de puntaje es obligatoria");
        }
    }
}
