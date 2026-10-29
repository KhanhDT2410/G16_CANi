package com.cani.client.ekyc;

import com.cani.config.VnptEkycProperties;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client kết nối trực tiếp với cổng API VNPT eKYC (https://api.idg.vnpt.vn).
 * Thực thi chuỗi 3 bước tối ưu:
 * 1. Upload ảnh mặt trước và ảnh selfie lên MinIO VNPT lấy mã Hash (/file-service/v1/addFile)
 * 2. Trích xuất thông tin OCR và kiểm tra thẻ giả (/ai/v1/ocr/id/front)
 * 3. So khớp khuôn mặt chân dung với ảnh trên thẻ CCCD (/ai/v1/face/compare)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VnptEkycClient implements EkycProvider {

    private final VnptEkycProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderName() {
        return "VNPT_EKYC";
    }

    @Override
    public EkycIdentityResultDto verifyIdentity(
            byte[] frontBytes, String frontMime,
            byte[] backBytes, String backMime,
            byte[] selfieBytes, String selfieMime) {

        if (!properties.isEnabled() || properties.getAccessToken() == null || properties.getAccessToken().isBlank()) {
            log.warn("VNPT eKYC chưa được kích hoạt hoặc Access Token đang để trống. Chuyển hồ sơ sang MANUAL_REVIEW.");
            return buildFallbackResult("VNPT eKYC chưa bật hoặc Access Token chưa cấu hình.");
        }

        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(properties.getTimeoutMs());
            factory.setReadTimeout(properties.getTimeoutMs());

            RestClient restClient = RestClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .requestFactory(factory)
                    .build();

            // Bước 1: Upload ảnh mặt trước CCCD lên MinIO VNPT để lấy Hash
            log.info("VNPT eKYC: Đang tải ảnh mặt trước CCCD lên VNPT Storage...");
            String frontHash = uploadFile(restClient, frontBytes, "front_card.jpg", "Mặt trước CCCD", frontMime);
            if (frontHash == null || frontHash.isBlank()) {
                return buildFallbackResult("Không thể tải ảnh mặt trước CCCD lên VNPT Storage.");
            }

            // Bước 2: Upload ảnh selfie khuôn mặt lên MinIO VNPT để lấy Hash
            log.info("VNPT eKYC: Đang tải ảnh selfie khuôn mặt lên VNPT Storage...");
            String selfieHash = uploadFile(restClient, selfieBytes, "selfie_face.jpg", "Ảnh selfie khuôn mặt", selfieMime);
            if (selfieHash == null || selfieHash.isBlank()) {
                return buildFallbackResult("Không thể tải ảnh selfie khuôn mặt lên VNPT Storage.");
            }

            // Bước 3: Gọi API OCR mặt trước CCCD
            log.info("VNPT eKYC: Đang bóc tách thông tin OCR mặt trước CCCD (Hash: {})...", frontHash);
            JsonNode ocrResult = ocrFrontCard(restClient, frontHash);

            // Bước 4: Gọi API so khớp khuôn mặt Face Matching
            log.info("VNPT eKYC: Đang so khớp khuôn mặt thẻ CCCD và ảnh selfie...");
            JsonNode faceResult = compareFace(restClient, frontHash, selfieHash);

            // Bước 5: Phân tích kết quả tổng hợp
            return parseVnptResults(ocrResult, faceResult);

        } catch (Exception e) {
            log.error("Lỗi trong quá trình gọi VNPT eKYC API: {}", e.getMessage(), e);
            return buildFallbackResult("Dịch vụ VNPT eKYC tạm thời gián đoạn (" + e.getMessage() + "). Đã chuyển hồ sơ sang hàng đợi CSKH duyệt.");
        }
    }

    /**
     * Upload file ảnh lên MinIO VNPT: POST /file-service/v1/addFile
     */
    private String uploadFile(RestClient restClient, byte[] fileBytes, String fileName, String title, String mime) throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        ByteArrayResource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(mime != null && !mime.isBlank() ? MediaType.parseMediaType(mime) : MediaType.IMAGE_JPEG);
        body.add("file", new HttpEntity<>(resource, fileHeaders));
        body.add("title", title);
        body.add("description", title);

        String rawResponse = restClient.post()
                .uri("/file-service/v1/addFile")
                .header(HttpHeaders.AUTHORIZATION, getAuthHeader())
                .header("Token-id", properties.getTokenId().trim())
                .header("Token-key", properties.getTokenKey().trim())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);

        log.debug("VNPT uploadFile response: {}", rawResponse);
        JsonNode root = objectMapper.readTree(rawResponse);
        if (root.has("object") && root.get("object").has("hash")) {
            return root.get("object").get("hash").asText();
        }

        log.error("VNPT uploadFile không trả về mã hash hợp lệ. Phản hồi: {}", rawResponse);
        return null;
    }

    /**
     * OCR trích xuất thông tin mặt trước CCCD: POST /ai/v1/ocr/id/front
     */
    private JsonNode ocrFrontCard(RestClient restClient, String frontHash) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("img_front", frontHash);
        body.put("client_session", generateClientSession());
        body.put("type", -1);
        body.put("validate_postcode", true);
        body.put("token", generateRandomToken());

        String rawResponse = restClient.post()
                .uri("/ai/v1/ocr/id/front")
                .header(HttpHeaders.AUTHORIZATION, getAuthHeader())
                .header("Token-id", properties.getTokenId().trim())
                .header("Token-key", properties.getTokenKey().trim())
                .header("mac-address", properties.getMacAddress().trim())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        log.debug("VNPT OCR front response: {}", rawResponse);
        return objectMapper.readTree(rawResponse);
    }

    /**
     * So khớp khuôn mặt thẻ CCCD và Selfie: POST /ai/v1/face/compare
     */
    private JsonNode compareFace(RestClient restClient, String frontHash, String selfieHash) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("img_front", frontHash);
        body.put("img_face", selfieHash);
        body.put("client_session", generateClientSession());
        body.put("token", generateRandomToken());

        String rawResponse = restClient.post()
                .uri("/ai/v1/face/compare")
                .header(HttpHeaders.AUTHORIZATION, getAuthHeader())
                .header("Token-id", properties.getTokenId().trim())
                .header("Token-key", properties.getTokenKey().trim())
                .header("mac-address", properties.getMacAddress().trim())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        log.debug("VNPT Face compare response: {}", rawResponse);
        return objectMapper.readTree(rawResponse);
    }

    /**
     * Bóc tách JSON trả về từ VNPT và xác định Routing Decision
     */
    public EkycIdentityResultDto parseVnptResults(JsonNode ocrNode, JsonNode faceNode) {
        JsonNode ocrObj = ocrNode != null ? ocrNode.path("object") : null;
        JsonNode faceObj = faceNode != null ? faceNode.path("object") : null;

        String idNumber = ocrObj != null ? ocrObj.path("id").asText(null) : null;
        String fullName = ocrObj != null ? ocrObj.path("name").asText(null) : null;
        String dob = ocrObj != null ? ocrObj.path("birth_day").asText(null) : null;
        String gender = ocrObj != null ? ocrObj.path("gender").asText("N/A") : "N/A";
        String nationality = ocrObj != null ? ocrObj.path("nationality").asText("Việt Nam") : "Việt Nam";

        if ("-".equals(gender) || gender.isBlank()) gender = "N/A";
        if ("-".equals(nationality) || nationality.isBlank()) nationality = "Việt Nam";

        String originLocation = ocrObj != null ? ocrObj.path("origin_location").asText(null) : null;
        String recentLocation = ocrObj != null ? ocrObj.path("recent_location").asText(null) : null;
        String validDate = ocrObj != null ? ocrObj.path("valid_date").asText(null) : null;
        String issueDate = ocrObj != null ? ocrObj.path("issue_date").asText(null) : null;

        // Phân tích gian lận (Anti-fraud checks)
        boolean isLegal = true;
        if (ocrObj != null && ocrObj.has("tampering")) {
            String isLegalStr = ocrObj.path("tampering").path("is_legal").asText("yes");
            isLegal = "yes".equalsIgnoreCase(isLegalStr);
        }

        boolean isFake = false;
        if (ocrObj != null && ocrObj.has("id_fake_warning")) {
            isFake = "yes".equalsIgnoreCase(ocrObj.path("id_fake_warning").asText("no"));
        }

        boolean isCutCorner = false;
        boolean isBlurred = false;
        java.util.List<JsonNode> warningNodes = new java.util.ArrayList<>();
        if (ocrObj != null && ocrObj.has("warning") && ocrObj.get("warning").isArray()) {
            warningNodes.add(ocrObj.get("warning"));
        }
        if (ocrObj != null && ocrObj.has("tampering") && ocrObj.path("tampering").has("warning") && ocrObj.path("tampering").get("warning").isArray()) {
            warningNodes.add(ocrObj.path("tampering").get("warning"));
        }
        for (JsonNode arr : warningNodes) {
            for (JsonNode w : arr) {
                String text = w.asText();
                if (text.contains("mat_goc") || text.contains("cut")) isCutCorner = true;
                if (text.contains("mo_nhoe") || text.contains("blur")) isBlurred = true;
            }
        }

        // Tỉ lệ khuôn mặt
        double faceProb = 0.0;
        String faceMsg = "NOMATCH";
        if (faceObj != null) {
            faceProb = faceObj.path("prob").asDouble(0.0);
            faceMsg = faceObj.path("msg").asText("NOMATCH");
        }
        boolean isFaceMatched = "MATCH".equalsIgnoreCase(faceMsg) || faceProb >= 75.0;
        boolean isExpired = isCardExpired(validDate);

        // Routing Decision
        String routingDecision;
        String routingReason;

        if (!isLegal || isFake) {
            routingDecision = "REJECTED";
            routingReason = "Phát hiện giấy tờ có dấu hiệu bị can thiệp, chỉnh sửa hoặc cắt góc (VNPT Anti-fraud).";
        } else if (isExpired) {
            routingDecision = "REJECTED";
            routingReason = String.format("Thẻ CCCD đã hết hạn sử dụng (%s). Vui lòng sử dụng giấy tờ còn hiệu lực.", validDate);
        } else if (isFaceMatched && faceProb >= 80.0 && !isCutCorner) {
            routingDecision = "AUTO_APPROVED";
            routingReason = String.format("VNPT eKYC xác thực thành công. Độ khớp khuôn mặt đạt %.1f%%.", faceProb);
        } else if (isFaceMatched && faceProb >= 60.0) {
            routingDecision = "MANUAL_REVIEW";
            routingReason = String.format("Độ khớp khuôn mặt đạt %.1f%% (ngưỡng trung bình). Chuyển CSKH/Admin thẩm định.", faceProb);
        } else {
            routingDecision = "REJECTED";
            routingReason = String.format("Khuôn mặt selfie không trùng khớp với ảnh trên thẻ CCCD (Độ khớp: %.1f%%).", faceProb);
        }

        Map<String, Object> rawAnalysis = new HashMap<>();
        rawAnalysis.put("provider", "VNPT_EKYC");
        rawAnalysis.put("faceProbability", faceProb);
        rawAnalysis.put("faceMessage", faceMsg);
        rawAnalysis.put("isLegal", isLegal);
        rawAnalysis.put("isFakeWarning", isFake);
        rawAnalysis.put("isBlurred", isBlurred);
        rawAnalysis.put("isExpired", isExpired);

        return EkycIdentityResultDto.builder()
                .idNumber(idNumber)
                .fullName(fullName)
                .dob(dob)
                .gender(gender)
                .nationality(nationality)
                .originAddress(originLocation)
                .residenceAddress(recentLocation)
                .expiryDate(validDate)
                .issueDate(issueDate)
                .ocrConfidence(0.95)
                .faceMatchScore(faceProb)
                .isCutCorner(isCutCorner)
                .isScreenCaptured(isFake)
                .isPhotoshoped(!isLegal)
                .isExpired(isExpired)
                .routingDecision(routingDecision)
                .routingReason(routingReason)
                .rawAnalysis(rawAnalysis)
                .build();
    }

    /**
     * Kiểm tra thẻ CCCD đã hết hạn hay chưa dựa trên chuỗi valid_date của VNPT OCR.
     * Hỗ trợ định dạng dd/MM/yyyy, dd-MM-yyyy, yyyy-MM-dd và trường hợp "KHÔNG THỜI HẠN".
     */
    private boolean isCardExpired(String validDate) {
        if (validDate == null || validDate.isBlank() || "-".equals(validDate.trim())) {
            return false;
        }
        String normalized = validDate.trim().toUpperCase();
        if (normalized.contains("KHÔNG THỜI HẠN") || normalized.contains("KHONG THOI HAN") || normalized.contains("UNLIMITED")) {
            return false;
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );
        for (DateTimeFormatter fmt : formatters) {
            try {
                LocalDate expiry = LocalDate.parse(validDate.trim(), fmt);
                return expiry.isBefore(LocalDate.now());
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private String getAuthHeader() {
        String token = properties.getAccessToken() != null ? properties.getAccessToken().trim() : "";
        if (token.regionMatches(true, 0, "bearer ", 0, 7)) {
            return token;
        }
        return "Bearer " + token;
    }

    private String generateClientSession() {
        return "ANDROID_cani_dev_device_1_" + System.currentTimeMillis();
    }

    private String generateRandomToken() {
        return "cani" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private EkycIdentityResultDto buildFallbackResult(String reason) {
        return EkycIdentityResultDto.builder()
                .ocrConfidence(0.50)
                .faceMatchScore(0.0)
                .routingDecision("MANUAL_REVIEW")
                .routingReason(reason)
                .rawAnalysis(Map.of("provider", "VNPT_EKYC_FALLBACK", "reason", reason))
                .build();
    }
}
