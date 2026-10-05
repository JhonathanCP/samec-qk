package com.essalud.samec.filestorage.domain.service;

import java.util.Locale;
import java.util.Map;

import com.essalud.samec.filestorage.domain.model.FileContent;
import com.essalud.samec.shared.domain.DomainException;

/**
 * Decides the content type of an uploaded file from its extension and checks that the bytes
 * really are of that kind (magic numbers). The type the browser declares is not trusted.
 */
public final class FileTypeDetector {

    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"));

    private FileTypeDetector() {
    }

    public static String detect(String fileName, FileContent content) {
        String extension = extensionOf(fileName);
        String type = BY_EXTENSION.get(extension);
        if (type == null) {
            throw new DomainException("Tipo de archivo no permitido (." + extension
                    + "). Se aceptan PDF, imágenes JPG/PNG y documentos de Office.");
        }
        if (!signatureMatches(extension, content.bytes())) {
            throw new DomainException("El contenido del archivo " + fileName + " no corresponde a su extensión");
        }
        return type;
    }

    /** Name without folders or control characters, as some browsers send the full path. */
    public static String cleanName(String fileName) {
        String name = fileName == null ? "" : fileName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) throw new DomainException("El nombre del archivo es obligatorio");
        return name;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean signatureMatches(String extension, byte[] b) {
        return switch (extension) {
            case "pdf" -> startsWith(b, 0x25, 0x50, 0x44, 0x46);                  // %PDF
            case "jpg", "jpeg" -> startsWith(b, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(b, 0x89, 0x50, 0x4E, 0x47);
            case "docx", "xlsx", "pptx" -> startsWith(b, 0x50, 0x4B, 0x03, 0x04); // ZIP (OOXML)
            case "doc", "xls", "ppt" -> startsWith(b, 0xD0, 0xCF, 0x11, 0xE0);    // OLE2
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }
}
