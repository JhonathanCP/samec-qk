package com.essalud.samec.organizacion;

/**
 * Nivel de complejidad de una IPRESS. Se guarda el nivel grueso (I, II, III); si el core necesita
 * la categoría exacta (I-1 … III-2, columna "Categoría IPRESS" del LEA) habrá que ampliarlo.
 */
public enum NivelComplejidad {
    I("Nivel I"),
    II("Nivel II"),
    III("Nivel III");

    private final String etiqueta;

    NivelComplejidad(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Acepta "1", "I", "Nivel II", "nivel iii"; null si no aplica. */
    public static NivelComplejidad desde(String valor) {
        if (valor == null) return null;
        String v = valor.strip().toUpperCase().replace("NIVEL", "").replace("-", "").strip();
        return switch (v) {
            case "1", "I" -> I;
            case "2", "II" -> II;
            case "3", "III" -> III;
            default -> null;
        };
    }
}
