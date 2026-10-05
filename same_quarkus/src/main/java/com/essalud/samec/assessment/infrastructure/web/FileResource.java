package com.essalud.samec.assessment.infrastructure.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestResponse;

import com.essalud.samec.assessment.application.port.in.AssessmentFileUseCases;
import com.essalud.samec.filestorage.application.port.in.FileStorageUseCases.FileDownload;
import com.essalud.samec.security.Permisos;

import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Download of evidence files and on-site photos (from the database). */
@Path("/files")
public class FileResource {

    @Inject
    AssessmentFileUseCases files;

    /**
     * {@code inline=true} to show it in the browser (PDF, images) instead of downloading it. Declared
     * as octet-stream so the bytes are written raw (with a wildcard JSON would serialize them as
     * base64); the response then carries the file's real content type.
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @PermissionsAllowed(Permisos.AUTOEVALUACION_VER)
    public RestResponse<byte[]> download(@PathParam("id") Long id, @RestQuery boolean inline) {
        FileDownload file = files.download(id);
        String name = file.metadata().originalName();
        String asciiName = name.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "'");
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return RestResponse.ResponseBuilder.ok(file.content().bytes(), file.metadata().contentType())
                .header("Content-Disposition", (inline ? "inline" : "attachment")
                        + "; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded)
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, no-store")
                .build();
    }
}
