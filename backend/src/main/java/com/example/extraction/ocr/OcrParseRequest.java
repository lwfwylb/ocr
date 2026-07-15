package com.example.extraction.ocr;

import com.example.extraction.configuration.dto.ConfigWizardPayload;
import com.example.extraction.model.domain.OcrEngineConfigRecord;
import com.example.extraction.task.domain.ExtractTaskRecord;

import java.nio.file.Path;

public class OcrParseRequest {
    private final ExtractTaskRecord task;
    private final OcrEngineConfigRecord engine;
    private final ConfigWizardPayload payload;
    private final Path inputPath;
    private final String inputFileName;
    private final String fileType;

    public OcrParseRequest(ExtractTaskRecord task,
                           OcrEngineConfigRecord engine,
                           ConfigWizardPayload payload,
                           Path inputPath,
                           String inputFileName,
                           String fileType) {
        this.task = task;
        this.engine = engine;
        this.payload = payload;
        this.inputPath = inputPath;
        this.inputFileName = inputFileName;
        this.fileType = fileType;
    }

    public ExtractTaskRecord getTask() {
        return task;
    }

    public OcrEngineConfigRecord getEngine() {
        return engine;
    }

    public ConfigWizardPayload getPayload() {
        return payload;
    }

    public Path getInputPath() {
        return inputPath;
    }

    public String getInputFileName() {
        return inputFileName;
    }

    public String getFileType() {
        return fileType;
    }
}
