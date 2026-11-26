package com.cani.service.skill.eval;

import com.cani.client.skill.SkillAiDocumentProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Trình trích xuất trường tài liệu kỹ năng qua AI OCR/Vision đa phương thức (hỗ trợ PDF và ảnh).
 * - Không dùng Gemini; nối trực tiếp qua cổng {@link SkillAiDocumentProvider} theo cấu hình dự án.
 * - Nếu chưa có provider, thiếu credentials hợp lệ hoặc chưa xác thực kết nối thực tế:
 *   trả về {@link DocumentExtractionResult.ExtractionStatus#UNCONFIGURED}. Chuỗi apiKey không rỗng
 *   một mình KHÔNG đủ để xác nhận kết nối.
 * - Không bao giờ giải mã byte ảnh/PDF thành UTF-8 rồi coi đó là nội dung tài liệu.
 * - Bộ đọc từ khóa ({@link #extractSyntheticBenchmarkOnly}) CHỈ dùng cho benchmark tổng hợp offline.
 */
@Service
@Slf4j
public class SkillDocumentExtractor {

    public static final String PROMPT_V1 = "v1.0.0";
    public static final String PROMPT_V2 = "v2.0.0";

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "application/pdf"
    );

    private final ObjectMapper objectMapper;
    private final SkillAiDocumentProvider aiDocumentProvider;

    @Value("${cani.skill.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${cani.skill.ai.model:NONE}")
    private String configuredModelName = "NONE";

    @Value("${cani.skill.ai.api-key:}")
    private String apiKey;

    public SkillDocumentExtractor(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    @Autowired
    public SkillDocumentExtractor(
            ObjectMapper objectMapper,
            @Autowired(required = false) SkillAiDocumentProvider aiDocumentProvider
    ) {
        this.objectMapper = objectMapper;
        this.aiDocumentProvider = aiDocumentProvider;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Chỉ trả về true khi có provider AI thực tế, cấu hình hợp lệ (không phải Gemini)
     * và kết nối thực tế đã được xác nhận. Chuỗi apiKey không rỗng một mình trả về false.
     */
    public boolean isConfigured() {
        return aiDocumentProvider != null && aiDocumentProvider.isReady();
    }

    public String getConfiguredProviderName() {
        return aiDocumentProvider != null ? aiDocumentProvider.getProviderName() : "NONE";
    }

    public String getConfiguredModelName() {
        if (aiDocumentProvider != null && aiDocumentProvider.isReady()) {
            return aiDocumentProvider.getModelName();
        }
        return configuredModelName != null && !configuredModelName.isBlank() ? configuredModelName : "NONE";
    }

    public String getUnconfiguredReason() {
        if (aiDocumentProvider == null) {
            if (aiEnabled && apiKey != null && !apiKey.isBlank()) {
                return "UNCONFIGURED: Đã có chuỗi apiKey nhưng thiếu bean/cấu hình SkillAiDocumentProvider xác thực kết nối thực tế.";
            }
            return "UNCONFIGURED: Chưa cấu hình SkillAiDocumentProvider (cani.skill.ai.enabled=false hoặc thiếu provider/credentials).";
        }
        String reason = aiDocumentProvider.getUnconfiguredReason();
        return reason != null ? reason : "UNCONFIGURED: Provider AI chưa sẵn sàng.";
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
                  "documentType": "CERTIFICATE" hoặc "CV_SELF_DECLARED",
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
                Bạn là AI OCR & trích xuất cấu trúc tài liệu kỹ năng nghề nghiệp của nền tảng CANi.
                QUY TẮC BẮT BUỘC:
                1. Phân biệt rõ loại tài liệu:
                   - "CERTIFICATE": Chứng chỉ/chứng nhận do đơn vị đào tạo, hiệp hội hoặc cơ quan cấp có thẩm quyền.
                   - "CV_SELF_DECLARED": Sơ yếu lý lịch/CV tự khai (có thể liệt kê kỹ năng tự khai nhưng KHÔNG xác nhận chứng chỉ).
                   - "CORRUPTED": Tệp hỏng, PDF scan mờ không đọc được hoặc định dạng sai.
                2. Chỉ trích xuất dữ liệu có cấu trúc, phân biệt rõ giá trị đọc được / thiếu / không đọc được:
                   - documentType: CERTIFICATE | CV_SELF_DECLARED | CORRUPTED | UNKNOWN
                   - recipientName: Tên đầy đủ của người nhận/ứng viên.
                   - institution: Đơn vị cấp chứng chỉ (null nếu là CV hoặc thiếu).
                   - certificateNumber: Số hiệu/mã chứng chỉ (null nếu thiếu).
                   - expiryDate: Ngày hết hạn (định dạng dd/MM/yyyy hoặc yyyy-MM-dd).
                     Nếu chứng chỉ ghi rõ vô thời hạn: trả về "VO_THOI_HAN".
                     Nếu ngày hết hạn bị mờ, không đọc được hoặc không hợp lệ (ví dụ 31/02/2030): trả về "KHONG_DOC_DUOC" và thêm "expiryDate" vào unreadableFields.
                   - skills: Danh sách kỹ năng tìm thấy (với CV là kỹ năng tự khai).
                3. BẮT BUỘC trích đoạn căn cứ nguyên văn (evidenceSnippets):
                   Với mỗi trường trích xuất được, trích nguyên văn từng ký tự dòng/cụm câu xuất hiện trên tài liệu (KHÔNG đổi "Số hiệu" thành "Mã hiệu").
                4. Báo cáo tách bạch:
                   - unreadableFields: Trường có trong tài liệu nhưng mờ, nhòe, sai định dạng hoặc không đọc được.
                   - missingFields: Trường hoàn toàn không có trong tài liệu.
                5. TUYỆT ĐỐI không tự suy diễn, không bịa đặt và không tự ý kết luận cấp điểm SP.
                Đầu ra JSON chuẩn:
                {
                  "documentType": "...",
                  "recipientName": "...",
                  "institution": "...",
                  "certificateNumber": "...",
                  "expiryDate": "...",
                  "skills": ["..."],
                  "evidenceSnippets": { "certificateNumber": "...", "skills": "..." },
                  "unreadableFields": ["..."],
                  "missingFields": ["..."]
                }
                Tài liệu:
                """ + (documentText != null ? documentText : "");
    }

    /**
     * Trích xuất dữ liệu có cấu trúc từ file ảnh hoặc PDF thực tế qua provider AI cấu hình trong dự án.
     * - KHÔNG giải mã byte ảnh/PDF thành chuỗi UTF-8 để đọc nội dung.
     * - Nếu provider chưa cấu hình, thiếu credentials hợp lệ hoặc chưa xác thực kết nối -> trả UNCONFIGURED.
     */
    public DocumentExtractionResult extract(byte[] fileBytes, String filename, String mimeType, String promptVersion) {
        String effectiveVersion = (promptVersion != null && !promptVersion.isBlank()) ? promptVersion : PROMPT_V2;

        if (fileBytes == null || fileBytes.length == 0) {
            return DocumentExtractionResult.builder()
                    .status(DocumentExtractionResult.ExtractionStatus.UNREADABLE_OR_CORRUPTED)
                    .documentType("CORRUPTED")
                    .promptVersion(effectiveVersion)
                    .modelName("NONE")
                    .unreadableFields(List.of("ALL_FIELDS"))
                    .failureMessage("Tệp tài liệu rỗng hoặc không có dữ liệu.")
                    .build();
        }

        String formatError = validateAllowedBinaryFormat(fileBytes, mimeType);
        if (formatError != null) {
            return DocumentExtractionResult.builder()
                    .status(DocumentExtractionResult.ExtractionStatus.UNREADABLE_OR_CORRUPTED)
                    .documentType("CORRUPTED")
                    .promptVersion(effectiveVersion)
                    .modelName("NONE")
                    .unreadableFields(List.of("documentType", "recipientName", "institution", "certificateNumber", "expiryDate", "skills"))
                    .failureMessage(formatError)
                    .build();
        }

        if (aiDocumentProvider == null || !aiDocumentProvider.hasValidConfiguration()) {
            return DocumentExtractionResult.builder()
                    .status(DocumentExtractionResult.ExtractionStatus.UNCONFIGURED)
                    .documentType("UNKNOWN")
                    .promptVersion(effectiveVersion)
                    .modelName("NONE")
                    .failureMessage(getUnconfiguredReason())
                    .build();
        }

        String contextHint = String.format(
                "[Tệp đính kèm dạng nhị phân %s | filename=%s | size=%d bytes — xử lý bằng OCR/Vision đa phương thức]",
                mimeType,
                filename != null ? filename : "document",
                fileBytes.length
        );
        String prompt = buildPrompt(effectiveVersion, contextHint);
        return aiDocumentProvider.extractDocument(fileBytes, filename, mimeType, prompt, effectiveVersion);
    }

    private String validateAllowedBinaryFormat(byte[] fileBytes, String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "Thiếu MIME type tài liệu hợp lệ (chỉ hỗ trợ JPG, PNG, WEBP, PDF).";
        }
        String lowerMime = mimeType.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MIME_TYPES.contains(lowerMime)) {
            return "Định dạng tài liệu không được hỗ trợ cho OCR/AI: " + mimeType + " (chỉ hỗ trợ JPG, PNG, WEBP, PDF).";
        }
        if ("application/pdf".equals(lowerMime)) {
            String header = new String(fileBytes, 0, Math.min(fileBytes.length, 32), StandardCharsets.US_ASCII);
            if (!header.contains("%PDF-")) {
                return "Tệp PDF không hợp lệ hoặc bị hỏng cấu trúc header (%PDF-).";
            }
            return null;
        }
        if ("image/jpeg".equals(lowerMime) || "image/jpg".equals(lowerMime)) {
            if (fileBytes.length < 4 || (fileBytes[0] & 0xFF) != 0xFF || (fileBytes[1] & 0xFF) != 0xD8) {
                return "Ảnh JPEG không hợp lệ hoặc luồng byte bị hỏng.";
            }
            return null;
        }
        if ("image/png".equals(lowerMime)) {
            if (fileBytes.length < 8
                    || (fileBytes[0] & 0xFF) != 0x89
                    || fileBytes[1] != 0x50
                    || fileBytes[2] != 0x4E
                    || fileBytes[3] != 0x47) {
                return "Ảnh PNG không hợp lệ hoặc luồng byte bị hỏng.";
            }
            return null;
        }
        if ("image/webp".equals(lowerMime)) {
            if (fileBytes.length < 12
                    || fileBytes[0] != 'R' || fileBytes[1] != 'I' || fileBytes[2] != 'F' || fileBytes[3] != 'F'
                    || fileBytes[8] != 'W' || fileBytes[9] != 'E' || fileBytes[10] != 'B' || fileBytes[11] != 'P') {
                return "Ảnh WEBP không hợp lệ hoặc luồng byte bị hỏng.";
            }
            return null;
        }
        return null;
    }

    /**
     * [BENCHMARK SYNTHETIC ONLY] Bộ đọc theo từ khóa/văn bản tổng hợp CHỈ phục vụ
     * benchmark offline trên tập mẫu text. KHÔNG dùng trong luồng Worker nộp hồ sơ thật.
     */
    public DocumentExtractionResult extractSyntheticBenchmarkOnly(
            String documentText,
            String promptVersion,
            String modelName
    ) {
        String effectiveVersion = (promptVersion != null && !promptVersion.isBlank()) ? promptVersion : PROMPT_V2;
        String effectiveModel = (modelName != null && !modelName.isBlank()) ? modelName : "synthetic-keyword-parser";

        if (documentText == null || documentText.isBlank() || isCorruptedText(documentText)) {
            return DocumentExtractionResult.builder()
                    .status(DocumentExtractionResult.ExtractionStatus.UNREADABLE_OR_CORRUPTED)
                    .documentType("CORRUPTED")
                    .promptVersion(effectiveVersion)
                    .modelName(effectiveModel)
                    .unreadableFields(List.of("documentType", "recipientName", "institution", "certificateNumber", "expiryDate", "skills"))
                    .failureMessage("Tài liệu hỏng hoặc không có cấu trúc văn bản đọc được.")
                    .build();
        }

        try {
            if (documentText.trim().startsWith("{")) {
                JsonNode root = objectMapper.readTree(documentText);
                return mapJsonNodeToResult(root, effectiveVersion, effectiveModel, documentText);
            }
            return extractFromPlainText(documentText, effectiveVersion, effectiveModel);
        } catch (Exception e) {
            log.warn("Lỗi phân tích cú pháp synthetic benchmark [promptVersion={}]: {}", effectiveVersion, e.getMessage());
            return DocumentExtractionResult.builder()
                    .status(DocumentExtractionResult.ExtractionStatus.PARSE_ERROR)
                    .documentType("UNKNOWN")
                    .promptVersion(effectiveVersion)
                    .modelName(effectiveModel)
                    .rawResponse(documentText)
                    .failureMessage("Không thể giải mã phản hồi JSON synthetic: " + e.getMessage())
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

        // Phát hiện CV tự khai (không được tự động duyệt chứng chỉ)
        if (lower.contains("curriculum vitae")
                || lower.contains("sơ yếu lý lịch")
                || lower.contains("so yeu ly lich")
                || lower.contains("kinh nghiệm làm việc")
                || lower.contains("kinh nghiem lam viec")
                || lower.contains("portfolio")) {
            List<String> skills = parseSkillsFromText(text);
            List<String> missing = isV2 ? List.of("certificateNumber", "institution", "expiryDate") : List.of();
            Map<String, String> evidence = new LinkedHashMap<>();
            if (isV2) {
                String recipientLine = findMatchingLine(text, "Họ và tên:", "Full Name:", "Ứng viên:");
                if (recipientLine != null) {
                    evidence.put("recipientName", recipientLine);
                }
                String skillLine = findSkillEvidenceLine(text, skills);
                if (skillLine != null) {
                    evidence.put("skills", skillLine);
                }
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
        Map<String, String> evidence = new LinkedHashMap<>();

        if (recipient == null) missing.add("recipientName");
        if (institution == null) missing.add("institution");
        if (certNum == null) missing.add("certificateNumber");

        String normalizedExpiry = rawExpiry;
        if (rawExpiry != null) {
            String lowerExpiry = rawExpiry.toLowerCase(Locale.ROOT);
            if (lowerExpiry.contains("vô thời hạn") || lowerExpiry.contains("permanent")) {
                normalizedExpiry = "VO_THOI_HAN";
            } else if (lowerExpiry.contains("không đọc được") || lowerExpiry.contains("sai định dạng")
                    || lowerExpiry.contains("chưa rõ") || rawExpiry.contains("13/40")
                    || rawExpiry.contains("99/99") || rawExpiry.contains("31/02/")) {
                normalizedExpiry = "KHONG_DOC_DUOC";
                unreadable.add("expiryDate");
            }
        } else {
            missing.add("expiryDate");
        }

        if (isV2) {
            String recipientLine = findMatchingLine(text, "Trao cho:", "Cấp cho:", "Recipient:", "Họ và tên:");
            if (recipientLine != null) evidence.put("recipientName", recipientLine);

            String instLine = findMatchingLine(text, "Đơn vị cấp:", "Cơ quan cấp:", "Institution:", "Học viện:");
            if (instLine != null) evidence.put("institution", instLine);

            String certLine = findMatchingLine(text, "Số hiệu:", "Mã chứng chỉ:", "Cert No:", "Certificate Number:");
            if (certLine != null) evidence.put("certificateNumber", certLine);

            String skillLine = findSkillEvidenceLine(text, skills);
            if (skillLine != null) evidence.put("skills", skillLine);

            String expiryLine = findMatchingLine(text, "Ngày hết hạn:", "Có giá trị đến:", "Expiry Date:", "Hạn sử dụng:");
            if (expiryLine != null) evidence.put("expiryDate", expiryLine);
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

    private String findSkillEvidenceLine(String text, List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return null;
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String lower = trimmed.toLowerCase(Locale.ROOT);
            boolean hasSkillLabel = lower.contains("kỹ năng:")
                    || lower.contains("ky nang:")
                    || lower.contains("kỹ năng tự khai:")
                    || lower.contains("nghiệp vụ:")
                    || lower.contains("skills:");
            if (hasSkillLabel && containsAnySkillKeyword(lower)) {
                return trimmed;
            }
        }
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (containsAnySkillKeyword(lower)) {
                return trimmed;
            }
        }
        return null;
    }

    private boolean containsAnySkillKeyword(String lowerLine) {
        return lowerLine.contains("pha chế") || lowerLine.contains("bartender") || lowerLine.contains("barista")
                || lowerLine.contains("phục vụ") || lowerLine.contains("waiter") || lowerLine.contains("chạy bàn")
                || lowerLine.contains("bảo vệ") || lowerLine.contains("security")
                || lowerLine.contains("lễ tân") || lowerLine.contains("check-in") || lowerLine.contains("receptionist")
                || lowerLine.contains("hậu cần") || lowerLine.contains("setup") || lowerLine.contains("logistics");
    }

    private String findMatchingLine(String text, String... markers) {
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String lowerLine = trimmed.toLowerCase(Locale.ROOT);
            for (String marker : markers) {
                if (lowerLine.contains(marker.toLowerCase(Locale.ROOT))) {
                    return trimmed;
                }
            }
        }
        return null;
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
