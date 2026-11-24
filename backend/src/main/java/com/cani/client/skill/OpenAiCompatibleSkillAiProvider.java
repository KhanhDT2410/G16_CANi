package com.cani.client.skill;

import com.cani.service.skill.eval.DocumentExtractionResult;
import com.cani.service.skill.eval.DocumentExtractionResult.ExtractionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adapter gọi provider AI tương thích OpenAI / OCR Vision qua HTTP để trích xuất tài liệu PDF/ảnh.
 * Không dùng Gemini; từ chối cấu hình Gemini hoặc cấu hình chỉ có chuỗi apiKey mà thiếu kết nối xác thực.
 */
@Slf4j
@Component
public class OpenAiCompatibleSkillAiProvider implements SkillAiDocumentProvider {

    private static final Set<String> PLACEHOLDER_KEYS = Set.of(
            "replace_me", "your_api_key", "changeme", "none", "null", "test", "dummy"
    );

    private final SkillAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final AtomicBoolean liveConnectionConfirmed;

    @Autowired
    public OpenAiCompatibleSkillAiProvider(SkillAiProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, buildDefaultRestClient(properties.getTimeoutMs()));
    }

    public OpenAiCompatibleSkillAiProvider(
            SkillAiProperties properties,
            ObjectMapper objectMapper,
            RestClient restClient
    ) {
        this.properties = properties != null ? properties : new SkillAiProperties();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.restClient = restClient;
        this.liveConnectionConfirmed = new AtomicBoolean(this.properties.isConnectionVerified());
    }

    private static RestClient buildDefaultRestClient(int timeoutMs) {
        int clampedTimeout = Math.max(2000, timeoutMs);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(clampedTimeout);
        factory.setReadTimeout(clampedTimeout);
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean isReady() {
        return hasValidConfiguration() && liveConnectionConfirmed.get();
    }

    @Override
    public boolean hasValidConfiguration() {
        return getConfigurationIssue() == null;
    }

    @Override
    public boolean verifyConnection() {
        if (!hasValidConfiguration()) {
            liveConnectionConfirmed.set(false);
            return false;
        }
        try {
            URI endpointUri = URI.create(properties.getEndpoint().trim());
            URI probeUri = buildModelsProbeUri(endpointUri);
            restClient.get()
                    .uri(probeUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey().trim())
                    .retrieve()
                    .toBodilessEntity();
            liveConnectionConfirmed.set(true);
            return true;
        } catch (Exception ex) {
            log.warn("Không thể xác thực kết nối tới Skill AI provider '{}': {}", getProviderName(), ex.getMessage());
            liveConnectionConfirmed.set(false);
            return false;
        }
    }

    @Override
    public String getProviderName() {
        if (properties.getProvider() == null || properties.getProvider().isBlank()) {
            return "NONE";
        }
        return properties.getProvider().trim();
    }

    @Override
    public String getModelName() {
        if (!isReady() || properties.getModel() == null || properties.getModel().isBlank()) {
            return "NONE";
        }
        return properties.getModel().trim();
    }

    @Override
    public String getUnconfiguredReason() {
        String configIssue = getConfigurationIssue();
        if (configIssue != null) {
            return configIssue;
        }
        if (!liveConnectionConfirmed.get()) {
            return "UNCONFIGURED: Chuỗi API key đã khai báo nhưng kết nối tới provider chưa được xác nhận thực tế.";
        }
        return null;
    }

    @Override
    public DocumentExtractionResult extractDocument(
            byte[] fileBytes,
            String filename,
            String mimeType,
            String prompt,
            String promptVersion
    ) {
        String configIssue = getConfigurationIssue();
        if (configIssue != null) {
            return unconfiguredResult(promptVersion, configIssue);
        }
        if (!liveConnectionConfirmed.get() && !verifyConnection()) {
            return unconfiguredResult(
                    promptVersion,
                    "UNCONFIGURED: Không thể xác nhận kết nối tới provider AI thực tế (" + getProviderName() + ")."
            );
        }

        try {
            Map<String, Object> requestBody = buildChatCompletionPayload(fileBytes, filename, mimeType, prompt);
            String rawResponse = restClient.post()
                    .uri(properties.getEndpoint().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey().trim())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (rawResponse == null || rawResponse.isBlank()) {
                return DocumentExtractionResult.builder()
                        .status(ExtractionStatus.PARSE_ERROR)
                        .documentType("UNKNOWN")
                        .promptVersion(promptVersion)
                        .modelName(properties.getModel().trim())
                        .failureMessage("Provider AI trả về phản hồi rỗng.")
                        .build();
            }

            liveConnectionConfirmed.set(true);
            return parseProviderResponse(rawResponse, promptVersion);
        } catch (Exception ex) {
            log.warn("Lỗi khi gọi Skill AI provider '{}': {}", getProviderName(), ex.getMessage());
            liveConnectionConfirmed.set(false);
            return unconfiguredResult(
                    promptVersion,
                    "UNCONFIGURED: Kết nối provider AI thất bại hoặc credentials không hợp lệ: " + ex.getMessage()
            );
        }
    }

    private String getConfigurationIssue() {
        if (!properties.isEnabled()) {
            return "UNCONFIGURED: Tính năng AI trích xuất chứng chỉ đang tắt (cani.skill.ai.enabled=false).";
        }
        String provider = trimToEmpty(properties.getProvider());
        String endpoint = trimToEmpty(properties.getEndpoint());
        String apiKey = trimToEmpty(properties.getApiKey());
        String model = trimToEmpty(properties.getModel());

        if (isGeminiReference(provider) || isGeminiReference(endpoint) || isGeminiReference(model)) {
            return "UNCONFIGURED: Gemini không được phép sử dụng trong luồng kỹ năng.";
        }
        if (provider.isEmpty()) {
            return "UNCONFIGURED: Chưa cấu hình cani.skill.ai.provider.";
        }
        if (endpoint.isEmpty() || !isHttpUrl(endpoint)) {
            return "UNCONFIGURED: Chưa cấu hình cani.skill.ai.endpoint hợp lệ (http/https).";
        }
        if (apiKey.isEmpty() || PLACEHOLDER_KEYS.contains(apiKey.toLowerCase(Locale.ROOT))) {
            return "UNCONFIGURED: Chưa có credentials (cani.skill.ai.api-key) hợp lệ.";
        }
        if (model.isEmpty()) {
            return "UNCONFIGURED: Chưa cấu hình cani.skill.ai.model hỗ trợ ảnh/OCR.";
        }
        return null;
    }

    private Map<String, Object> buildChatCompletionPayload(
            byte[] fileBytes,
            String filename,
            String mimeType,
            String prompt
    ) {
        String normalizedMime = mimeType != null ? mimeType.toLowerCase(Locale.ROOT) : "";
        String base64 = Base64.getEncoder().encodeToString(fileBytes);

        List<Map<String, Object>> userParts = new ArrayList<>();
        userParts.add(Map.of("type", "text", "text", prompt));
        if ("application/pdf".equals(normalizedMime)) {
            String safeName = (filename == null || filename.isBlank()) ? "document.pdf" : filename;
            userParts.add(Map.of(
                    "type", "file",
                    "file", Map.of(
                            "filename", safeName,
                            "file_data", "data:application/pdf;base64," + base64
                    )
            ));
        } else {
            userParts.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", "data:" + normalizedMime + ";base64," + base64)
            ));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel().trim());
        payload.put("temperature", 0.0);
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", "Ban la he thong OCR & trich xuat du lieu cau truc. Chi tra ve JSON hop le, khong tu y phe duyet chung chi hay suy dien gia tri khong co trong tai lieu."
                ),
                Map.of("role", "user", "content", userParts)
        ));
        return payload;
    }

    private DocumentExtractionResult parseProviderResponse(String rawResponse, String promptVersion) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode contentNode = extractStructuredContentNode(root);
            if (contentNode == null || !contentNode.isObject()) {
                return DocumentExtractionResult.builder()
                        .status(ExtractionStatus.PARSE_ERROR)
                        .documentType("UNKNOWN")
                        .promptVersion(promptVersion)
                        .modelName(properties.getModel().trim())
                        .rawResponse(rawResponse)
                        .failureMessage("Phản hồi từ AI provider không chứa JSON đối tượng có cấu trúc.")
                        .build();
            }

            List<String> skills = readStringList(contentNode.get("skills"));
            if (skills.isEmpty()) {
                skills = readStringList(contentNode.get("extractedSkills"));
            }
            Map<String, String> evidence = readStringMap(contentNode.get("evidenceSnippets"));
            List<String> unreadable = readStringList(contentNode.get("unreadableFields"));
            List<String> missing = readStringList(contentNode.get("missingFields"));

            String docType = readNullableText(contentNode, "documentType");
            if (docType == null) {
                docType = "UNKNOWN";
            }
            ExtractionStatus status = "CORRUPTED".equalsIgnoreCase(docType)
                    ? ExtractionStatus.UNREADABLE_OR_CORRUPTED
                    : ExtractionStatus.SUCCESS;

            return DocumentExtractionResult.builder()
                    .status(status)
                    .documentType(docType)
                    .recipientName(readNullableText(contentNode, "recipientName"))
                    .institution(readNullableText(contentNode, "institution"))
                    .certificateNumber(readNullableText(contentNode, "certificateNumber"))
                    .expiryDate(readNullableText(contentNode, "expiryDate"))
                    .extractedSkills(skills)
                    .evidenceSnippets(evidence)
                    .unreadableFields(unreadable)
                    .missingFields(missing)
                    .promptVersion(promptVersion)
                    .modelName(properties.getModel().trim())
                    .rawResponse(rawResponse)
                    .build();
        } catch (Exception ex) {
            return DocumentExtractionResult.builder()
                    .status(ExtractionStatus.PARSE_ERROR)
                    .documentType("UNKNOWN")
                    .promptVersion(promptVersion)
                    .modelName(properties.getModel().trim())
                    .rawResponse(rawResponse)
                    .failureMessage("Không thể parse JSON từ phản hồi AI: " + ex.getMessage())
                    .build();
        }
    }

    private JsonNode extractStructuredContentNode(JsonNode root) throws Exception {
        if (root.has("documentType") || root.has("skills") || root.has("extractedSkills")) {
            return root;
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isTextual()) {
                String cleaned = stripMarkdownFences(content.asText());
                return objectMapper.readTree(cleaned);
            }
            if (content.isObject()) {
                return content;
            }
        }
        return null;
    }

    private String stripMarkdownFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private DocumentExtractionResult unconfiguredResult(String promptVersion, String reason) {
        return DocumentExtractionResult.builder()
                .status(ExtractionStatus.UNCONFIGURED)
                .documentType("UNKNOWN")
                .promptVersion(promptVersion)
                .modelName("NONE")
                .failureMessage(reason)
                .build();
    }

    private static URI buildModelsProbeUri(URI endpointUri) {
        String path = endpointUri.getPath() != null ? endpointUri.getPath() : "";
        String probePath = path.contains("/v1/")
                ? path.substring(0, path.indexOf("/v1/") + 4) + "models"
                : path;
        return URI.create(endpointUri.getScheme() + "://" + endpointUri.getAuthority() + probePath);
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean isGeminiReference(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("gemini") || lower.contains("generativelanguage.googleapis.com");
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String readNullableText(JsonNode node, String field) {
        JsonNode target = node.get(field);
        if (target == null || target.isNull()) {
            return null;
        }
        String text = target.asText("").trim();
        return text.isEmpty() ? null : text;
    }

    private static List<String> readStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (!value.isEmpty()) {
                    list.add(value);
                }
            }
        }
        return list;
    }

    private static Map<String, String> readStringMap(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (entry.getValue() != null && !entry.getValue().isNull()) {
                    String value = entry.getValue().asText("").trim();
                    if (!value.isEmpty()) {
                        map.put(entry.getKey(), value);
                    }
                }
            });
        }
        return map;
    }
}
