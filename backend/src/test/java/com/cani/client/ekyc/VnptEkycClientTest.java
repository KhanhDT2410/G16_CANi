package com.cani.client.ekyc;

import com.cani.config.VnptEkycProperties;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.cani.dto.ekyc.EkycIdentityResultDto.ExpiryDateStatus;
import com.cani.dto.ekyc.EkycIdentityResultDto.FaceCompareStatus;
import com.cani.entity.enums.EkycRoutingDecision;
import com.cani.entity.enums.EkycVerificationStatus;
import com.cani.exception.BadRequestException;
import com.cani.service.ekyc.EkycDecisionEngine;
import com.cani.service.ekyc.EkycIdentityCryptoService;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VnptEkycClientTest {

    private static final byte[] JPEG_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC);

    private VnptEkycProperties properties;
    private ObjectMapper objectMapper;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private VnptEkycClient vnptEkycClient;
    private EkycDecisionEngine decisionEngine;

    @BeforeEach
    void setUp() {
        properties = new VnptEkycProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://api.idg.vnpt.vn");
        properties.setTokenId("test-token-id");
        properties.setTokenKey("test-token-key");
        properties.setAccessToken("test-access-token");
        properties.setMacAddress("TEST_MAC");
        properties.setVerifyBackCard(true);

        objectMapper = new ObjectMapper();
        restClientBuilder = RestClient.builder().baseUrl(properties.getBaseUrl());
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        vnptEkycClient = new VnptEkycClient(properties, objectMapper, restClientBuilder.build(), FIXED_CLOCK);
        decisionEngine = new EkycDecisionEngine(properties, new EkycIdentityCryptoService(""));
    }

    @Test
    @DisplayName("VNPT trả kết quả hợp lệ (mặt trước + mặt sau + Face Compare MATCH) -> sẵn sàng AUTO_APPROVED và không bịa ocrConfidence")
    void shouldSucceedWhenVnptReturnsValidFrontBackAndFaceMatch() {
        stubUpload("front_hash");
        stubUpload("back_hash");
        stubUpload("selfie_hash");
        stubFrontOcr(validFrontOcrJson("15/08/2042"));
        stubBackOcr(validBackOcrJson());
        stubFaceCompare("""
                {
                  "message": "IDG-00000000",
                  "object": { "msg": "MATCH", "prob": 94.8 }
                }
                """);

        EkycIdentityResultDto result = vnptEkycClient.verifyIdentity(
                JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg");

        mockServer.verify();
        assertThat(result.isTechnicalFailure()).isFalse();
        assertThat(result.getIdNumber()).isEqualTo("079201012345");
        assertThat(result.getFullName()).isEqualTo("NGUYỄN VĂN AN");
        assertThat(result.getIssueDate()).isEqualTo("10/05/2021");
        assertThat(result.isBackCardVerified()).isTrue();
        assertThat(result.getExpiryStatus()).isEqualTo(ExpiryDateStatus.VALID);
        assertThat(result.getFaceCompareStatus()).isEqualTo(FaceCompareStatus.MATCH);
        assertThat(result.getFaceMatchScore()).isEqualTo(94.8);
        assertThat(result.getOcrConfidence()).isNull(); // Không tự bịa 0.95 khi VNPT không trả trường confidence

        EkycDecisionEngine.DecisionOutcome outcome = decisionEngine.evaluate(result, null, false, false);
        assertThat(outcome.getStatus()).isEqualTo(EkycVerificationStatus.APPROVED);
        assertThat(outcome.getRoutingDecision()).isEqualTo(EkycRoutingDecision.AUTO_APPROVED);
    }

    @Test
    @DisplayName("OCR thất bại (VNPT trả mã lỗi hoặc thiếu object) -> TECHNICAL_FAILURE, tuyệt đối không AUTO_APPROVED")
    void shouldReturnTechnicalFailureWhenOcrFails() {
        stubUpload("front_hash");
        stubUpload("back_hash");
        stubUpload("selfie_hash");
        stubFrontOcr("""
                { "message": "IDG-00010001", "object": {} }
                """);
        stubBackOcr(validBackOcrJson());
        stubFaceCompare("""
                { "message": "IDG-00000000", "object": { "msg": "MATCH", "prob": 95.0 } }
                """);

        EkycIdentityResultDto result = vnptEkycClient.verifyIdentity(
                JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg");

        assertThat(result.isTechnicalFailure()).isTrue();
        assertThat(result.getTechnicalErrorCode()).isEqualTo("VNPT_OCR_FAILED");

        EkycDecisionEngine.DecisionOutcome outcome = decisionEngine.evaluate(result, null, false, false);
        assertThat(outcome.getStatus()).isEqualTo(EkycVerificationStatus.TECHNICAL_FAILURE);
        assertThat(outcome.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("Thiếu ảnh CCCD mặt trước hoặc mặt sau -> từ chối đầu vào bằng BadRequestException trước khi gọi VNPT")
    void shouldRejectMissingFrontOrBackImage() {
        assertThatThrownBy(() -> vnptEkycClient.verifyIdentity(
                null, "image/jpeg", JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mặt trước");

        assertThatThrownBy(() -> vnptEkycClient.verifyIdentity(
                JPEG_BYTES, "image/jpeg", new byte[0], "image/jpeg", JPEG_BYTES, "image/jpeg"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mặt sau");
    }

    @Test
    @DisplayName("Face Compare không có kết quả (null) -> TECHNICAL_FAILURE, tuyệt đối không coi OCR thành công là APPROVED")
    void shouldFailWhenFaceCompareMissing() throws Exception {
        JsonNode frontNode = objectMapper.readTree(validFrontOcrJson("15/08/2042"));
        JsonNode backNode = objectMapper.readTree(validBackOcrJson());

        EkycIdentityResultDto result = vnptEkycClient.parseVnptResults(frontNode, backNode, null);

        assertThat(result.isTechnicalFailure()).isTrue();
        assertThat(result.getFaceCompareStatus()).isEqualTo(FaceCompareStatus.MISSING);
        assertThat(result.getTechnicalErrorCode()).isEqualTo("VNPT_FACE_COMPARE_MISSING");

        EkycDecisionEngine.DecisionOutcome outcome = decisionEngine.evaluate(result, null, false, false);
        assertThat(outcome.getStatus()).isEqualTo(EkycVerificationStatus.TECHNICAL_FAILURE);
        assertThat(outcome.getStatus()).isNotEqualTo(EkycVerificationStatus.APPROVED);
    }

    @Test
    @DisplayName("Face Compare timeout -> TECHNICAL_FAILURE với mã VNPT_FACE_COMPARE_TIMEOUT và không AUTO_APPROVED")
    void shouldReturnTechnicalFailureWhenFaceCompareTimesOut() {
        stubUpload("front_hash");
        stubUpload("back_hash");
        stubUpload("selfie_hash");
        stubFrontOcr(validFrontOcrJson("15/08/2042"));
        stubBackOcr(validBackOcrJson());
        mockServer.expect(requestTo("https://api.idg.vnpt.vn/ai/v1/face/compare"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out"));
                });

        EkycIdentityResultDto result = vnptEkycClient.verifyIdentity(
                JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg");

        assertThat(result.isTechnicalFailure()).isTrue();
        assertThat(result.getFaceCompareStatus()).isEqualTo(FaceCompareStatus.TIMEOUT);
        assertThat(result.getTechnicalErrorCode()).isEqualTo("VNPT_FACE_COMPARE_TIMEOUT");

        EkycDecisionEngine.DecisionOutcome outcome = decisionEngine.evaluate(result, null, false, false);
        assertThat(outcome.getStatus()).isEqualTo(EkycVerificationStatus.TECHNICAL_FAILURE);
        assertThat(outcome.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("Face Compare trả kết quả sai định dạng (prob không phải số / msg không hợp lệ) -> TECHNICAL_FAILURE")
    void shouldReturnTechnicalFailureWhenFaceCompareFormatInvalid() throws Exception {
        JsonNode frontNode = objectMapper.readTree(validFrontOcrJson("15/08/2042"));
        JsonNode backNode = objectMapper.readTree(validBackOcrJson());
        JsonNode malformedFaceNode = objectMapper.readTree("""
                { "message": "IDG-00000000", "object": { "msg": "MATCH", "prob": "ninety-five" } }
                """);

        EkycIdentityResultDto result = vnptEkycClient.parseVnptResults(frontNode, backNode, malformedFaceNode);

        assertThat(result.isTechnicalFailure()).isTrue();
        assertThat(result.getFaceCompareStatus()).isEqualTo(FaceCompareStatus.INVALID_FORMAT);
        assertThat(result.getFaceMatchScore()).isNull();

        JsonNode unknownMsgNode = objectMapper.readTree("""
                { "message": "IDG-00000000", "object": { "msg": "MAYBE", "prob": 0.9 } }
                """);
        EkycIdentityResultDto result2 = vnptEkycClient.parseVnptResults(frontNode, backNode, unknownMsgNode);
        assertThat(result2.isTechnicalFailure()).isTrue();
        assertThat(result2.getFaceCompareStatus()).isEqualTo(FaceCompareStatus.INVALID_FORMAT);
    }

    @Test
    @DisplayName("CCCD đã hết hạn -> expiryStatus=EXPIRED và DecisionEngine từ chối REJECTED")
    void shouldRejectWhenCccdExpired() throws Exception {
        JsonNode frontNode = objectMapper.readTree(validFrontOcrJson("01/01/2020"));
        JsonNode backNode = objectMapper.readTree(validBackOcrJson());
        JsonNode faceNode = objectMapper.readTree("""
                { "message": "IDG-00000000", "object": { "msg": "MATCH", "prob": 0.95 } }
                """);

        EkycIdentityResultDto result = vnptEkycClient.parseVnptResults(frontNode, backNode, faceNode);

        assertThat(result.getExpiryStatus()).isEqualTo(ExpiryDateStatus.EXPIRED);
        assertThat(result.isExpired()).isTrue();

        EkycDecisionEngine.DecisionOutcome outcome = decisionEngine.evaluate(result, null, false, false);
        assertThat(outcome.getStatus()).isEqualTo(EkycVerificationStatus.REJECTED);
        assertThat(outcome.getRoutingReason()).contains("hết hạn");
    }

    @Test
    @DisplayName("CCCD không đọc được ngày hết hạn -> expiryStatus=UNREADABLE và chuyển PENDING_REVIEW (không mặc định hợp lệ)")
    void shouldRouteToPendingReviewWhenExpiryDateUnreadable() throws Exception {
        JsonNode frontNode = objectMapper.readTree(validFrontOcrJson("-"));
        JsonNode backNode = objectMapper.readTree(validBackOcrJson());
        JsonNode faceNode = objectMapper.readTree("""
                { "message": "IDG-00000000", "object": { "msg": "MATCH", "prob": 92.0 } }
                """);

        EkycIdentityResultDto result = vnptEkycClient.parseVnptResults(frontNode, backNode, faceNode);

        assertThat(result.getExpiryStatus()).isEqualTo(ExpiryDateStatus.UNREADABLE);
        assertThat(result.isExpired()).isFalse();

        EkycDecisionEngine.DecisionOutcome outcome = decisionEngine.evaluate(result, null, false, false);
        assertThat(outcome.getStatus()).isEqualTo(EkycVerificationStatus.PENDING_REVIEW);
        assertThat(outcome.getRoutingDecision()).isEqualTo(EkycRoutingDecision.MANUAL_REVIEW);
        assertThat(outcome.getRoutingReason()).contains("ngày hết hạn");
    }

    @Test
    @DisplayName("Có fraud flags (cắt góc / tampering / giả mạo) -> REJECTED")
    void shouldRejectWhenFraudFlagsDetected() throws Exception {
        JsonNode frontNode = objectMapper.readTree("""
                {
                  "message": "IDG-00000000",
                  "object": {
                    "id": "079201012345",
                    "name": "LÊ VĂN GIẢ",
                    "valid_date": "15/08/2042",
                    "tampering": { "is_legal": "no", "warning": ["anh_dau_vao_mat_goc"] },
                    "id_fake_warning": "yes"
                  }
                }
                """);
        JsonNode backNode = objectMapper.readTree(validBackOcrJson());
        JsonNode faceNode = objectMapper.readTree("""
                { "message": "IDG-00000000", "object": { "msg": "MATCH", "prob": 95.0 } }
                """);

        EkycIdentityResultDto result = vnptEkycClient.parseVnptResults(frontNode, backNode, faceNode);

        assertThat(result.isDocumentLegal()).isFalse();
        assertThat(result.isFakeDocument()).isTrue();
        assertThat(result.isCutCorner()).isTrue();
        assertThat(result.isPhotoshoped()).isTrue();

        EkycDecisionEngine.DecisionOutcome outcome = decisionEngine.evaluate(result, null, false, false);
        assertThat(outcome.getStatus()).isEqualTo(EkycVerificationStatus.REJECTED);
    }

    @Test
    @DisplayName("Token không hợp lệ (HTTP 401) -> TECHNICAL_FAILURE với mã VNPT_AUTH_INVALID, không fallback Gemini/Mock")
    void shouldReturnTechnicalFailureWhenTokenInvalid() {
        mockServer.expect(requestTo("https://api.idg.vnpt.vn/file-service/v1/addFile"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"invalid_token\"}"));

        EkycIdentityResultDto result = vnptEkycClient.verifyIdentity(
                JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg");

        assertThat(result.isTechnicalFailure()).isTrue();
        assertThat(result.getTechnicalErrorCode()).isEqualTo("VNPT_AUTH_INVALID");

        PrimaryEkycProvider primary = new PrimaryEkycProvider(vnptEkycClient);
        assertThat(primary.getProviderName()).isEqualTo("VNPT_EKYC");
    }

    @Test
    @DisplayName("VNPT mất kết nối mạng -> TECHNICAL_FAILURE với mã VNPT_CONNECTION_ERROR")
    void shouldReturnTechnicalFailureWhenVnptDisconnected() {
        mockServer.expect(requestTo("https://api.idg.vnpt.vn/file-service/v1/addFile"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new ResourceAccessException("Connection refused", new ConnectException("Connection refused"));
                });

        EkycIdentityResultDto result = vnptEkycClient.verifyIdentity(
                JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg");

        assertThat(result.isTechnicalFailure()).isTrue();
        assertThat(result.getTechnicalErrorCode()).isEqualTo("VNPT_CONNECTION_ERROR");
        EkycDecisionEngine.DecisionOutcome outcome = decisionEngine.evaluate(result, null, false, false);
        assertThat(outcome.getStatus()).isEqualTo(EkycVerificationStatus.TECHNICAL_FAILURE);
        assertThat(outcome.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("VNPT chưa bật hoặc thiếu credentials -> TECHNICAL_FAILURE (không chuyển sang Mock hay Gemini)")
    void shouldReturnTechnicalFailureWhenDisabledOrMissingCredentials() {
        properties.setEnabled(false);
        EkycIdentityResultDto disabled = vnptEkycClient.verifyIdentity(
                JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg");
        assertThat(disabled.isTechnicalFailure()).isTrue();
        assertThat(disabled.getTechnicalErrorCode()).isEqualTo("VNPT_DISABLED");

        properties.setEnabled(true);
        properties.setAccessToken("");
        EkycIdentityResultDto missingCreds = vnptEkycClient.verifyIdentity(
                JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg", JPEG_BYTES, "image/jpeg");
        assertThat(missingCreds.isTechnicalFailure()).isTrue();
        assertThat(missingCreds.getTechnicalErrorCode()).isEqualTo("VNPT_CONFIG_INCOMPLETE");
    }

    private void stubUpload(String hash) {
        mockServer.expect(requestTo("https://api.idg.vnpt.vn/file-service/v1/addFile"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"message\":\"IDG-00000000\",\"object\":{\"hash\":\"" + hash + "\"}}",
                        MediaType.APPLICATION_JSON));
    }

    private void stubFrontOcr(String json) {
        mockServer.expect(requestTo("https://api.idg.vnpt.vn/ai/v1/ocr/id/front"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void stubBackOcr(String json) {
        mockServer.expect(requestTo("https://api.idg.vnpt.vn/ai/v1/ocr/id/back"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void stubFaceCompare(String json) {
        mockServer.expect(requestTo("https://api.idg.vnpt.vn/ai/v1/face/compare"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static String validFrontOcrJson(String validDate) {
        return """
                {
                  "message": "IDG-00000000",
                  "object": {
                    "id": "079201012345",
                    "name": "NGUYỄN VĂN AN",
                    "birth_day": "15/08/2002",
                    "gender": "Nam",
                    "nationality": "Việt Nam",
                    "origin_location": "Bến Nghé, Quận 1, TP. HCM",
                    "recent_location": "Linh Trung, TP. Thủ Đức, TP. HCM",
                    "valid_date": "%s",
                    "tampering": { "is_legal": "yes", "warning": [] },
                    "id_fake_warning": "no"
                  }
                }
                """.formatted(validDate);
    }

    private static String validBackOcrJson() {
        return """
                {
                  "message": "IDG-00000000",
                  "object": {
                    "issue_date": "10/05/2021",
                    "features": "Nốt ruồi C: 1cm sau đuôi mắt trái",
                    "tampering": { "is_legal": "yes", "warning": [] },
                    "id_fake_warning": "no"
                  }
                }
                """;
    }
}
