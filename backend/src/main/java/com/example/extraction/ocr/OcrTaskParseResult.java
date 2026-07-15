package com.example.extraction.ocr;

import com.example.extraction.model.domain.OcrEngineConfigRecord;
import com.example.extraction.result.domain.DocumentParseResultRecord;

public class OcrTaskParseResult {
    private final DocumentParseResultRecord parseResult;
    private final OcrEngineConfigRecord engine;
    private final long durationMs;
    private final boolean directTextParse;

    public OcrTaskParseResult(DocumentParseResultRecord parseResult, OcrEngineConfigRecord engine, long durationMs, boolean directTextParse) {
        this.parseResult = parseResult;
        this.engine = engine;
        this.durationMs = durationMs;
        this.directTextParse = directTextParse;
    }

    public DocumentParseResultRecord getParseResult() {
        return parseResult;
    }

    public OcrEngineConfigRecord getEngine() {
        return engine;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isDirectTextParse() {
        return directTextParse;
    }
}
