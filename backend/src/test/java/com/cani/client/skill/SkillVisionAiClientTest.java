package com.cani.client.skill;

import com.cani.dto.skill.SkillVerificationAiResultDto;
import com.cani.entity.enums.RoutingDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("SkillVisionAiClient — Fail-Closed, Error Handling & Prompt V2 Tests")
class SkillVisionAiClientTest {

    private ObjectMapper objectMapper;
    private SkillVerificationPromptBuilder promptBuilder;
    private SkillVisionAiProperties properties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        promptBuilder = new SkillVerificationPromptBuilder();
        properties = new SkillVisionAiProperties();
        properties.setUrl("https://generativelanguage.googleapis.com/v1beta/models");
        properties.setModel("gemini-2.5-flash");
        properties.setTimeoutMs(5000);
        properties.setSandboxFallbackEnabled(false);
    }

    private SkillVisionAiClient buildClientWithMockServer(RestClient.Builder builder) {
        return new SkillVisionAiClient(properties, objectMapper, promptBuilder, builder.build());
    }

    @Test
    @DisplayName("Thiếu API Key -> Fail-Closed ném AiProviderException(MISSING_API_KEY), KHÔNG bịa AUTO_APPROVE")
    void verifyDocument_MissingApiKey_ThrowsFailClosed() {
        properties.setApiKey("");
        SkillVisionAiClient client = new SkillVisionAiClient(properties, objectMapper, promptBuilder);

        AiProviderException ex = assertThrows(AiProviderException.class, () ->
                client.verifyDocument(new byte[]{1, 2, 3}, "image/jpeg", List.of("Pha chế"), "cert.jpg", "Phạm Duy Hưng"));

        assertEquals("MISSING_API_KEY", ex.getErrorCode());
        assertFalse(ex.isRetryable());
    }

    @Test
    @DisplayName("Chế độ sandbox=true khi thiếu key -> chỉ trả về REJECT, KHÔNG bịa tên/tổ chức/AUTO_APPROVE")
    void verifyDocument_SandboxEnabled_ReturnsAdminReviewWithNoFabrication() {
        properties.setApiKey("");
        properties.setSandboxFallbackEnabled(true);
        SkillVisionAiClient client = new SkillVisionAiClient(properties, objectMapper, promptBuilder);

        SkillVerificationAiResultDto result = client.verifyDocument(
                new byte[]{1, 2, 3}, "image/jpeg", List.of("Pha chế"), "cert.jpg", "Phạm Duy Hưng");

        assertEquals(RoutingDecision.REJECT, result.getRoutingDecision());
        assertNull(result.getRecipientName());
        assertNull(result.getInstitution());
        assertNull(result.getIdentityNumber());
        assertEquals(0.0, result.getOverallConfidence());
    }

    @Test
    @DisplayName("API trả lỗi HTTP 4xx (404/400) -> ném AiProviderException, KHÔNG fallback AUTO_APPROVE")
    void verifyDocument_Http4xx_ThrowsAiProviderException() {
        properties.setApiKey("test-key-secret");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key-secret"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"error\":\"model not found\"}"));

        SkillVisionAiClient client = buildClientWithMockServer(builder);

        AiProviderException ex = assertThrows(AiProviderException.class, () ->
                client.verifyDocument(new byte[]{1, 2, 3}, "image/jpeg", List.of("Pha chế"), "cert.jpg", "Phạm Duy Hưng"));

        assertEquals("HTTP_404", ex.getErrorCode());
        assertFalse(ex.isRetryable());
        assertFalse(ex.getUserSafeReason().contains("test-key-secret"));
        server.verify();
    }

    @Test
    @DisplayName("API trả lỗi HTTP 5xx (503) -> ném AiProviderException retryable=true")
    void verifyDocument_Http5xx_ThrowsRetryableException() {
        properties.setApiKey("test-key-secret");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("Service Unavailable"));

        SkillVisionAiClient client = buildClientWithMockServer(builder);

        AiProviderException ex = assertThrows(AiProviderException.class, () ->
                client.verifyDocument(new byte[]{1, 2, 3}, "image/jpeg", List.of("Pha chế"), "cert.jpg", "Phạm Duy Hưng"));

        assertEquals("HTTP_503", ex.getErrorCode());
        assertTrue(ex.isRetryable());
        server.verify();
    }

    @Test
    @DisplayName("API Timeout -> ném AiProviderException(API_TIMEOUT, retryable=true)")
    void verifyDocument_Timeout_ThrowsRetryableException() {
        properties.setApiKey("test-key-secret");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out"));
                });

        SkillVisionAiClient client = buildClientWithMockServer(builder);

        AiProviderException ex = assertThrows(AiProviderException.class, () ->
                client.verifyDocument(new byte[]{1, 2, 3}, "image/jpeg", List.of("Pha chế"), "cert.jpg", "Phạm Duy Hưng"));

        assertEquals("API_TIMEOUT", ex.getErrorCode());
        assertTrue(ex.isRetryable());
    }

    @Test
    @DisplayName("Model trả JSON sai định dạng -> ném AiProviderException(MALFORMED_JSON)")
    void verifyDocument_MalformedJson_ThrowsException() {
        properties.setApiKey("test-key-secret");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String badGeminiPayload = """
                {
                  "candidates": [
                    { "content": { "parts": [ { "text": "NOT A VALID JSON {{{" } ] } }
                  ]
                }
                """;

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andRespond(withSuccess(badGeminiPayload, MediaType.APPLICATION_JSON));

        SkillVisionAiClient client = buildClientWithMockServer(builder);

        AiProviderException ex = assertThrows(AiProviderException.class, () ->
                client.verifyDocument(new byte[]{1, 2, 3}, "image/jpeg", List.of("Pha chế"), "cert.jpg", "Phạm Duy Hưng"));

        assertEquals("MALFORMED_JSON", ex.getErrorCode());
        assertTrue(ex.isRetryable());
    }

    @Test
    @DisplayName("Model trả JSON V2 hợp lệ (thiếu trường tùy chọn trả về null, không bịa) -> parse đầy đủ V2 fields")
    void verifyDocument_ValidV2JsonWithNullOptionals_ParsesCleanly() {
        properties.setApiKey("test-key-secret");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String innerJson = """
                {
                  "documentType": "CERTIFICATE",
                  "documentReadable": true,
                  "recipientName": "Phạm Duy Hưng",
                  "issuerName": "Trường Trung cấp Du lịch Saigontourist",
                  "certificateNumber": null,
                  "issueDate": "2025-08-15",
                  "expiryDate": null,
                  "verificationUrl": null,
                  "extractedSkills": [
                    {
                      "skillName": "Barista",
                      "taxonomyRole": "Pha chế (Bartender/Barista)",
                      "confidenceScore": 0.94,
                      "evidenceSnippet": "Hoàn thành khóa Barista"
                    }
                  ],
                  "hasSeal": true,
                  "hasSignature": true,
                  "hasVerificationQr": false,
                  "hasVerificationUrl": false,
                  "identityMatch": "EXACT",
                  "documentAuthenticity": "LIKELY_GENUINE_FORMAT",
                  "skillRelevance": "MATCH",
                  "riskFlags": [],
                  "missingInformation": ["certificateNumber"],
                  "aiConfidence": 0.93,
                  "recommendedDecision": "AUTO_APPROVE",
                  "reasoningSummary": "Đọc rõ họ tên, tổ chức cấp và dấu mộc."
                }
                """;
        String envelope;
        try {
            envelope = objectMapper.writeValueAsString(java.util.Map.of(
                    "candidates", List.of(java.util.Map.of(
                            "content", java.util.Map.of(
                                    "parts", List.of(java.util.Map.of("text", innerJson))
                            )
                    ))
            ));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andRespond(withSuccess(envelope, MediaType.APPLICATION_JSON));

        SkillVisionAiClient client = buildClientWithMockServer(builder);

        SkillVerificationAiResultDto result = client.verifyDocument(
                new byte[]{1, 2, 3}, "image/jpeg", List.of("Pha chế (Bartender/Barista)"), "cert.jpg", "Phạm Duy Hưng");

        assertEquals("CERTIFICATE", result.getDocumentType());
        assertTrue(result.getDocumentReadable());
        assertEquals("Phạm Duy Hưng", result.getRecipientName());
        assertEquals("Trường Trung cấp Du lịch Saigontourist", result.getInstitution());
        assertNull(result.getCertificateNumber());
        assertNull(result.getExpiryDate());
        assertTrue(result.getHasSealOrSignature());
        assertEquals(0.93, result.getOverallConfidence(), 0.001);
        assertEquals("gemini-2.5-flash", result.getModelName());
        assertEquals(SkillVerificationPromptBuilder.PROMPT_VERSION, result.getPromptVersion());
        assertNotNull(result.getRawAiJson());
    }
}
