package com.example.extraction.ocr;

import com.example.extraction.common.BusinessException;
import com.example.extraction.configuration.dto.ConfigWizardPayload;
import com.example.extraction.model.domain.OcrEngineConfigRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class MinerUClient implements OcrEngineClient {
    private final ObjectMapper objectMapper;

    public MinerUClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String adapterType, String engineType, String provider, String engineCode) {
        if ("MINERU".equalsIgnoreCase(nullToEmpty(adapterType))) {
            return true;
        }
        String joined = (nullToEmpty(adapterType) + " " + nullToEmpty(engineType) + " " + nullToEmpty(provider) + " " + nullToEmpty(engineCode)).toLowerCase();
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
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(
                    multipartBody(request.getInputFileName(), fileContent, contentType(request), request), headers);
            ResponseEntity<String> httpResponse = restTemplate(engine).postForEntity(engine.getBaseUrl(), entity, String.class);
            OcrParseResponse result = parseResponseBody(httpResponse.getBody());
            result.setEngineCode(engine.getEngineCode());
            result.setDurationMs(System.currentTimeMillis() - begin);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            throw new BusinessException("OCR_HTTP_" + e.getStatusCode().value(),
                    "MinerU 调用失败：HTTP " + e.getStatusCode().value() + responseBodyMessage(e.getResponseBodyAsString()));
        } catch (IOException e) {
            throw new BusinessException("OCR_IO_ERROR", "MinerU 文件读取或响应解析失败：" + e.getMessage());
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

    private MultiValueMap<String, Object> multipartBody(String fileName, byte[] content, String contentType, OcrParseRequest request) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", filePart(fileName, contentType, content));
        body.add("backend", textParam(request, "backend", "pipeline"));
        addRepeatedTextPart(body, "lang_list", param(request, "lang_list"), List.of("ch", "en"));
        body.add("parse_method", textParam(request, "parse_method", "auto"));
        body.add("formula_enable", textParam(request, "formula_enable", "true"));
        body.add("table_enable", textParam(request, "table_enable", "true"));
        body.add("return_md", textParam(request, "return_md", "true"));
        body.add("return_middle_json", textParam(request, "return_middle_json", "false"));
        body.add("response_format_zip", textParam(request, "response_format_zip", "false"));
        body.add("return_images", textParam(request, "return_images", "true"));
        return body;
    }

    private HttpEntity<ByteArrayResource> filePart(String fileName, String contentType, byte[] content) {
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return safeFileName(fileName);
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        return new HttpEntity<>(resource, headers);
    }

    private void addRepeatedTextPart(MultiValueMap<String, Object> body, String name, Object value, List<String> defaults) {
        if (value instanceof Iterable<?> items) {
            boolean added = false;
            for (Object item : items) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    body.add(name, String.valueOf(item));
                    added = true;
                }
            }
            if (added) {
                return;
            }
        } else if (value != null && StringUtils.hasText(String.valueOf(value))) {
            for (String item : String.valueOf(value).split(",")) {
                if (StringUtils.hasText(item)) {
                    body.add(name, item.trim());
                }
            }
            return;
        }
        for (String item : defaults) {
            body.add(name, item);
        }
    }

    private String contentType(OcrParseRequest request) {
        String fileType = nullToEmpty(request.getFileType()).toLowerCase();
        String fileName = nullToEmpty(request.getInputFileName()).toLowerCase();
        if (fileType.contains("pdf") || fileName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (fileType.contains("wordprocessingml") || fileName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (fileType.contains("presentationml") || fileName.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        if (fileType.contains("spreadsheetml") || fileName.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (fileType.contains("jp2") || fileType.contains("jpeg2000") || fileName.endsWith(".jp2")) {
            return "image/jp2";
        }
        if (fileType.contains("webp") || fileName.endsWith(".webp")) {
            return "image/webp";
        }
        if (fileType.contains("gif") || fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileType.contains("bmp") || fileName.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (fileType.contains("tiff") || fileType.contains("tif") || fileName.endsWith(".tif") || fileName.endsWith(".tiff")) {
            return "image/tiff";
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

    private RestTemplate restTemplate(OcrEngineConfigRecord engine) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = timeoutSeconds(engine, 360);
        factory.setConnectTimeout(Duration.ofSeconds(Math.min(timeout, 30)));
        factory.setReadTimeout(Duration.ofSeconds(timeout));
        return new RestTemplate(factory);
    }

    private String responseBodyMessage(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        String compact = responseBody.replaceAll("\\s+", " ").trim();
        if (compact.length() > 2000) {
            compact = compact.substring(0, 2000) + "...";
        }
        return "，响应内容：" + compact;
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
        if (lower.endsWith(".jp2")) {
            return "image/jp2";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) {
            return "image/tiff";
        }
        return fallback;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
