package com.cani.client.skill;

import com.cani.client.skill.CertificateLookupSource.CertificateLookupOutcome;
import com.cani.client.skill.CertificateLookupSource.CertificateLookupRequest;
import com.cani.client.skill.CertificateLookupSource.CertificateLookupResult;
import com.cani.entity.Skill;
import com.cani.repository.SkillRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeminiSkillCertificateLookupSource — AI Vision Skill Certificate Extraction & Safety Tests")
class GeminiSkillCertificateLookupSourceTest {

    @Mock
    private SkillRepository skillRepository;

    private SkillGeminiProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new SkillGeminiProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-gemini-key");
        properties.setModel("gemini-2.5-flash");
    }

    private CertificateLookupRequest sampleRequest() {
        return CertificateLookupRequest.builder()
                .workerId(UUID.randomUUID())
                .workerFullName("Nguyễn Văn An")
                .documentName("bartender_cert.jpg")
                .mimeType("image/jpeg")
                .fileBytes("fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8))
                .requestedSkillName("Pha chế (Bartender/Barista)")
                .build();
    }

    private String wrapGeminiEnvelope(String innerJson) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(
                        Map.of("content", Map.of(
                                "parts", List.of(Map.of("text", innerJson))
                        ))
                )
        ));
    }

    @Test
    @DisplayName("Khi tắt AI hoặc thiếu API key -> supports()=false và router trả UNSUPPORTED_SOURCE trung thực")
    void verifyCertificate_ReturnsUnsupportedSource_WhenKeyMissingOrDisabled() {
        properties.setApiKey("");
        GeminiSkillCertificateLookupSource source = new GeminiSkillCertificateLookupSource(properties, objectMapper, skillRepository);
        assertFalse(source.supports(sampleRequest()));

        RoutingCertificateLookupSource router = new RoutingCertificateLookupSource(
                List.of(source),
                new UnsupportedCertificateLookupSource()
        );
        CertificateLookupResult res = router.verifyCertificate(sampleRequest());

        assertEquals(CertificateLookupOutcome.UNSUPPORTED_SOURCE, res.getOutcome());
        assertEquals(UnsupportedCertificateLookupSource.SOURCE_CODE, res.getVerificationSource());
    }

    @Test
    @DisplayName("Chứng chỉ hợp lệ vô thời hạn (kèm markdown fence) -> VERIFIED với permanentExpiry=true và taxonomy skills")
    void verifyCertificate_ReturnsVerified_ForValidPermanentCertificate() throws Exception {
        when(skillRepository.findByIsActiveTrue()).thenReturn(List.of(
                Skill.builder().name("Pha chế (Bartender/Barista)").isActive(true).build(),
                Skill.builder().name("Lễ tân / Check-in").isActive(true).build()
        ));

        String innerJson = """
                ```json
                {
                  "documentType": "CERTIFICATE",
                  "recipientName": "Nguyễn Văn An",
                  "institution": "Trường Trung cấp Saigontourist",
                  "certificateNumber": "SG-2026-009",
                  "issueDate": "10/05/2026",
                  "expiryDate": "VO_THOI_HAN",
                  "hasSealOrSignature": true,
                  "isAuthenticCertificate": true,
                  "matchedSkillNames": ["Pha chế (Bartender/Barista)"],
                  "overallConfidence": 0.94,
                  "rejectionReason": null
                }
                ```
                """;
        String envelope = wrapGeminiEnvelope(innerJson);

        GeminiSkillCertificateLookupSource source = new GeminiSkillCertificateLookupSource(properties, objectMapper, skillRepository) {
            @Override
            protected String executeGeminiCall(Map<String, Object> payload) {
                return envelope;
            }
        };

        RoutingCertificateLookupSource router = new RoutingCertificateLookupSource(
                List.of(source),
                new UnsupportedCertificateLookupSource()
        );
        CertificateLookupResult res = router.verifyCertificate(sampleRequest());

        assertEquals(CertificateLookupOutcome.VERIFIED, res.getOutcome());
        assertEquals(GeminiSkillCertificateLookupSource.SOURCE_CODE, res.getVerificationSource());
        assertEquals("Nguyễn Văn An", res.getRecipientName());
        assertEquals("Trường Trung cấp Saigontourist", res.getInstitution());
        assertEquals("SG-2026-009", res.getCertificateNumber());
        assertTrue(res.isPermanent());
        assertNull(res.getExpiryDate());
        assertEquals(List.of("Pha chế (Bartender/Barista)"), res.getMatchedSkillNames());
    }

    @Test
    @DisplayName("CV tự khai hoặc thiếu dấu mộc -> MISMATCH (CV tự khai) và vẫn giữ metadata trích xuất được")
    void verifyCertificate_ReturnsMismatch_WhenCvOrMissingSeal() throws Exception {
        String cvJson = """
                {
                  "documentType": "CV_SELF_DECLARED",
                  "recipientName": "Nguyễn Văn An",
                  "institution": null,
                  "certificateNumber": null,
                  "issueDate": null,
                  "expiryDate": null,
                  "hasSealOrSignature": false,
                  "isAuthenticCertificate": false,
                  "matchedSkillNames": ["Pha chế (Bartender/Barista)"],
                  "overallConfidence": 0.25,
                  "rejectionReason": "Là CV tự khai"
                }
                """;
        String envelope = wrapGeminiEnvelope(cvJson);

        GeminiSkillCertificateLookupSource source = new GeminiSkillCertificateLookupSource(properties, objectMapper, skillRepository) {
            @Override
            protected String executeGeminiCall(Map<String, Object> payload) {
                return envelope;
            }
        };

        CertificateLookupResult res = source.verifyCertificate(sampleRequest());

        assertEquals(CertificateLookupOutcome.MISMATCH, res.getOutcome());
        assertEquals(GeminiSkillCertificateLookupSource.SOURCE_CODE, res.getVerificationSource());
        assertEquals("CV_SELF_DECLARED", res.getFailureCode());
        assertEquals("Nguyễn Văn An", res.getRecipientName());
        assertTrue(res.getReason().contains("CV tự khai"));
    }

    @Test
    @DisplayName("Lỗi kết nối / timeout khi gọi Gemini -> TEMPORARY_FAILURE cho phép Worker thử lại")
    void verifyCertificate_ReturnsTemporaryFailure_WhenApiCallThrows() {
        GeminiSkillCertificateLookupSource source = new GeminiSkillCertificateLookupSource(properties, objectMapper, skillRepository) {
            @Override
            protected String executeGeminiCall(Map<String, Object> payload) {
                throw new RuntimeException("503 Service Unavailable");
            }
        };

        CertificateLookupResult res = source.verifyCertificate(sampleRequest());

        assertEquals(CertificateLookupOutcome.TEMPORARY_FAILURE, res.getOutcome());
        assertEquals(GeminiSkillCertificateLookupSource.SOURCE_CODE, res.getVerificationSource());
        assertEquals("GEMINI_TEMPORARY_ERROR", res.getFailureCode());
    }

    @Test
    @DisplayName("Tự động fallback sang model kế tiếp khi model chính lỗi 503/404")
    void verifyCertificate_FallsBackToNextModel_WhenPrimaryModelFails() throws Exception {
        properties.setModel("gemini-2.5-flash");
        String cvJson = """
                {
                  "documentType": "CV_SELF_DECLARED",
                  "recipientName": "PHẠM DUY HƯNG",
                  "institution": null,
                  "certificateNumber": null,
                  "issueDate": null,
                  "expiryDate": null,
                  "hasSealOrSignature": false,
                  "isAuthenticCertificate": false,
                  "matchedSkillNames": [],
                  "overallConfidence": 0.20,
                  "rejectionReason": "Là CV tự khai"
                }
                """;
        String envelope = wrapGeminiEnvelope(cvJson);
        List<String> calledUrls = new java.util.ArrayList<>();

        GeminiSkillCertificateLookupSource source = new GeminiSkillCertificateLookupSource(properties, objectMapper, skillRepository) {
            @Override
            protected String invokeSingleModel(
                    org.springframework.web.client.RestClient restClient,
                    String fullUrl,
                    String apiKey,
                    Map<String, Object> payload
            ) {
                calledUrls.add(fullUrl);
                if (calledUrls.size() == 1) {
                    throw new RuntimeException("503 Server Unavailable");
                }
                return envelope;
            }
        };

        CertificateLookupResult res = source.verifyCertificate(sampleRequest());

        assertEquals(2, calledUrls.size());
        assertFalse(calledUrls.get(0).contains("gemini-2.5-flash"));
        assertTrue(calledUrls.get(0).contains("gemini-flash-lite-latest"));
        assertEquals(CertificateLookupOutcome.MISMATCH, res.getOutcome());
        assertEquals("CV_SELF_DECLARED", res.getFailureCode());
        assertEquals("PHẠM DUY HƯNG", res.getRecipientName());
    }

    @Test
    @DisplayName("Chứng chỉ khóa học hợp lệ không in ngày hết hạn (expiryDate: null) -> VERIFIED với permanentExpiry=true để không bị từ chối oan")
    void verifyCertificate_ReturnsVerifiedPermanent_WhenCourseCertificateHasNullExpiryDate() throws Exception {
        String innerJson = """
                {
                  "documentType": "CERTIFICATE",
                  "recipientName": "Nguyễn Văn An",
                  "institution": "Trung tâm Đào tạo Pha chế Á Âu",
                  "certificateNumber": "AAU-2026-999",
                  "issueDate": "12/04/2026",
                  "expiryDate": null,
                  "hasSealOrSignature": true,
                  "isAuthenticCertificate": true,
                  "matchedSkillNames": ["Pha chế (Bartender/Barista)"],
                  "overallConfidence": 0.92,
                  "rejectionReason": null
                }
                """;
        String envelope = wrapGeminiEnvelope(innerJson);

        GeminiSkillCertificateLookupSource source = new GeminiSkillCertificateLookupSource(properties, objectMapper, skillRepository) {
            @Override
            protected String executeGeminiCall(Map<String, Object> payload) {
                return envelope;
            }
        };

        CertificateLookupResult res = source.verifyCertificate(sampleRequest());

        assertEquals(CertificateLookupOutcome.VERIFIED, res.getOutcome());
        assertTrue(res.isPermanent());
        assertNull(res.getExpiryDate());
        assertEquals(List.of("Pha chế (Bartender/Barista)"), res.getMatchedSkillNames());
    }
}
