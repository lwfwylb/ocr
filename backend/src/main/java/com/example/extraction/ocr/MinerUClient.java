package com.example.extraction.ocr;

import com.example.extraction.common.BusinessException;
import com.example.extraction.model.domain.OcrEngineConfigRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Component
public class MinerUClient implements OcrEngineClient {
    private final ObjectMapper objectMapper;

    public MinerUClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String engineType, String provider, String engineCode) {
        String joined = (nullToEmpty(engineType) + " " + nullToEmpty(provider) + " " + nullToEmpty(engineCode)).toLowerCase();
        return joined.contains("mineru") || joined.contains("miner_u");
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
            String boundary = "----ExtractionBoundary" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = multipartBody(boundary, request.getInputFileName(), fileContent, contentType(request));
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(engine.getBaseUrl()))
                    .timeout(Duration.ofSeconds(timeoutSeconds(engine, 360)))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> httpResponse = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds(engine, 360), 30)))
                    .build()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                throw new BusinessException("OCR_HTTP_" + httpResponse.statusCode(), "MinerU 调用失败：HTTP " + httpResponse.statusCode());
            }
            OcrParseResponse result = parseResponseBody(httpResponse.body());
            result.setEngineCode(engine.getEngineCode());
            result.setDurationMs(System.currentTimeMillis() - begin);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("OCR_IO_ERROR", "MinerU 文件读取或响应解析失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("OCR_INTERRUPTED", "MinerU 调用被中断");
        } catch (IllegalArgumentException e) {
            throw new BusinessException("OCR_400", "MinerU 服务地址不合法：" + e.getMessage());
        }
    }

    private OcrParseResponse parseResponseBody(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root.path("results");
        if (!results.isObject()) {
            throw new BusinessException("OCR_RESPONSE_ERROR", "MinerU 响应中没有 results 字段");
        }
        OcrParseResponse result = new OcrParseResponse();
        StringBuilder markdown = new StringBuilder(4096);
        Iterator<Map.Entry<String, JsonNode>> files = results.fields();
        int pageCount = 0;
        while (files.hasNext()) {
            Map.Entry<String, JsonNode> entry = files.next();
            JsonNode fileResult = entry.getValue();
            String text = fileResult.path("md_content").asText("");
            collectImages(result, fileResult.path("images"));
            if (StringUtils.hasText(text)) {
                if (!markdown.isEmpty()) {
                    markdown.append("\n\n---\n\n");
                }
                markdown.append(text);
            }
            pageCount++;
        }
        result.setMarkdownText(markdown.toString());
        result.setRawJson(responseBody);
        result.setPageCount(pageCount == 0 ? null : pageCount);
        return result;
    }

    private void collectImages(OcrParseResponse result, JsonNode imagesNode) {
        if (imagesNode == null || !imagesNode.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = imagesNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            byte[] bytes = decodeBase64(entry.getValue().asText(""));
            if (bytes.length > 0) {
                result.getImages().add(new OcrImageArtifact(entry.getKey(), sanitizeName(entry.getKey()), bytes, guessMimeType(entry.getKey(), "image/jpeg")));
            }
        }
    }

    private byte[] multipartBody(String boundary, String fileName, byte[] content, String contentType) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        addFilePart(output, boundary, "files", fileName, contentType, content);
        addTextPart(output, boundary, "backend", "pipeline");
        addTextPart(output, boundary, "lang_list", "ch");
        addTextPart(output, boundary, "lang_list", "en");
        addTextPart(output, boundary, "parse_method", "auto");
        addTextPart(output, boundary, "formula_enable", "true");
        addTextPart(output, boundary, "table_enable", "true");
        addTextPart(output, boundary, "return_md", "true");
        addTextPart(output, boundary, "return_middle_json", "false");
        addTextPart(output, boundary, "response_format_zip", "false");
        addTextPart(output, boundary, "return_images", "true");
        write(output, "--" + boundary + "--\r\n");
        return output.toByteArray();
    }

    private void addTextPart(ByteArrayOutputStream output, String boundary, String name, String value) throws IOException {
        write(output, "--" + boundary + "\r\n");
        write(output, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(output, value + "\r\n");
    }

    private void addFilePart(ByteArrayOutputStream output, String boundary, String name, String fileName,
                             String contentType, byte[] content) throws IOException {
        write(output, "--" + boundary + "\r\n");
        write(output, "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + safeFileName(fileName) + "\"\r\n");
        write(output, "Content-Type: " + contentType + "\r\n\r\n");
        output.write(content);
        write(output, "\r\n");
    }

    private void write(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private String contentType(OcrParseRequest request) {
        String fileType = nullToEmpty(request.getFileType()).toLowerCase();
        String fileName = nullToEmpty(request.getInputFileName()).toLowerCase();
        if (fileType.contains("pdf") || fileName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (fileType.contains("png") || fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileType.contains("jpg") || fileType.contains("jpeg") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
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

    private String safeFileName(String value) {
        return sanitizeName(StringUtils.hasText(value) ? value : "document.pdf");
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
