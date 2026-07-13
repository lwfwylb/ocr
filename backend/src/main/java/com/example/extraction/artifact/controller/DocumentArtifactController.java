package com.example.extraction.artifact.controller;

import com.example.extraction.artifact.domain.DocumentArtifactRecord;
import com.example.extraction.artifact.dto.DocumentArtifactResponse;
import com.example.extraction.artifact.dto.DocumentArtifactStepResponse;
import com.example.extraction.artifact.service.DocumentArtifactService;
import com.example.extraction.common.ApiResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/artifacts")
public class DocumentArtifactController {
    private final DocumentArtifactService documentArtifactService;

    public DocumentArtifactController(DocumentArtifactService documentArtifactService) {
        this.documentArtifactService = documentArtifactService;
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<List<DocumentArtifactResponse>> listByTaskId(@PathVariable("taskId") String taskId) {
        return ApiResponse.success(documentArtifactService.listByTaskId(taskId));
    }

    @GetMapping("/tasks/{taskId}/steps")
    public ApiResponse<List<DocumentArtifactStepResponse>> stepsByTaskId(@PathVariable("taskId") String taskId) {
        return ApiResponse.success(documentArtifactService.stepsByTaskId(taskId));
    }

    @GetMapping("/{artifactId}/preview")
    public ResponseEntity<Resource> preview(@PathVariable("artifactId") String artifactId) {
        return fileResponse(artifactId, false);
    }

    @GetMapping("/{artifactId}/download")
    public ResponseEntity<Resource> download(@PathVariable("artifactId") String artifactId) {
        return fileResponse(artifactId, true);
    }

    private ResponseEntity<Resource> fileResponse(String artifactId, boolean attachment) {
        DocumentArtifactRecord artifact = documentArtifactService.requireArtifact(artifactId);
        Path path = documentArtifactService.requireReadablePath(artifactId);
        FileSystemResource resource = new FileSystemResource(path);
        String fileName = artifact.getFileName() == null ? path.getFileName().toString() : artifact.getFileName();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (artifact.getMimeType() != null && !artifact.getMimeType().contains(";")) {
            mediaType = MediaType.parseMediaType(artifact.getMimeType());
        }
        ContentDisposition disposition = (attachment ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(fileName)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }
}
