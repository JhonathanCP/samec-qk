package com.essalud.samec.catalog.infrastructure.persistence;

import com.essalud.samec.shared.trazabilidad.EntidadAuditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Norm (law, technical guide, directive) cited by the LEA; stored once and referenced. */
@Getter
@Setter
@Entity
@Table(name = "normative_documents",
        uniqueConstraints = @UniqueConstraint(name = "uk_normative_documents_title", columnNames = "title"))
public class NormativeDocumentEntity extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", length = 1000, nullable = false)
    private String title;

    @Column(name = "url", length = 1000)
    private String url;
}
