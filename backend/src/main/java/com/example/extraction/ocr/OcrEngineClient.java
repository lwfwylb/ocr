package com.example.extraction.ocr;

public interface OcrEngineClient {
    boolean supports(String adapterType, String engineType, String provider, String engineCode);

    OcrParseResponse parse(OcrParseRequest request);
}
