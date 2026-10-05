package com.essalud.samec.filestorage.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.essalud.samec.filestorage.domain.model.FileContent;
import com.essalud.samec.filestorage.domain.service.FileTypeDetector;

class FileTypeDetectorTest {

    private static FileContent bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) b[i] = (byte) values[i];
        return new FileContent(b);
    }

    @Test
    void typeComesFromTheExtensionAndMustMatchTheContent() {
        FileContent pdf = new FileContent("%PDF-1.7 ...".getBytes(StandardCharsets.US_ASCII));
        assertThat(FileTypeDetector.detect("Plan.PDF", pdf)).isEqualTo("application/pdf");
        assertThat(FileTypeDetector.detect("foto.jpg", bytes(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo("image/jpeg");
        assertThat(FileTypeDetector.detect("acta.docx", bytes(0x50, 0x4B, 0x03, 0x04)))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertThatThrownBy(() -> FileTypeDetector.detect("virus.pdf", bytes(0x4D, 0x5A, 0x90, 0x00)))
                .hasMessageContaining("no corresponde");
        assertThatThrownBy(() -> FileTypeDetector.detect("script.exe", pdf)).hasMessageContaining("no permitido");
        assertThatThrownBy(() -> FileTypeDetector.detect("sin-extension", pdf)).hasMessageContaining("no permitido");
    }

    @Test
    void cleansTheNameSentByTheBrowser() {
        assertThat(FileTypeDetector.cleanName("C:\\Users\\ana\\Plan anual.pdf")).isEqualTo("Plan anual.pdf");
        assertThat(FileTypeDetector.cleanName("../../etc/acta.pdf")).isEqualTo("acta.pdf");
        assertThatThrownBy(() -> FileTypeDetector.cleanName("  ")).hasMessageContaining("obligatorio");
    }
}
