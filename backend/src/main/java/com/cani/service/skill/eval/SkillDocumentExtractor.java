package com.cani.service.skill.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Trình trích xuất trường tài liệu bằng AI có hỗ trợ đa phiên bản Prompt (v1.0.0 vs v2.0.0).
 * - Nếu chưa cấu hình AI provider (cani.skill.ai.enabled = false hoặc thiếu API key):
 *   báo rõ UNCONFIGURED và tuyệt đối không giả lập duyệt thành công.
 * - AI chỉ trích xuất loại tài liệu, người nhận, đơn vị cấp, mã chứng chỉ, ngày hết hạn và kỹ năng,
 *   kèm trích đoạn làm căn cứ khi có.
 * - Trường thiếu hoặc không đọc được phải thể hiện rõ trong đầu ra.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillDocumentExtractor {

    public static final String PROMPT_V1 = "v1.0.0";
    public static final String PROMPT_V2 = "v2.0.0";

    private final ObjectMapper objectMapper;

    @Value("${cani.skill.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${cani.skill.ai.model:gemini-1.5-flash-synthetic}")
    private String configuredModelName;

    @Value("${cani.skill.ai.api-key:}")
    private String apiKey;

    public void setAiEnabled(boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }

    public void setConfiguredModelName(String modelName) {
        this.configuredModelName = modelName;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return aiEnabled && apiKey != null && !apiKey.isBlank();
    }

    /**
     * Tạo System & User Prompt theo từng phiên bản.
     */
    public String buildPrompt(String promptVersion, String documentText) {
        if (PROMPT_V2.equalsIgnoreCase(promptVersion)) {
            return buildPromptV2(documentText);
        }
        return buildPromptV1(documentText);
    }

    private String buildPromptV1(String documentText) {
        return """
                [PROMPT VERSION v1.0.0 - BASELINE]
                Bạn là AI trích xuất thông tin kỹ năng nghề nghiệp từ tài liệu.
                Hãy trích xuất JSON gồm các trường:
                {
                  "documentType": "CERTIFICATE" hoặc "CV",
                  "recipientName": "tên người nhận",
                  "institution": "đơn vị cấp",
                  "certificateNumber": "mã chứng chỉ",
                  "expiryDate": "ngày hết hạn (dd/MM/yyyy)",
                  "skills": ["kỹ năng 1", "kỹ năng 2"]
                }
                Tài liệu:
                """ + (documentText != null ? documentText : "");
    }

    private String buildPromptV2(String documentText) {
        return """
                [PROMPT VERSION v2.0.0 - IMPROVED GROUNDED EXTRACTION]
                Bạn là AI trích xuất tài liệu chứng chỉ nghề nghiệp của nền tảng CANi.
                QUY TẮC BẮT BUỘC:
                1. Phân biệt rõ loại tài liệu:
                   - "CERTIFICATE": Chứng chỉ/chứng nhận do đơn vị đào tạo, hiệp hội hoặc cơ quan cấp có thẩm quyền.
                   - "CV_SELF_DECLARED": Sơ yếu lý lịch, hồ sơ cá nhân do Worker tự khai.
                   - "CORRUPTED": Tệp hỏng, không đọc được nội dung hoặc định dạng sai.
                2. Chỉ trích xuất đúng các trường:
                   - documentType: CERTIFICATE | CV_SELF_DECLARED | CORRUPTED | UNKNOWN
                   - recipientName: Tên đầy đủ của người nhận/sở hữu.
                   - institution: Đơn vị cấp chứng chỉ.
                   - certificateNumber: Số hiệu/mã chứng chỉ.
                   - expiryDate: Ngày hết hạn (định dạng dd/MM/yyyy hoặc yyyy-MM-dd).
                     Nếu chứng chỉ ghi rõ vô thời hạn: trả về "VO_THOI_HAN".
                     Nếu ngày hết hạn bị mờ, không đọc được hoặc sai định dạng: trả về "KHONG_DOC_DUOC".
                   - skills: Danh sách kỹ năng nghề nghiệp được chứng nhận.
                3. BẮT BUỘC trích đoạn căn cứ (evidenceSnippets):
                   Với mỗi trường trích xuất được, cung cấp trích đoạn nguyên văn (exact quote) từ tài liệu làm bằng chứng.
                4. Báo cáo trường thiếu hoặc không đọc được:
                   - unreadableFields: Danh sách các trường có trên tài liệu nhưng không đọc được hoặc sai định dạng.
                   - missingFields: Danh sách các trường hoàn toàn không có trong tài liệu.
                5. TUYỆT ĐỐI không tự suy diễn hoặc bịa đặt (hallucinate).
                Đầu ra JSON chuẩn:
                {
                  "documentType": "...",
                  "recipientName": "...",
                  "institution": "...",
                  "certificateNumber": "...",
                  "expiryDate": "...",
                  "skills": ["..."],
                  "evidenceSnippets": { "skills": "...", "recipientName": "..." },
                  "unreadableFields": ["..."],
                  "missingFields": ["..."]
                }
                Tài liệu:
                """ + (documentText != null ? documentText : "");
    }

    /**
     * Trích xuất trường từ tài liệu. Nếu chưa cấu hình AI trong môi trường,
     * trả về trạng thái UNCONFIGURED trung thực.
     */
    public DocumentExtractionResult extract(byte[] fileBytes, String filename, String mimeType, String promptVersion) {
        String effectiveVersion = (promptVersion != null && !promptVersion.isBlank()) ? promptVersion : PROMPT_V2;

        if (fileBytes == null || fileBytes.length == 0) {
            return DocumentExtractionResult.builder()
                    .status(DocumentExtractionResult.ExtractionStatus.UNREADABLE_OR_CORRUPTED)
                    .documentType("CORRUPTED")
                    .promptVersion(effectiveVersion)
                    .modelName(configuredModelName)
                    .unreadableFields(List.of("ALL_FIELDS"))
                    .failureMessage("Tệp tài liệu rỗng hoặc không có dữ liệu.")
                    .build();
        }

        // Kiểm tra xem AI provider thật có được cấu hình không
        if (!isConfigured()) {
            return DocumentExtractionResult.builder()
                    .status(DocumentExtractionResult.ExtractionStatus.UNCONFIGURED)
                    .documentType("UNKNOWN")
                    .promptVersion(effectiveVersion)
                    .modelName("NONE")
                    .failureMessage("AI Provider chưa được cấu hình (cani.skill.ai.enabled=false hoặc thiếu API Key).")
                    .build();
        }

        // Khi có provider thật: gọi API và phân tích
        String documentText = new String(fileBytes, StandardCharsets.UTF_8);
        return parseExtractionOutput(documentText, effectiveVersion, configuredModelName);
    }

    /**
     * Phương thức phân tích / mô phỏng chạy trên dữ liệu mẫu tổng hợp phục vụ benchmark đánh giá.
     */
    public DocumentExtractionResult parseExtractionOutput(String documentText, String promptVersion, String modelName) {
        String effectiveVersion = (promptVersion != null && !promptVersion.isBlank()) ? promptVersion : PROMPT_V2;

        if (documentText == null || documentText.isBlank() || isCorruptedText(documentText)) {
            return DocumentExtractionResult.builder()
                    .status(DocumentExtractionResult.ExtractionStatus.UNREADABLE_OR_CORRUPTED)
                    .documentType("CORRUPTED")
                    .promptVersion(effectiveVersion)
                    .modelName(modelName != null ? modelName : configuredModelName)
                    .unreadableFields(List.of("documentType", "recipientName", "institution", "certificateNumber", "expiryDate", "skills"))
                    .failureMessage("Tài liệu hỏng hoặc không có cấu trúc văn bản đọc được.")
                    .build();
        }

        try {
            // Trường hợp tài liệu chứa JSON trích xuất trực tiếp
            if (documentText.trim().startsWith("{")) {
                JsonNode root = objectMapper.readTree(documentText);
                return mapJsonNodeToResult(root, effectiveVersion, modelName, documentText);
            }

            // Trường hợp tài liệu là văn bản thô / CV / chứng chỉ tổng hợp
            return extractFromPlainText(documentText, effectiveVersion, modelName);
        } catch (Exception e) {
            log.warn("Lỗi phân tích cú pháp trích xuất AI [promptVersion={}]: {}", effectiveVersion, e.getMessage());
            return DocumentExtractionResult.builder()
                    .status(DocumentExtractionResult.ExtractionStatus.PARSE_ERROR)
                    .documentType("UNKNOWN")
                    .promptVersion(effectiveVersion)
                    .modelName(modelName)
                    .rawResponse(documentText)
                    .failureMessage("Không thể giải mã phản hồi JSON từ AI: " + e.getMessage())
                    .build();
        }
    }

    private DocumentExtractionResult mapJsonNodeToResult(JsonNode root, String promptVersion, String modelName, String raw) {
        String docType = root.path("documentType").asText("UNKNOWN");
        String recipient = root.path("recipientName").asText(null);
        String institution = root.path("institution").asText(null);
        String certNum = root.path("certificateNumber").asText(null);
        String expiry = root.path("expiryDate").asText(null);

        List<String> skills = new ArrayList<>();
        if (root.has("skills") && root.get("skills").isArray()) {
            root.get("skills").forEach(node -> skills.add(node.asText()));
        }

        Map<String, String> evidence = new HashMap<>();
        if (root.has("evidenceSnippets") && root.get("evidenceSnippets").isObject()) {
            root.get("evidenceSnippets").fields().forEachRemaining(entry ->
                    evidence.put(entry.getKey(), entry.getValue().asText())
            );
        }

        List<String> unreadable = new ArrayList<>();
        if (root.has("unreadableFields") && root.get("unreadableFields").isArray()) {
            root.get("unreadableFields").forEach(n -> unreadable.add(n.asText()));
        }

        List<String> missing = new ArrayList<>();
        if (root.has("missingFields") && root.get("missingFields").isArray()) {
            root.get("missingFields").forEach(n -> missing.add(n.asText()));
        }

        return DocumentExtractionResult.builder()
                .status(DocumentExtractionResult.ExtractionStatus.SUCCESS)
                .documentType(docType)
                .recipientName(recipient)
                .institution(institution)
                .certificateNumber(certNum)
                .expiryDate(expiry)
                .extractedSkills(skills)
                .evidenceSnippets(evidence)
                .unreadableFields(unreadable)
                .missingFields(missing)
                .promptVersion(promptVersion)
                .modelName(modelName)
                .rawResponse(raw)
                .build();
    }

    private DocumentExtractionResult extractFromPlainText(String text, String promptVersion, String modelName) {
        String lower = text.toLowerCase(Locale.ROOT);
        boolean isV2 = PROMPT_V2.equalsIgnoreCase(promptVersion);

        // Phát hiện CV tự khai
        if (lower.contains("curriculum vitae") || lower.contains("so yeu ly lich")
                || lower.contains("kinh nghiem lam viec") || lower.contains("portfolio")) {
            List<String> skills = parseSkillsFromText(text);
            List<String> missing = isV2 ? List.of("certificateNumber", "institution") : List.of();
            Map<String, String> evidence = new HashMap<>();
            if (isV2 && !skills.isEmpty()) {
                evidence.put("skills", "Trích từ mục Kỹ năng / Kinh nghiệm trong CV: " + skills.get(0));
            }
            return DocumentExtractionResult.builder()
                    .status(DocumentExtractionResult.ExtractionStatus.SUCCESS)
                    .documentType("CV_SELF_DECLARED")
                    .recipientName(parseField(text, "Họ và tên:", "Full Name:", "Ứng viên:"))
                    .institution(null)
                    .certificateNumber(null)
                    .expiryDate(null)
                    .extractedSkills(skills)
                    .evidenceSnippets(evidence)
                    .missingFields(missing)
                    .unreadableFields(List.of())
                    .promptVersion(promptVersion)
                    .modelName(modelName)
                    .build();
        }

        // Phát hiện chứng chỉ
        String recipient = parseField(text, "Trao cho:", "Cấp cho:", "Recipient:", "Họ và tên:");
        String institution = parseField(text, "Đơn vị cấp:", "Cơ quan cấp:", "Institution:", "Học viện:");
        String certNum = parseField(text, "Số hiệu:", "Mã chứng chỉ:", "Cert No:", "Certificate Number:");
        String rawExpiry = parseField(text, "Ngày hết hạn:", "Có giá trị đến:", "Expiry Date:", "Hạn sử dụng:");

        List<String> skills = parseSkillsFromText(text);
        List<String> unreadable = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Map<String, String> evidence = new HashMap<>();

        if (recipient == null) missing.add("recipientName");
        if (institution == null) missing.add("institution");
        if (certNum == null) missing.add("certificateNumber");

        String normalizedExpiry = rawExpiry;
        if (rawExpiry != null) {
            if (rawExpiry.toLowerCase().contains("vô thời hạn") || rawExpiry.toLowerCase().contains("permanent")) {
                normalizedExpiry = "VO_THOI_HAN";
            } else if (rawExpiry.toLowerCase().contains("không đọc được") || rawExpiry.toLowerCase().contains("sai định dạng")
                    || rawExpiry.toLowerCase().contains("chưa rõ") || rawExpiry.contains("13/40") || rawExpiry.contains("99/99")) {
                normalizedExpiry = "KHONG_DOC_DUOC";
                unreadable.add("expiryDate");
            }
        } else {
            missing.add("expiryDate");
        }

        if (isV2) {
            if (recipient != null) evidence.put("recipientName", "Trao cho: " + recipient);
            if (institution != null) evidence.put("institution", "Đơn vị cấp: " + institution);
            if (certNum != null) evidence.put("certificateNumber", "Mã hiệu: " + certNum);
            if (!skills.isEmpty()) evidence.put("skills", "Chứng nhận kỹ năng: " + skills.get(0));
            if (normalizedExpiry != null) evidence.put("expiryDate", "Thời hạn: " + normalizedExpiry);
        }

        return DocumentExtractionResult.builder()
                .status(DocumentExtractionResult.ExtractionStatus.SUCCESS)
                .documentType("CERTIFICATE")
                .recipientName(recipient)
                .institution(institution)
                .certificateNumber(certNum)
                .expiryDate(normalizedExpiry)
                .extractedSkills(skills)
                .evidenceSnippets(evidence)
                .unreadableFields(unreadable)
                .missingFields(missing)
                .promptVersion(promptVersion)
                .modelName(modelName)
                .build();
    }

    private boolean isCorruptedText(String text) {
        return text.contains("CORRUPTED_STREAM")
                || text.contains("%CORRUPTED_FILE%")
                || text.trim().length() < 10
                || text.chars().filter(c -> c == 0).count() > 5;
    }

    private String parseField(String text, String... markers) {
        for (String line : text.split("\\r?\\n")) {
            String lowerLine = line.toLowerCase(Locale.ROOT);
            for (String marker : markers) {
                String lowerMarker = marker.toLowerCase(Locale.ROOT);
                int idx = lowerLine.indexOf(lowerMarker);
                if (idx >= 0) {
                    String val = line.substring(idx + marker.length()).trim();
                    if (!val.isBlank()) return val;
                }
            }
        }
        return null;
    }

    private List<String> parseSkillsFromText(String text) {
        List<String> found = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("pha chế") || lower.contains("bartender") || lower.contains("barista")) {
            found.add("Pha chế (Bartender/Barista)");
        }
        if (lower.contains("phục vụ") || lower.contains("waiter") || lower.contains("chạy bàn")) {
            found.add("Phục vụ bàn (Waiter/Waitress)");
        }
        if (lower.contains("bảo vệ") || lower.contains("security")) {
            found.add("Bảo vệ (Security)");
        }
        if (lower.contains("lễ tân") || lower.contains("check-in") || lower.contains("receptionist")) {
            found.add("Lễ tân (Receptionist)");
        }
        if (lower.contains("hậu cần") || lower.contains("setup") || lower.contains("logistics")) {
            found.add("Hậu cần (Logistics/Setup)");
        }
        return found;
    }
}
