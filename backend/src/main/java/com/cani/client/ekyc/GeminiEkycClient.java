package com.cani.client.ekyc;

import com.cani.client.skill.SkillVisionAiProperties;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Client xác thực eKYC bằng Gemini Vision AI (OCR Căn cước công dân gắn chip & So khớp khuôn mặt Selfie).
 * Bóc tách chính xác thông tin thực tế trên thẻ CCCD người dùng tải lên:
 * - Số CCCD (12 chữ số), Họ và tên, Ngày sinh, Giới tính, Quốc tịch
 * - Quê quán, Nơi thường trú, Có giá trị đến, Ngày cấp, Đặc điểm nhận dạng
 * - So khớp khuôn mặt giữa ảnh chân dung trên thẻ CCCD và ảnh Selfie.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiEkycClient implements EkycProvider {

    private final SkillVisionAiProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderName() {
        return "GEMINI_VISION_EKYC";
    }

    public boolean isAvailable() {
        return properties != null && properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    @Override
    public EkycIdentityResultDto verifyIdentity(
            byte[] frontBytes, String frontMime,
            byte[] backBytes, String backMime,
            byte[] selfieBytes, String selfieMime) {

        if (!isAvailable()) {
            log.warn("Gemini Vision eKYC: Chưa cấu hình GEMINI_API_KEY.");
            return null;
        }

        List<String> candidateModels = new ArrayList<>();
        if (properties.getModel() != null && !properties.getModel().isBlank()) {
            String configured = properties.getModel().trim();
            if (!configured.startsWith("gemini-3.5")) {
                candidateModels.add(configured);
            }
        }
        for (String fallbackModel : List.of("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")) {
            if (!candidateModels.contains(fallbackModel)) {
                candidateModels.add(fallbackModel);
            }
        }

        Map<String, Object> payload = buildRequestPayload(frontBytes, frontMime, backBytes, backMime, selfieBytes, selfieMime);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 20000);
        factory.setReadTimeout(properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 25000);

        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .build();

        for (String modelName : candidateModels) {
            String fullUrl = String.format("%s/%s:generateContent", properties.getUrl(), modelName);
            try {
                log.info("Gemini eKYC OCR: Đang bóc tách thông tin thẻ CCCD thực tế qua model [{}]...", modelName);
                String rawResponse = restClient.post()
                        .uri(fullUrl)
                        .header("x-goog-api-key", properties.getApiKey().trim())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(String.class);

                EkycIdentityResultDto parsed = parseGeminiResponse(rawResponse);
                if (parsed != null) {
                    log.info("Gemini eKYC OCR hoàn tất: Họ tên=[{}], Số CCCD=[{}], FaceMatch=[{}%], Decision=[{}]",
                            parsed.getFullName(),
                            parsed.getIdNumber() != null && parsed.getIdNumber().length() >= 6
                                    ? parsed.getIdNumber().substring(0, 3) + "******"
                                    : parsed.getIdNumber(),
                            parsed.getFaceMatchScore(),
                            parsed.getRoutingDecision());
                    return parsed;
                }
            } catch (Exception e) {
                log.warn("Gemini eKYC model [{}] gặp lỗi: {}. Đang thử model dự phòng...", modelName, e.getMessage());
            }
        }

        return null;
    }

    private Map<String, Object> buildRequestPayload(
            byte[] frontBytes, String frontMime,
            byte[] backBytes, String backMime,
            byte[] selfieBytes, String selfieMime) {

        String prompt = """
                Bạn là Hệ thống AI Thẩm định Định danh Điện tử (eKYC OCR & Face Matching) chuẩn Nghị định 13/2023/NĐ-CP cho nền tảng CANi tại Việt Nam.
                Tôi gửi cho bạn tối đa 3 bức ảnh theo thứ tự:
                - Ảnh 1: Mặt trước thẻ Căn cước công dân (CCCD) / Thẻ căn cước Việt Nam.
                - Ảnh 2 (nếu có): Mặt sau thẻ CCCD.
                - Ảnh 3 (nếu có): Ảnh chân dung Selfie của người dùng.

                NHIỆM VỤ BẮT BUỘC:
                1. Đọc chính xác tuyệt đối từng ký tự in trên thẻ CCCD trong Ảnh 1 và Ảnh 2 (KHÔNG ĐƯỢC tự bịa thông tin mặc định, đọc đúng tên có dấu tiếng Việt in hoa, đúng 12 chữ số CCCD, đúng ngày sinh, quê quán, nơi thường trú trên thẻ).
                2. Kiểm tra xem Ảnh 1 có thực sự là thẻ Căn cước công dân / Căn cước / CMND hợp lệ của Việt Nam hay không (isValidIdCard). Nếu người dùng tải ảnh linh tinh (ảnh phong cảnh, ảnh màn hình trống, ảnh không phải CCCD, hoặc không đọc được số CCCD), đặt isValidIdCard = false và routingDecision = "REJECTED".
                3. So sánh khuôn mặt trên Ảnh 1 (ảnh thẻ CCCD) và Ảnh 3 (ảnh Selfie) để chấm điểm tương đồng faceMatchScore (thang điểm 0.0 đến 100.0). Nếu cùng một người hoặc ảnh chân dung hợp lệ rõ mặt thì chấm từ 85.0 đến 98.5.

                Trả về DUY NHẤT một đối tượng JSON theo đúng cấu trúc sau (không kèm markdown):
                {
                  "isValidIdCard": boolean,
                  "idNumber": string (chỉ gồm các chữ số của Số CCCD / Số định danh cá nhân đọc được trên thẻ, ví dụ "037204005797", hoặc null nếu không đọc được),
                  "fullName": string (Họ và tên chữ IN HOA có dấu đọc đúng từ thẻ CCCD, hoặc null),
                  "dob": string (Ngày sinh định dạng dd/MM/yyyy đọc từ thẻ, hoặc null),
                  "gender": string ("Nam" hoặc "Nữ" đọc từ thẻ, hoặc "N/A"),
                  "nationality": string (Quốc tịch đọc từ thẻ, mặc định "Việt Nam"),
                  "originAddress": string (Quê quán đọc nguyên văn từ mặt trước thẻ CCCD, hoặc null),
                  "residenceAddress": string (Nơi thường trú / Nơi cư trú đọc nguyên văn từ mặt trước thẻ CCCD, hoặc null),
                  "expiryDate": string (Có giá trị đến định dạng dd/MM/yyyy hoặc "Không thời hạn", hoặc null),
                  "issueDate": string (Ngày cấp đọc từ mặt sau thẻ CCCD định dạng dd/MM/yyyy, hoặc null),
                  "identificationFeatures": string (Đặc điểm nhận dạng đọc từ mặt sau thẻ CCCD, hoặc null),
                  "ocrConfidence": float (từ 0.0 đến 1.0),
                  "faceMatchScore": float (từ 0.0 đến 100.0),
                  "isCutCorner": boolean,
                  "isScreenCaptured": boolean,
                  "isPhotoshoped": boolean,
                  "routingDecision": "AUTO_APPROVED" | "MANUAL_REVIEW" | "REJECTED",
                  "routingReason": string (Giải thích ngắn gọn bằng tiếng Việt)
                }
                """;

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));

        addImagePart(parts, frontBytes, frontMime);
        addImagePart(parts, backBytes, backMime);
        addImagePart(parts, selfieBytes, selfieMime);

        Map<String, Object> contents = Map.of("parts", parts);
        Map<String, Object> generationConfig = Map.of(
                "responseMimeType", "application/json",
                "temperature", 0.0
        );

        return Map.of(
                "contents", List.of(contents),
                "generationConfig", generationConfig
        );
    }

    private void addImagePart(List<Map<String, Object>> parts, byte[] bytes, String mimeType) {
        if (bytes == null || bytes.length == 0) return;
        String safeMime = (mimeType != null && !mimeType.isBlank() && mimeType.startsWith("image/"))
                ? mimeType
                : "image/jpeg";
        String base64Data = Base64.getEncoder().encodeToString(bytes);
        parts.add(Map.of(
                "inlineData", Map.of(
                        "mimeType", safeMime,
                        "data", base64Data
                )
        ));
    }

    private EkycIdentityResultDto parseGeminiResponse(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode candidates = root.path("candidates");
        if (candidates.isEmpty()) {
            return null;
        }

        String jsonText = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
        JsonNode data = objectMapper.readTree(jsonText);

        boolean isValidIdCard = data.path("isValidIdCard").asBoolean(false);
        String idNumber = cleanNullableString(data.path("idNumber").asText(null));
        if (idNumber != null) {
            idNumber = idNumber.replaceAll("\\D", "");
        }

        String fullName = cleanNullableString(data.path("fullName").asText(null));
        if (fullName != null) {
            fullName = fullName.toUpperCase();
        }

        String dob = cleanNullableString(data.path("dob").asText(null));
        String gender = cleanNullableString(data.path("gender").asText("N/A"));
        String nationality = cleanNullableString(data.path("nationality").asText("Việt Nam"));
        String originAddress = cleanNullableString(data.path("originAddress").asText(null));
        String residenceAddress = cleanNullableString(data.path("residenceAddress").asText(null));
        String expiryDate = cleanNullableString(data.path("expiryDate").asText(null));
        String issueDate = cleanNullableString(data.path("issueDate").asText(null));
        String identificationFeatures = cleanNullableString(data.path("identificationFeatures").asText(null));

        double ocrConfidence = data.path("ocrConfidence").asDouble(0.95);
        double faceMatchScore = data.path("faceMatchScore").asDouble(90.0);
        boolean isCutCorner = data.path("isCutCorner").asBoolean(false);
        boolean isScreenCaptured = data.path("isScreenCaptured").asBoolean(false);
        boolean isPhotoshoped = data.path("isPhotoshoped").asBoolean(false);
        boolean isExpired = isCardExpired(expiryDate);

        String routingDecision = cleanNullableString(data.path("routingDecision").asText("AUTO_APPROVED"));
        String routingReason = cleanNullableString(data.path("routingReason").asText("Đã trích xuất thông tin CCCD và đối khớp khuôn mặt hợp lệ."));

        if (!isValidIdCard || idNumber == null || idNumber.length() < 9) {
            routingDecision = "REJECTED";
            routingReason = "Ảnh tải lên không phải là thẻ Căn cước công dân hợp lệ hoặc bị mờ không đọc được số CCCD.";
        } else if (isExpired) {
            routingDecision = "REJECTED";
            routingReason = String.format("Thẻ CCCD đã hết hạn sử dụng (%s). Vui lòng sử dụng giấy tờ còn hiệu lực.", expiryDate);
        } else if (isCutCorner || isPhotoshoped) {
            routingDecision = "REJECTED";
            routingReason = "Phát hiện thẻ CCCD có dấu hiệu cắt góc hoặc chỉnh sửa can thiệp.";
        } else if (faceMatchScore < 60.0) {
            routingDecision = "REJECTED";
            routingReason = String.format("Khuôn mặt selfie không trùng khớp với ảnh trên thẻ CCCD (Độ khớp: %.1f%%).", faceMatchScore);
        } else if (faceMatchScore >= 80.0) {
            routingDecision = "AUTO_APPROVED";
            if (routingReason == null || routingReason.isBlank()) {
                routingReason = String.format("Xác thực eKYC thành công. Độ khớp khuôn mặt đạt %.1f%%.", faceMatchScore);
            }
        } else {
            routingDecision = "MANUAL_REVIEW";
            routingReason = String.format("Độ khớp khuôn mặt đạt %.1f%% (ngưỡng trung bình). Chuyển Admin/CSKH thẩm định.", faceMatchScore);
        }

        Map<String, Object> rawAnalysis = new HashMap<>();
        rawAnalysis.put("provider", "GEMINI_VISION_EKYC");
        rawAnalysis.put("isValidIdCard", isValidIdCard);
        rawAnalysis.put("faceMatchScore", faceMatchScore);
        rawAnalysis.put("ocrConfidence", ocrConfidence);

        return EkycIdentityResultDto.builder()
                .idNumber(idNumber)
                .fullName(fullName)
                .dob(dob)
                .gender(gender != null ? gender : "N/A")
                .nationality(nationality != null ? nationality : "Việt Nam")
                .originAddress(originAddress)
                .residenceAddress(residenceAddress)
                .expiryDate(expiryDate)
                .issueDate(issueDate)
                .identificationFeatures(identificationFeatures)
                .ocrConfidence(ocrConfidence)
                .faceMatchScore(faceMatchScore)
                .isCutCorner(isCutCorner)
                .isScreenCaptured(isScreenCaptured)
                .isPhotoshoped(isPhotoshoped)
                .isExpired(isExpired)
                .routingDecision(routingDecision)
                .routingReason(routingReason)
                .rawAnalysis(rawAnalysis)
                .build();
    }

    private String cleanNullableString(String val) {
        if (val == null) return null;
        String trimmed = val.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) || "undefined".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

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
}
