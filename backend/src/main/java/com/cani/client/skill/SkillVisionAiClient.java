package com.cani.client.skill;

import com.cani.dto.skill.ExtractedSkillDto;
import com.cani.dto.skill.SkillVerificationAiResultDto;
import com.cani.entity.enums.RoutingDecision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Client tương tác với Gemini Vision API cho tầng Document Intelligence (V2).
 * Tuân thủ nghiêm ngặt cơ chế Fail-Closed:
 * - Thiếu API Key, timeout, lỗi 4xx/5xx hoặc JSON hỏng -> ném AiProviderException (KHÔNG tự bịa dữ liệu hay AUTO_APPROVE).
 * - Không hardcode ghi đè tên model, không ghi API Key hoặc PII vào log.
 */
@Component
@Slf4j
public class SkillVisionAiClient {

    private static final String DEFAULT_MODEL = "gemini-2.5-flash";

    private final SkillVisionAiProperties properties;
    private final ObjectMapper objectMapper;
    private final SkillVerificationPromptBuilder promptBuilder;
    private final RestClient customRestClient;

    @Autowired
    public SkillVisionAiClient(
            SkillVisionAiProperties properties,
            ObjectMapper objectMapper,
            SkillVerificationPromptBuilder promptBuilder
    ) {
        this(properties, objectMapper, promptBuilder, null);
    }

    public SkillVisionAiClient(SkillVisionAiProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new SkillVerificationPromptBuilder(), null);
    }

    SkillVisionAiClient(
            SkillVisionAiProperties properties,
            ObjectMapper objectMapper,
            SkillVerificationPromptBuilder promptBuilder,
            RestClient customRestClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
        this.customRestClient = customRestClient;
    }

    /**
     * Overload giữ tương thích ngược; khuyến nghị gọi bản đầy đủ có expectedWorkerName.
     */
    @Deprecated
    public SkillVerificationAiResultDto verifyDocument(byte[] fileBytes, String mimeType, List<String> taxonomySkills) {
        return verifyDocument(fileBytes, mimeType, taxonomySkills, null, null);
    }

    /**
     * Gọi Gemini Vision API phân tích tài liệu chứng chỉ với ngữ cảnh tên Worker và danh mục kỹ năng.
     */
    public SkillVerificationAiResultDto verifyDocument(
            byte[] fileBytes,
            String mimeType,
            List<String> taxonomySkills,
            String filename,
            String expectedWorkerName
    ) {
        String resolvedModel = resolveModelName();

        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            if (properties.isSandboxFallbackEnabled()) {
                log.warn("Gemini API key chưa được cấu hình và sandboxFallbackEnabled=true -> trả về kết quả sandbox PENDING_REVIEW (không bịa dữ liệu, không AUTO_APPROVE).");
                return buildSafeSandboxReviewResult(resolvedModel);
            }
            log.error("Gemini API key chưa được cấu hình. Kích hoạt Fail-Closed.");
            throw new AiProviderException(
                    "MISSING_API_KEY",
                    false,
                    "Hệ thống xác minh AI chưa được cấu hình khóa kết nối. Yêu cầu đã được lưu lại để kiểm tra hoặc thử lại sau."
            );
        }

        if (fileBytes == null || fileBytes.length == 0) {
            throw new AiProviderException(
                    "EMPTY_DOCUMENT",
                    false,
                    "Không có dữ liệu tệp để gửi tới hệ thống phân tích AI."
            );
        }

        String fullUrl = String.format("%s/%s:generateContent", properties.getUrl().trim(), resolvedModel);
        String prompt = promptBuilder.buildDocumentIntelligencePrompt(taxonomySkills, expectedWorkerName);
        Map<String, Object> requestPayload = buildRequestPayload(prompt, fileBytes, mimeType);

        try {
            RestClient restClient = getOrCreateRestClient();
            log.info("Sending document intelligence request to Vision AI [model={}, promptVersion={}]",
                    resolvedModel, SkillVerificationPromptBuilder.PROMPT_VERSION);

            String rawResponse = restClient.post()
                    .uri(fullUrl)
                    .header("x-goog-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            return parseAiResponse(rawResponse, resolvedModel);
        } catch (AiProviderException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            log.error("Vision AI trả về lỗi HTTP 4xx [status={}, model={}]", code, resolvedModel);
            boolean retryable = (code == 429 || code == 408);
            throw new AiProviderException(
                    "HTTP_" + code,
                    retryable,
                    "Dịch vụ phân tích AI từ chối yêu cầu (mã lỗi " + code + "). Hồ sơ chưa được phê duyệt và cần kiểm tra lại.",
                    e
            );
        } catch (HttpServerErrorException e) {
            int code = e.getStatusCode().value();
            log.error("Vision AI trả về lỗi HTTP 5xx [status={}, model={}]", code, resolvedModel);
            throw new AiProviderException(
                    "HTTP_" + code,
                    true,
                    "Dịch vụ phân tích AI đang tạm thời gián đoạn (mã lỗi " + code + "). Vui lòng thử lại sau.",
                    e
            );
        } catch (ResourceAccessException e) {
            log.error("Vision AI timeout hoặc lỗi kết nối mạng [model={}]: {}", resolvedModel, e.getMessage());
            throw new AiProviderException(
                    "API_TIMEOUT",
                    true,
                    "Kết nối tới dịch vụ phân tích AI bị quá thời gian chờ. Hệ thống đã ghi nhận để thử lại sau.",
                    e
            );
        } catch (Exception e) {
            log.error("Lỗi không xác định khi gọi Vision AI [model={}]: {}", resolvedModel, e.getClass().getSimpleName());
            throw new AiProviderException(
                    "UNEXPECTED_PROVIDER_ERROR",
                    true,
                    "Không thể hoàn tất phân tích tài liệu qua AI do sự cố kỹ thuật. Vui lòng thử lại sau.",
                    e
            );
        }
    }

    private RestClient getOrCreateRestClient() {
        if (customRestClient != null) {
            return customRestClient;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 20000;
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    private String resolveModelName() {
        String configured = properties.getModel();
        if (configured == null || configured.isBlank()) {
            return DEFAULT_MODEL;
        }
        return configured.trim();
    }

    private Map<String, Object> buildRequestPayload(String prompt, byte[] fileBytes, String mimeType) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));

        String base64Data = Base64.getEncoder().encodeToString(fileBytes);
        String safeMime = (mimeType != null && !mimeType.isBlank()) ? mimeType : "image/jpeg";
        parts.add(Map.of(
                "inlineData", Map.of(
                        "mimeType", safeMime,
                        "data", base64Data
                )
        ));

        Map<String, Object> contents = Map.of("parts", parts);
        Map<String, Object> generationConfig = Map.of(
                "responseMimeType", "application/json",
                "temperature", 0.1
        );

        return Map.of(
                "contents", List.of(contents),
                "generationConfig", generationConfig
        );
    }

    SkillVerificationAiResultDto parseAiResponse(String rawResponse, String resolvedModel) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new AiProviderException(
                    "EMPTY_RESPONSE",
                    true,
                    "Dịch vụ AI trả về phản hồi rỗng. Không thể xác minh tự động."
            );
        }

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new AiProviderException(
                        "EMPTY_CANDIDATES",
                        true,
                        "AI không trả về nội dung phân tích cho tài liệu này."
                );
            }

            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty() || !parts.get(0).has("text")) {
                throw new AiProviderException(
                        "MISSING_TEXT_PART",
                        true,
                        "Phản hồi từ AI thiếu dữ liệu phân tích JSON."
                );
            }

            String jsonText = stripMarkdownFences(parts.get(0).path("text").asText());
            JsonNode data = objectMapper.readTree(jsonText);
            if (!data.isObject()) {
                throw new AiProviderException(
                        "INVALID_JSON_OBJECT",
                        true,
                        "Định dạng JSON AI trả về không hợp lệ."
                );
            }

            List<ExtractedSkillDto> skills = parseExtractedSkills(data.path("extractedSkills"));

            String decisionStr = firstNonBlankText(data, "recommendedDecision", "routingDecision");
            RoutingDecision decision = parseRoutingDecision(decisionStr);

            boolean hasSeal = data.path("hasSeal").asBoolean(false);
            boolean hasSignature = data.path("hasSignature").asBoolean(false);
            boolean hasSealOrSignature = data.has("hasSealOrSignature")
                    ? data.path("hasSealOrSignature").asBoolean(false)
                    : (hasSeal || hasSignature);

            Double rawConfidence = readOptionalDouble(data, "aiConfidence", "overallConfidence");

            return SkillVerificationAiResultDto.builder()
                    .documentType(firstNonBlankText(data, "documentType"))
                    .documentReadable(data.has("documentReadable") ? data.path("documentReadable").asBoolean(true) : true)
                    .recipientName(readNullableText(data, "recipientName"))
                    .identityNumber(readNullableText(data, "identityNumber"))
                    .institution(firstNonBlankText(data, "issuerName", "institution"))
                    .certificateNumber(readNullableText(data, "certificateNumber"))
                    .issueDate(readNullableText(data, "issueDate"))
                    .expiryDate(readNullableText(data, "expiryDate"))
                    .verificationUrl(readNullableText(data, "verificationUrl"))
                    .hasSeal(hasSeal)
                    .hasSignature(hasSignature)
                    .hasSealOrSignature(hasSealOrSignature)
                    .hasVerificationQr(data.path("hasVerificationQr").asBoolean(false))
                    .hasVerificationUrl(data.path("hasVerificationUrl").asBoolean(false)
                            || readNullableText(data, "verificationUrl") != null)
                    .identityMatch(firstNonBlankText(data, "identityMatch"))
                    .documentAuthenticity(firstNonBlankText(data, "documentAuthenticity"))
                    .skillRelevance(firstNonBlankText(data, "skillRelevance"))
                    .riskFlags(readStringList(data.path("riskFlags")))
                    .missingInformation(readStringList(data.path("missingInformation")))
                    .overallConfidence(rawConfidence != null ? rawConfidence : 0.0)
                    .extractedSkills(skills)
                    .routingDecision(decision)
                    .routingReason(firstNonBlankText(data, "reasoningSummary", "routingReason"))
                    .modelName(resolvedModel)
                    .promptVersion(SkillVerificationPromptBuilder.PROMPT_VERSION)
                    .rawAiJson(jsonText)
                    .build();

        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("Không thể parse JSON phản hồi từ Vision AI: {}", e.getMessage());
            throw new AiProviderException(
                    "MALFORMED_JSON",
                    true,
                    "Phản hồi phân tích từ AI bị sai định dạng JSON. Không thể tự động phê duyệt.",
                    e
            );
        }
    }

    /**
     * Chỉ dùng khi sandboxFallbackEnabled=true trong môi trường thử nghiệm nội bộ.
     * Tuyệt đối KHÔNG tự tạo tên người, CCCD, tổ chức cấp hay AUTO_APPROVE.
     */
    private SkillVerificationAiResultDto buildSafeSandboxReviewResult(String resolvedModel) {
        return SkillVerificationAiResultDto.builder()
                .documentType("UNKNOWN")
                .documentReadable(false)
                .recipientName(null)
                .identityNumber(null)
                .institution(null)
                .certificateNumber(null)
                .issueDate(null)
                .expiryDate(null)
                .hasSeal(false)
                .hasSignature(false)
                .hasSealOrSignature(false)
                .hasVerificationQr(false)
                .hasVerificationUrl(false)
                .identityMatch("UNKNOWN")
                .documentAuthenticity("UNVERIFIED")
                .skillRelevance("NO_MATCH")
                .riskFlags(List.of("SANDBOX_MODE_NO_AI"))
                .missingInformation(List.of("AI_ANALYSIS_UNAVAILABLE"))
                .overallConfidence(0.0)
                .extractedSkills(Collections.emptyList())
                .routingDecision(RoutingDecision.REJECT)
                .routingReason("Chế độ sandbox đang bật (chưa cấu hình AI). Hồ sơ bị từ chối tự động để đảm bảo an toàn.")
                .modelName(resolvedModel + "-sandbox")
                .promptVersion(SkillVerificationPromptBuilder.PROMPT_VERSION)
                .rawAiJson("{\"sandbox\":true}")
                .build();
    }

    private List<ExtractedSkillDto> parseExtractedSkills(JsonNode skillsNode) {
        List<ExtractedSkillDto> skills = new ArrayList<>();
        if (skillsNode != null && skillsNode.isArray()) {
            for (JsonNode sn : skillsNode) {
                if (sn.isObject()) {
                    skills.add(ExtractedSkillDto.builder()
                            .skillName(sn.path("skillName").asText("N/A"))
                            .taxonomyRole(sn.path("taxonomyRole").asText("N/A"))
                            .confidenceScore(sn.path("confidenceScore").asDouble(0.0))
                            .evidenceSnippet(sn.path("evidenceSnippet").asText(""))
                            .build());
                }
            }
        }
        return skills;
    }

    private RoutingDecision parseRoutingDecision(String raw) {
        if (raw == null || raw.isBlank()) {
            return RoutingDecision.REJECT;
        }
        try {
            return RoutingDecision.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RoutingDecision.REJECT;
        }
    }

    private String stripMarkdownFences(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private String readNullableText(JsonNode node, String field) {
        JsonNode val = node.path(field);
        if (val.isMissingNode() || val.isNull()) {
            return null;
        }
        String text = val.asText(null);
        return (text == null || text.isBlank() || "null".equalsIgnoreCase(text.trim())) ? null : text.trim();
    }

    private String firstNonBlankText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = readNullableText(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Double readOptionalDouble(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode val = node.path(field);
            if (!val.isMissingNode() && !val.isNull() && val.isNumber()) {
                return val.asDouble();
            }
        }
        return null;
    }

    private List<String> readStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && !item.isNull()) {
                    String s = item.asText("").trim();
                    if (!s.isBlank()) {
                        list.add(s);
                    }
                }
            }
        }
        return list;
    }
}
