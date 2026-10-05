package com.essalud.samec.filestorage.domain.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.essalud.samec.shared.domain.DomainException;

/** The bytes of a stored file. */
public record FileContent(byte[] bytes) {

    public FileContent {
        if (bytes == null || bytes.length == 0) throw new DomainException("El archivo está vacío");
    }

    public long size() {
        return bytes.length;
    }

    public String sha256() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e); // JVM problem, not a business rule
        }
    }
}
