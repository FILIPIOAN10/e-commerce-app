package com.ecommerce.project.service.gdpr;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Serialises assembled sections into the ZIP the customer downloads: one
 * pretty-printed JSON file per domain, plus a manifest.
 *
 * <p>A private {@code ObjectMapper}, for the same reason
 * {@link com.ecommerce.project.service.outbox.OutboxPayloadCodec} keeps one: the
 * archive format is a promise made to a person, and it must not shift because
 * somebody tuned the web layer's serialisation. Dates are written as ISO-8601
 * strings rather than epoch numbers — this file is meant to be read by a human.
 */
@Component
public class GdprExportArchiveWriter {

    private static final String MANIFEST_ENTRY = "manifest.json";

    private static final String MANIFEST_NOTE = """
            This archive contains the personal data this store holds about your account, \
            exported under GDPR Article 15. One file per area. Order history is included \
            as it stands; if you also request erasure, those records are kept for tax \
            purposes but stripped of anything identifying you.""";

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .build();

    public byte[] write(Long userId, List<GdprExportData.Section> sections) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            GdprExportData.Manifest manifest = new GdprExportData.Manifest(
                    Instant.now(),
                    userId,
                    MANIFEST_NOTE,
                    sections.stream().map(GdprExportData.Section::fileName).toList());
            writeEntry(zip, MANIFEST_ENTRY, manifest);

            for (GdprExportData.Section section : sections) {
                writeEntry(zip, section.fileName(), section.content());
            }
        } catch (IOException e) {
            // In-memory streams: an IOException here is a bug, not a transient fault.
            throw new UncheckedIOException("Failed to build GDPR export archive", e);
        }
        return buffer.toByteArray();
    }

    private void writeEntry(ZipOutputStream zip, String fileName, Object content) throws IOException {
        zip.putNextEntry(new ZipEntry(fileName));
        zip.write(objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(content));
        zip.closeEntry();
    }
}
