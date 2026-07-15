package com.example.extraction.ocr;

import com.example.extraction.common.BusinessException;
import com.example.extraction.configuration.dto.ConfigWizardPayload;
import com.example.extraction.model.domain.OcrEngineConfigRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

@Component
public class PaddleOcrVlClient implements OcrEngineClient {
    private final ObjectMapper objectMapper;

    public PaddleOcrVlClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String adapterType, String engineType, String provider, String engineCode) {
        if ("PADDLE_OCR_VL".equalsIgnoreCase(nullToEmpty(adapterType))) {
            return true;
        }
        String joined = (nullToEmpty(adapterType) + " " + nullToEmpty(engineType) + " " + nullToEmpty(provider) + " " + nullToEmpty(engineCode)).toLowerCase();
        return joined.contains("paddle") || joined.contains("paddleocr") || joined.contains("paddle_ocr_vl");
    }

    @Override
    public OcrParseResponse parse(OcrParseRequest request) {
        OcrEngineConfigRecord engine = request.getEngine();
        if (!StringUtils.hasText(engine.getBaseUrl())) {
            throw new BusinessException("OCR_400", "OCR 引擎服务地址为空");
        }
        long begin = System.currentTimeMillis();
        try {
            byte[] fileContent = Files.readAllBytes(request.getInputPath());
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("file", Base64.getEncoder().encodeToString(fileContent));
            payload.put("fileType", resolvePaddleFileType(request));
            payload.put("useDocUnwarping", boolParam(request, "useDocUnwarping", true));
            payload.put("useOcrForImageBlock", boolParam(request, "useOcrForImageBlock", true));
            payload.put("formatBlockContent", boolParam(request, "formatBlockContent", true));
            payload.put("useSealRecognition", boolParam(request, "useSealRecognition", true));
            payload.put("useDocOrientationClassify", boolParam(request, "useDocOrientationClassify", true));
            payload.put("useLayoutDetection", boolParam(request, "useLayoutDetection", true));
            payload.put("layoutMergeBboxesMode", textParam(request, "layoutMergeBboxesMode", "large"));

            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(engine.getBaseUrl()))
                    .timeout(Duration.ofSeconds(timeoutSeconds(engine, 180)))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            HttpResponse<String> httpResponse = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds(engine, 180), 30)))
                    .build()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                throw new BusinessException("OCR_HTTP_" + httpResponse.statusCode(), "PaddleOCR-VL 调用失败：HTTP " + httpResponse.statusCode());
            }
            OcrParseResponse result = parseResponseBody(httpResponse.body());
            result.setEngineCode(engine.getEngineCode());
            result.setDurationMs(System.currentTimeMillis() - begin);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("OCR_IO_ERROR", "PaddleOCR-VL 文件读取或响应解析失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("OCR_INTERRUPTED", "PaddleOCR-VL 调用被中断");
        } catch (IllegalArgumentException e) {
            throw new BusinessException("OCR_400", "PaddleOCR-VL 服务地址不合法：" + e.getMessage());
        }
    }

    private OcrParseResponse parseResponseBody(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode resultNode = root.path("result");
        JsonNode layoutParsingResults = resultNode.path("layoutParsingResults");
        StringBuilder markdown = new StringBuilder(4096);
        OcrParseResponse result = new OcrParseResponse();
        int pageCount = 0;
        if (layoutParsingResults.isArray()) {
            for (JsonNode item : layoutParsingResults) {
                String text = item.path("markdown").path("text").asText("");
                collectImages(result, item.path("markdown").path("images"), "markdown");
                collectImages(result, item.path("outputImages"), "output");
                if (StringUtils.hasText(text)) {
                    if (!markdown.isEmpty()) {
                        markdown.append("\n\n---\n\n");
                    }
                    markdown.append(text);
                }
                pageCount++;
            }
        }
        if (!StringUtils.hasText(markdown.toString())) {
            markdown.append(resultNode.toString());
        }
        result.setMarkdownText(markdown.toString());
        result.setRawJson(responseBody);
        result.setPageCount(pageCount == 0 ? null : pageCount);
        return result;
    }

    private void collectImages(OcrParseResponse result, JsonNode imagesNode, String prefix) {
        if (imagesNode == null || !imagesNode.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = imagesNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            byte[] bytes = decodeBase64(entry.getValue().asText(""));
            if (bytes.length > 0) {
                String imageName = "markdown".equals(prefix) ? entry.getKey() : prefix + "_" + entry.getKey();
                result.getImages().add(new OcrImageArtifact(imageName, sanitizeName(imageName), bytes, guessMimeType(entry.getKey(), "image/jpeg")));
            }
        }
    }

    private int resolvePaddleFileType(OcrParseRequest request) {
        Object configured = param(request, "fileType");
        if (configured instanceof Number number) {
            return number.intValue();
        }
        if (configured instanceof String value && StringUtils.hasText(value)) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        String fileType = nullToEmpty(request.getFileType()).toLowerCase();
        String fileName = nullToEmpty(request.getInputFileName()).toLowerCase();
        if (fileType.contains("pdf") || fileName.endsWith(".pdf")) {
            return 0;
        }
        return 1;
    }

    private boolean boolParam(OcrParseRequest request, String key, boolean defaultValue) {
        Object value = param(request, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    private String textParam(OcrParseRequest request, String key, String defaultValue) {
        Object value = param(request, key);
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? defaultValue : String.valueOf(value);
    }

    private Object param(OcrParseRequest request, String key) {
        ConfigWizardPayload payload = request.getPayload();
        if (payload == null || payload.getParseConfig() == null || payload.getParseConfig().getEngineParams() == null) {
            return null;
        }
        return payload.getParseConfig().getEngineParams().get(key);
    }

    private int timeoutSeconds(OcrEngineConfigRecord engine, int fallback) {
        return engine.getTimeoutSeconds() == null || engine.getTimeoutSeconds() <= 0 ? fallback : engine.getTimeoutSeconds();
    }

    private byte[] decodeBase64(String value) {
        if (!StringUtils.hasText(value)) {
            return new byte[0];
        }
        String pure = value.contains(",") ? value.substring(value.indexOf(',') + 1) : value;
        try {
            return Base64.getDecoder().decode(pure);
        } catch (IllegalArgumentException ignored) {
            return new byte[0];
        }
    }

    private String sanitizeName(String value) {
        String text = StringUtils.hasText(value) ? value : "image.jpg";
        return text.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String guessMimeType(String fileName, String fallback) {
        String lower = nullToEmpty(fileName).toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return fallback;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
