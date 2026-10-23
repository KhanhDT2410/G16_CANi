package com.cani.client.ekyc;

import com.cani.config.VnptEkycProperties;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VnptEkycClientTest {

    private VnptEkycProperties properties;
    private ObjectMapper objectMapper;
    private VnptEkycClient vnptEkycClient;

    @BeforeEach
    void setUp() {
        properties = new VnptEkycProperties();
        objectMapper = new ObjectMapper();
        vnptEkycClient = new VnptEkycClient(properties, objectMapper);
    }

    @Test
    @DisplayName("Tự động fallback về MANUAL_REVIEW khi VNPT chưa kích hoạt hoặc thiếu Access Token")
    void shouldFallbackWhenDisabledOrNoToken() {
        properties.setEnabled(false);
        properties.setAccessToken("");

        EkycIdentityResultDto result = vnptEkycClient.verifyIdentity(
                "fake_front".getBytes(), "image/jpeg",
                "fake_back".getBytes(), "image/jpeg",
                "fake_selfie".getBytes(), "image/jpeg"
        );

        assertThat(result).isNotNull();
        assertThat(result.getRoutingDecision()).isEqualTo("MANUAL_REVIEW");
        assertThat(result.getRoutingReason()).contains("chưa bật hoặc Access Token chưa cấu hình");
    }

    @Test
    @DisplayName("Parse phản hồi VNPT thành công: OCR đầy đủ và khuôn mặt khớp cao -> AUTO_APPROVED")
    void shouldParseSuccessfulVnptResults() throws Exception {
        String ocrJson = """
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
                        "valid_date": "15/08/2042",
                        "issue_date": "10/05/2021",
                        "tampering": {
                            "is_legal": "yes",
                            "warning": []
                        },
                        "id_fake_warning": "no"
                    }
                }
                """;

        String faceJson = """
                {
                    "message": "IDG-00000000",
                    "object": {
                        "msg": "MATCH",
                        "prob": 94.8,
                        "result": "Khuôn mặt khớp"
                    }
                }
                """;

        JsonNode ocrNode = objectMapper.readTree(ocrJson);
        JsonNode faceNode = objectMapper.readTree(faceJson);

        EkycIdentityResultDto result = vnptEkycClient.parseVnptResults(ocrNode, faceNode);

        assertThat(result).isNotNull();
        assertThat(result.getIdNumber()).isEqualTo("079201012345");
        assertThat(result.getFullName()).isEqualTo("NGUYỄN VĂN AN");
        assertThat(result.getDob()).isEqualTo("15/08/2002");
        assertThat(result.getFaceMatchScore()).isEqualTo(94.8);
        assertThat(result.getRoutingDecision()).isEqualTo("AUTO_APPROVED");
        assertThat(result.getRoutingReason()).contains("VNPT eKYC xác thực thành công");
        assertThat(result.isCutCorner()).isFalse();
        assertThat(result.isPhotoshoped()).isFalse();
    }

    @Test
    @DisplayName("Từ chối REJECTED khi phát hiện thẻ CCCD bị can thiệp cắt góc hoặc chỉnh sửa (tampering = no)")
    void shouldRejectWhenTamperingDetected() throws Exception {
        String ocrJson = """
                {
                    "message": "IDG-00000000",
                    "object": {
                        "id": "079201012345",
                        "name": "LÊ VĂN GIẢ",
                        "tampering": {
                            "is_legal": "no",
                            "warning": ["anh_dau_vao_mat_goc"]
                        },
                        "id_fake_warning": "yes"
                    }
                }
                """;

        String faceJson = """
                {
                    "message": "IDG-00000000",
                    "object": {
                        "msg": "MATCH",
                        "prob": 90.0
                    }
                }
                """;

        JsonNode ocrNode = objectMapper.readTree(ocrJson);
        JsonNode faceNode = objectMapper.readTree(faceJson);

        EkycIdentityResultDto result = vnptEkycClient.parseVnptResults(ocrNode, faceNode);

        assertThat(result.getRoutingDecision()).isEqualTo("REJECTED");
        assertThat(result.getRoutingReason()).contains("Phát hiện giấy tờ có dấu hiệu bị can thiệp");
        assertThat(result.isCutCorner()).isTrue();
        assertThat(result.isPhotoshoped()).isTrue();
    }

    @Test
    @DisplayName("Từ chối REJECTED khi khuôn mặt không khớp (NOMATCH hoặc prob thấp)")
    void shouldRejectWhenFaceMismatch() throws Exception {
        String ocrJson = """
                {
                    "message": "IDG-00000000",
                    "object": {
                        "id": "079201012345",
                        "name": "NGUYỄN VĂN AN",
                        "tampering": {
                            "is_legal": "yes"
                        }
                    }
                }
                """;

        String faceJson = """
                {
                    "message": "IDG-00000000",
                    "object": {
                        "msg": "NOMATCH",
                        "prob": 35.2,
                        "result": "Khuôn mặt không khớp"
                    }
                }
                """;

        JsonNode ocrNode = objectMapper.readTree(ocrJson);
        JsonNode faceNode = objectMapper.readTree(faceJson);

        EkycIdentityResultDto result = vnptEkycClient.parseVnptResults(ocrNode, faceNode);

        assertThat(result.getRoutingDecision()).isEqualTo("REJECTED");
        assertThat(result.getRoutingReason()).contains("Khuôn mặt selfie không trùng khớp");
    }
}
