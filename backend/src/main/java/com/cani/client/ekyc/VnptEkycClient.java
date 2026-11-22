package com.cani.client.ekyc;

import com.cani.config.VnptEkycProperties;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.cani.dto.ekyc.EkycIdentityResultDto.ExpiryDateStatus;
import com.cani.dto.ekyc.EkycIdentityResultDto.FaceCompareStatus;
import com.cani.dto.ekyc.EkycIdentityResultDto.LivenessStatus;
import com.cani.entity.enums.EkycRoutingDecision;
import com.cani.exception.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/**
 * Client kết nối trực tiếp với cổng VNPT eKYC API thực tế.
 *
 * Luồng xác minh:
 * 1. Kiểm tra đầu vào ảnh mặt trước, mặt sau, ảnh selfie và cấu hình VNPT (không fallback Mock/AI).
 * 2. Upload ảnh mặt trước, mặt sau (nếu verifyBackCard=true) và selfie lên VNPT File Service.
 * 3. Gọi OCR mặt trước + OCR mặt sau (theo cấu hình hợp đồng API VNPT).
 * 4. Gọi Liveness/PAD nếu gói dịch vụ VNPT đã đăng ký và được cấu hình endpoint chính thức.
 * 5. Gọi Face Compare so khớp khuôn mặt trên CCCD và ảnh selfie.
 * 6. Chuẩn hóa kết quả trung thực (không bịa confidence, không tự suy đoán thang điểm 0-1/0-100,
 *    không coi OCR thành công là xác minh thành công, không coi ngày hết hạn không đọc được là hợp lệ).
 *
 * Bảo mật log: TUYỆT ĐỐI không ghi token, CCCD, họ tên, ảnh hay raw response chứa PII ra log.
 */
@Slf4j
@Component
public class VnptEkycClient implements EkycProvider {

    public static final String PROVIDER_NAME = "VNPT_EKYC";
    private static final String VNPT_SUCCESS_CODE = "IDG-00000000";

    private static final List<DateTimeFormatter> EXPIRY_DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    );

    private final VnptEkycProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient customRestClient;
    private final Clock clock;

    @Autowired
    public VnptEkycClient(VnptEkycProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, null, Clock.systemUTC());
    }

    VnptEkycClient(VnptEkycProperties properties, ObjectMapper objectMapper, RestClient customRestClient, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.customRestClient = customRestClient;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public EkycIdentityResultDto verifyIdentity(
            byte[] frontBytes, String frontMime,
            byte[] backBytes, String backMime,
            byte[] selfieBytes, String selfieMime) {

        validateImageBytes(frontBytes, "Ảnh mặt trước CCCD");
        validateImageBytes(backBytes, "Ảnh mặt sau CCCD");
        validateImageBytes(selfieBytes, "Ảnh chân dung Selfie");

        if (!properties.isEnabled()) {
            log.warn("VNPT eKYC đang tắt (cani.ekyc.vnpt.enabled=false). Không fallback sang Mock/AI.");
            return buildTechnicalFailure(
                    "VNPT_DISABLED",
                    "Dịch vụ xác thực VNPT eKYC hiện chưa được kích hoạt trên hệ thống. Vui lòng thử lại sau.",
                    null
            );
        }

        if (!properties.hasRequiredCredentials()) {
            log.error("VNPT eKYC thiếu thông tin cấu hình bắt buộc (baseUrl/tokenId/tokenKey/accessToken/macAddress).");
            return buildTechnicalFailure(
                    "VNPT_CONFIG_INCOMPLETE",
                    "Hệ thống xác thực VNPT eKYC chưa được cấu hình đầy đủ thông số kết nối. Vui lòng thử lại sau.",
                    null
            );
        }

        if ((properties.isCardLivenessEnabled() && isBlank(properties.getCardLivenessPath()))
                || (properties.isFaceLivenessEnabled() && isBlank(properties.getFaceLivenessPath()))) {
            log.error("VNPT eKYC Liveness đã bật nhưng thiếu đường dẫn endpoint chính thức.");
            return buildTechnicalFailure(
                    "VNPT_LIVENESS_CONFIG_INCOMPLETE",
                    "Cấu hình kiểm tra Liveness của VNPT eKYC chưa đầy đủ. Vui lòng thử lại sau.",
                    null
            );
        }

        String clientSession = generateClientSession();
        RestClient restClient = resolveRestClient();

        try {
            // 1. Upload ảnh mặt trước, mặt sau và ảnh selfie lên VNPT Storage
            String frontHash = uploadFile(restClient, frontBytes, "front_card.jpg", "Front CCCD", frontMime);
            String backHash = null;
            if (properties.isVerifyBackCard()) {
                backHash = uploadFile(restClient, backBytes, "back_card.jpg", "Back CCCD", backMime);
            }
            String selfieHash = uploadFile(restClient, selfieBytes, "selfie_face.jpg", "Selfie Face", selfieMime);

            // 2. OCR mặt trước CCCD
            JsonNode frontOcrNode = callVnptJsonEndpoint(
                    restClient,
                    properties.getOcrFrontPath(),
                    buildFrontOcrPayload(frontHash, clientSession),
                    "VNPT_OCR_FRONT"
            );

            // 3. OCR mặt sau CCCD (nếu hợp đồng yêu cầu — mặc định bật)
            JsonNode backOcrNode = null;
            if (properties.isVerifyBackCard()) {
                backOcrNode = callVnptJsonEndpoint(
                        restClient,
                        properties.getOcrBackPath(),
                        buildBackOcrPayload(backHash, clientSession),
                        "VNPT_OCR_BACK"
                );
            }

            // 4. Kiểm tra Liveness/PAD nếu gói API VNPT đã đăng ký hỗ trợ & bật cấu hình
            JsonNode cardLivenessNode = null;
            if (properties.isCardLivenessEnabled()) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("img", frontHash);
                payload.put("client_session", clientSession);
                payload.put("token", generateRandomToken());
                cardLivenessNode = callVnptJsonEndpoint(
                        restClient,
                        properties.getCardLivenessPath(),
                        payload,
                        "VNPT_CARD_LIVENESS"
                );
            }

            JsonNode faceLivenessNode = null;
            if (properties.isFaceLivenessEnabled()) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("img", selfieHash);
                payload.put("client_session", clientSession);
                payload.put("token", generateRandomToken());
                faceLivenessNode = callVnptJsonEndpoint(
                        restClient,
                        properties.getFaceLivenessPath(),
                        payload,
                        "VNPT_FACE_LIVENESS"
                );
            }

            // 5. Face Compare (bắt buộc — lỗi / timeout / sai định dạng sẽ trả TECHNICAL_FAILURE, tuyệt đối không AUTO_APPROVED)
            JsonNode faceCompareNode;
            try {
                faceCompareNode = callVnptJsonEndpoint(
                        restClient,
                        properties.getFaceComparePath(),
                        buildFaceComparePayload(frontHash, selfieHash, clientSession),
                        "VNPT_FACE_COMPARE"
                );
            } catch (VnptApiCallException faceEx) {
                log.warn("VNPT Face Compare thất bại [session={}, errorCode={}]", clientSession, faceEx.errorCode);
                FaceCompareStatus status = "VNPT_FACE_COMPARE_TIMEOUT".equals(faceEx.errorCode)
                        ? FaceCompareStatus.TIMEOUT
                        : FaceCompareStatus.ERROR;
                EkycIdentityResultDto failure = buildTechnicalFailure(
                        faceEx.errorCode,
                        faceEx.userMessage,
                        clientSession
                );
                failure.setFaceCompareStatus(status);
                return failure;
            }

            return parseVnptResults(frontOcrNode, backOcrNode, faceCompareNode, cardLivenessNode, faceLivenessNode, clientSession);

        } catch (VnptApiCallException apiEx) {
            log.warn("VNPT eKYC gọi API thất bại [session={}, errorCode={}]", clientSession, apiEx.errorCode);
            return buildTechnicalFailure(apiEx.errorCode, apiEx.userMessage, clientSession);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi không mong đợi khi gọi VNPT eKYC [session={}, type={}]", clientSession, e.getClass().getSimpleName());
            return buildTechnicalFailure(
                    "VNPT_UNEXPECTED_ERROR",
                    "Dịch vụ VNPT eKYC gặp sự cố kỹ thuật tạm thời. Vui lòng thử lại sau.",
                    clientSession
            );
        }
    }

    /**
     * Tương thích cho các unit test kiểm tra parse kết quả trực tiếp (mặt trước + mặt sau + Face Compare).
     */
    public EkycIdentityResultDto parseVnptResults(JsonNode frontOcrNode, JsonNode backOcrNode, JsonNode faceNode) {
        return parseVnptResults(frontOcrNode, backOcrNode, faceNode, null, null, null);
    }

    /**
     * Chuẩn hóa kết quả từ các phản hồi JSON của VNPT eKYC.
     * Không tự bịa dữ liệu khi trường vắng mặt, không tự suy đoán thang điểm Face Match.
     */
    public EkycIdentityResultDto parseVnptResults(
            JsonNode frontOcrNode,
            JsonNode backOcrNode,
            JsonNode faceNode,
            JsonNode cardLivenessNode,
            JsonNode faceLivenessNode,
            String clientSession) {

        // 1. Xác thực phản hồi OCR mặt trước
        if (!isValidVnptResponse(frontOcrNode)) {
            return buildTechnicalFailure(
                    "VNPT_OCR_FAILED",
                    "VNPT eKYC không trả về dữ liệu OCR hợp lệ cho mặt trước CCCD. Vui lòng thử lại sau.",
                    clientSession
            );
        }
        JsonNode frontObj = frontOcrNode.path("object");

        // 2. Xác thực phản hồi OCR mặt sau (nếu yêu cầu xác minh mặt sau)
        JsonNode backObj = null;
        boolean backVerified = false;
        if (properties.isVerifyBackCard()) {
            if (!isValidVnptResponse(backOcrNode)) {
                return buildTechnicalFailure(
                        "VNPT_BACK_OCR_FAILED",
                        "VNPT eKYC không trả về dữ liệu OCR hợp lệ cho mặt sau CCCD. Vui lòng thử lại sau.",
                        clientSession
                );
            }
            backObj = backOcrNode.path("object");
            backVerified = true;
        } else if (isValidVnptResponse(backOcrNode)) {
            backObj = backOcrNode.path("object");
            backVerified = true;
        }

        // 3. Xác thực phản hồi Face Compare (BẮT BUỘC — không có hoặc sai định dạng => TECHNICAL_FAILURE)
        if (faceNode == null || faceNode.isNull()) {
            EkycIdentityResultDto failure = buildTechnicalFailure(
                    "VNPT_FACE_COMPARE_MISSING",
                    "VNPT eKYC không trả về kết quả so khớp khuôn mặt. Không thể hoàn tất xác minh, vui lòng thử lại.",
                    clientSession
            );
            failure.setFaceCompareStatus(FaceCompareStatus.MISSING);
            return failure;
        }
        if (!isValidVnptResponse(faceNode)) {
            EkycIdentityResultDto failure = buildTechnicalFailure(
                    "VNPT_FACE_COMPARE_INVALID_FORMAT",
                    "Kết quả so khớp khuôn mặt từ VNPT eKYC không hợp lệ. Vui lòng thử lại sau.",
                    clientSession
            );
            failure.setFaceCompareStatus(FaceCompareStatus.INVALID_FORMAT);
            return failure;
        }

        JsonNode faceObj = faceNode.path("object");
        ParsedFaceCompare parsedFace = parseFaceCompare(faceObj);
        if (parsedFace.status == FaceCompareStatus.INVALID_FORMAT || parsedFace.status == FaceCompareStatus.MISSING) {
            EkycIdentityResultDto failure = buildTechnicalFailure(
                    "VNPT_FACE_COMPARE_INVALID_FORMAT",
                    "Phản hồi so khớp khuôn mặt từ VNPT eKYC sai định dạng dữ liệu. Vui lòng thử lại sau.",
                    clientSession
            );
            failure.setFaceCompareStatus(parsedFace.status);
            return failure;
        }

        // 4. Bóc tách thông tin OCR (không bịa giá trị nếu thiếu)
        String idNumber = textOrNull(frontObj, "id");
        String fullName = textOrNull(frontObj, "name");
        String dob = textOrNull(frontObj, "birth_day");
        String gender = cleanOptionalText(textOrNull(frontObj, "gender"));
        String nationality = cleanOptionalText(textOrNull(frontObj, "nationality"));
        String originLocation = cleanOptionalText(textOrNull(frontObj, "origin_location"));
        String recentLocation = cleanOptionalText(textOrNull(frontObj, "recent_location"));
        String validDate = cleanOptionalText(textOrNull(frontObj, "valid_date"));
        if (validDate == null && backObj != null) {
            validDate = cleanOptionalText(textOrNull(backObj, "valid_date"));
        }

        String issueDate = cleanOptionalText(textOrNull(frontObj, "issue_date"));
        if (issueDate == null && backObj != null) {
            issueDate = cleanOptionalText(textOrNull(backObj, "issue_date"));
        }
        String features = backObj != null ? cleanOptionalText(textOrNull(backObj, "features")) : null;
        if (features == null) {
            features = cleanOptionalText(textOrNull(frontObj, "features"));
        }

        // Độ tin cậy OCR thực tế (chỉ lấy nếu VNPT thực sự trả về trường số)
        Double ocrConfidence = extractRealOcrConfidence(frontObj);

        // 5. Phân tích cờ chống gian lận trên cả mặt trước và mặt sau
        FraudCheckSummary frontFraud = inspectFraudFlags(frontObj);
        FraudCheckSummary backFraud = backObj != null ? inspectFraudFlags(backObj) : FraudCheckSummary.clean();

        boolean isLegal = frontFraud.isLegal && backFraud.isLegal;
        boolean isFake = frontFraud.isFake || backFraud.isFake;
        boolean isCutCorner = frontFraud.isCutCorner || backFraud.isCutCorner;
        boolean isScreenCaptured = frontFraud.isScreenCaptured || backFraud.isScreenCaptured || isFake;
        boolean isPhotoshoped = !isLegal || frontFraud.isPhotoshoped || backFraud.isPhotoshoped;
        boolean isBlurred = frontFraud.isBlurred || backFraud.isBlurred;
        boolean backTampered = !backFraud.isLegal || backFraud.isFake || backFraud.isCutCorner || backFraud.isPhotoshoped;

        // 6. Phân tích Liveness / PAD (nếu có trong response hoặc endpoint liveness riêng)
        LivenessStatus livenessStatus = evaluateLiveness(frontObj, faceObj, cardLivenessNode, faceLivenessNode);
        if (livenessStatus == LivenessStatus.ERROR) {
            EkycIdentityResultDto failure = buildTechnicalFailure(
                    "VNPT_LIVENESS_INVALID_RESPONSE",
                    "Kết quả kiểm tra Liveness từ VNPT eKYC không hợp lệ. Vui lòng thử lại sau.",
                    clientSession
            );
            failure.setLivenessStatus(LivenessStatus.ERROR);
            return failure;
        }

        // 7. Phân tích ngày hết hạn CCCD (KHÔNG mặc định hợp lệ khi không đọc được!)
        ExpiryDateStatus expiryStatus = evaluateExpiryDate(validDate);
        boolean isExpired = expiryStatus == ExpiryDateStatus.EXPIRED;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("provider", PROVIDER_NAME);
        metadata.put("faceCompareStatus", parsedFace.status.name());
        metadata.put("faceMessage", parsedFace.rawMessage);
        metadata.put("expiryStatus", expiryStatus.name());
        metadata.put("livenessStatus", livenessStatus.name());
        metadata.put("backCardVerified", backVerified);
        metadata.put("isLegal", isLegal);
        metadata.put("isFakeWarning", isFake);
        metadata.put("isBlurred", isBlurred);
        metadata.put("isExpired", isExpired);

        return EkycIdentityResultDto.builder()
                .providerName(PROVIDER_NAME)
                .providerReference(clientSession)
                .idNumber(idNumber)
                .fullName(fullName)
                .dob(dob)
                .gender(gender)
                .nationality(nationality)
                .originAddress(originLocation)
                .residenceAddress(recentLocation)
                .expiryDate(validDate)
                .issueDate(issueDate)
                .identificationFeatures(features)
                .ocrConfidence(ocrConfidence)
                .faceMatchScore(parsedFace.score)
                .faceCompareMessage(parsedFace.rawMessage)
                .faceCompareStatus(parsedFace.status)
                .expiryStatus(expiryStatus)
                .livenessStatus(livenessStatus)
                .documentLegal(isLegal)
                .fakeDocument(isFake)
                .isCutCorner(isCutCorner)
                .isScreenCaptured(isScreenCaptured)
                .isPhotoshoped(isPhotoshoped)
                .isExpired(isExpired)
                .hasBlurryWarning(isBlurred)
                .backCardVerified(backVerified)
                .backCardTampered(backTampered)
                .technicalFailure(false)
                .rawAnalysis(metadata)
                .build();
    }

    /**
     * Đánh giá ngày hết hạn CCCD theo 3 trạng thái rõ ràng: VALID, EXPIRED, UNREADABLE.
     * Không bao giờ mặc định hợp lệ khi không đọc được ngày hết hạn.
     */
    ExpiryDateStatus evaluateExpiryDate(String validDate) {
        if (isBlank(validDate) || "-".equals(validDate.trim()) || "N/A".equalsIgnoreCase(validDate.trim())) {
            return ExpiryDateStatus.UNREADABLE;
        }
        String normalized = validDate.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("KHÔNG THỜI HẠN")
                || normalized.contains("KHONG THOI HAN")
                || normalized.contains("UNLIMITED")) {
            return ExpiryDateStatus.VALID;
        }
        for (DateTimeFormatter fmt : EXPIRY_DATE_FORMATTERS) {
            try {
                LocalDate expiry = LocalDate.parse(validDate.trim(), fmt);
                return expiry.isBefore(LocalDate.now(clock))
                        ? ExpiryDateStatus.EXPIRED
                        : ExpiryDateStatus.VALID;
            } catch (Exception ignored) {
                // Thử định dạng tiếp theo
            }
        }
        return ExpiryDateStatus.UNREADABLE;
    }

    private ParsedFaceCompare parseFaceCompare(JsonNode faceObj) {
        if (faceObj == null || !faceObj.isObject()) {
            return new ParsedFaceCompare(FaceCompareStatus.INVALID_FORMAT, null, null);
        }

        String rawMsg = textOrNull(faceObj, "msg");
        JsonNode probNode = faceObj.get("prob");

        if (isBlank(rawMsg) || probNode == null || !probNode.isNumber()) {
            return new ParsedFaceCompare(FaceCompareStatus.INVALID_FORMAT, null, rawMsg);
        }

        double score = probNode.doubleValue();
        if (!Double.isFinite(score) || score < 0.0) {
            return new ParsedFaceCompare(FaceCompareStatus.INVALID_FORMAT, null, rawMsg);
        }

        // Chỉ kiểm tra giới hạn thang điểm nếu đã được cấu hình tường minh theo tài liệu VNPT
        if (properties.getFaceMatchScoreMax() != null && score > properties.getFaceMatchScoreMax()) {
            return new ParsedFaceCompare(FaceCompareStatus.INVALID_FORMAT, null, rawMsg);
        }

        String upperMsg = rawMsg.trim().toUpperCase(Locale.ROOT);
        if ("NOMATCH".equals(upperMsg)) {
            return new ParsedFaceCompare(FaceCompareStatus.NOMATCH, score, upperMsg);
        }
        if ("MATCH".equals(upperMsg)) {
            // Chỉ áp dụng ngưỡng số nếu ngưỡng đã được xác nhận & cấu hình trong properties
            if (properties.getFaceMatchMinThreshold() != null && score < properties.getFaceMatchMinThreshold()) {
                return new ParsedFaceCompare(FaceCompareStatus.BELOW_THRESHOLD, score, upperMsg);
            }
            return new ParsedFaceCompare(FaceCompareStatus.MATCH, score, upperMsg);
        }

        return new ParsedFaceCompare(FaceCompareStatus.INVALID_FORMAT, null, rawMsg);
    }

    private LivenessStatus evaluateLiveness(
            JsonNode frontObj,
            JsonNode faceObj,
            JsonNode cardLivenessNode,
            JsonNode faceLivenessNode) {

        List<LivenessStatus> observed = new ArrayList<>();

        if (cardLivenessNode != null) {
            if (!isValidVnptResponse(cardLivenessNode)) {
                return LivenessStatus.ERROR;
            }
            observed.add(parseLivenessObject(cardLivenessNode.path("object")));
        }
        if (faceLivenessNode != null) {
            if (!isValidVnptResponse(faceLivenessNode)) {
                return LivenessStatus.ERROR;
            }
            observed.add(parseLivenessObject(faceLivenessNode.path("object")));
        }

        // Kiểm tra thêm nếu trường liveness được trả trực tiếp trong OCR/Face response
        if (frontObj != null && frontObj.has("card_liveness")) {
            observed.add(parseLivenessValue(frontObj.path("card_liveness")));
        }
        if (faceObj != null && faceObj.has("liveness")) {
            observed.add(parseLivenessValue(faceObj.path("liveness")));
        }

        if (observed.isEmpty()) {
            return LivenessStatus.NOT_EVALUATED;
        }
        if (observed.contains(LivenessStatus.ERROR)) {
            return LivenessStatus.ERROR;
        }
        if (observed.contains(LivenessStatus.FAILED)) {
            return LivenessStatus.FAILED;
        }
        if (observed.stream().allMatch(s -> s == LivenessStatus.PASSED)) {
            return LivenessStatus.PASSED;
        }
        return LivenessStatus.NOT_EVALUATED;
    }

    private LivenessStatus parseLivenessObject(JsonNode obj) {
        if (obj == null || !obj.isObject()) {
            return LivenessStatus.ERROR;
        }
        if (obj.has("liveness")) {
            return parseLivenessValue(obj.path("liveness"));
        }
        if (obj.has("is_real")) {
            return parseLivenessValue(obj.path("is_real"));
        }
        return LivenessStatus.ERROR;
    }

    private LivenessStatus parseLivenessValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return LivenessStatus.NOT_EVALUATED;
        }
        if (node.isBoolean()) {
            return node.booleanValue() ? LivenessStatus.PASSED : LivenessStatus.FAILED;
        }
        String val = node.asText("").trim().toLowerCase(Locale.ROOT);
        if ("success".equals(val) || "real".equals(val) || "live".equals(val) || "yes".equals(val) || "true".equals(val)) {
            return LivenessStatus.PASSED;
        }
        if ("fail".equals(val) || "failed".equals(val) || "fake".equals(val) || "spoof".equals(val) || "no".equals(val) || "false".equals(val)) {
            return LivenessStatus.FAILED;
        }
        return LivenessStatus.ERROR;
    }

    private FraudCheckSummary inspectFraudFlags(JsonNode obj) {
        if (obj == null || !obj.isObject()) {
            return FraudCheckSummary.clean();
        }
        boolean isLegal = true;
        if (obj.has("tampering") && obj.path("tampering").has("is_legal")) {
            String isLegalStr = obj.path("tampering").path("is_legal").asText("yes");
            isLegal = "yes".equalsIgnoreCase(isLegalStr);
        }

        boolean isFake = false;
        if (obj.has("id_fake_warning")) {
            isFake = "yes".equalsIgnoreCase(obj.path("id_fake_warning").asText("no"));
        }

        boolean isCutCorner = false;
        boolean isScreenCaptured = false;
        boolean isPhotoshoped = false;
        boolean isBlurred = false;

        List<JsonNode> warningArrays = new ArrayList<>();
        if (obj.has("warning") && obj.get("warning").isArray()) {
            warningArrays.add(obj.get("warning"));
        }
        if (obj.has("tampering") && obj.path("tampering").has("warning") && obj.path("tampering").get("warning").isArray()) {
            warningArrays.add(obj.path("tampering").get("warning"));
        }
        for (JsonNode arr : warningArrays) {
            for (JsonNode w : arr) {
                String text = w.asText("").toLowerCase(Locale.ROOT);
                if (text.contains("mat_goc") || text.contains("cut") || text.contains("corner")) {
                    isCutCorner = true;
                }
                if (text.contains("chup_man_hinh") || text.contains("screen") || text.contains("photocopy")) {
                    isScreenCaptured = true;
                }
                if (text.contains("chinh_sua") || text.contains("photoshop") || text.contains("tamper") || text.contains("fake")) {
                    isPhotoshoped = true;
                }
                if (text.contains("mo_nhoe") || text.contains("blur") || text.contains("loa_sang") || text.contains("glare")) {
                    isBlurred = true;
                }
            }
        }

        return new FraudCheckSummary(isLegal, isFake, isCutCorner, isScreenCaptured, isPhotoshoped, isBlurred);
    }

    private Double extractRealOcrConfidence(JsonNode frontObj) {
        if (frontObj == null || !frontObj.isObject()) {
            return null;
        }
        if (frontObj.has("overall_score") && frontObj.get("overall_score").isNumber()) {
            double val = frontObj.get("overall_score").doubleValue();
            return Double.isFinite(val) ? val : null;
        }
        List<Double> probs = new ArrayList<>();
        for (String key : List.of("id_prob", "name_prob", "birth_day_prob")) {
            if (frontObj.has(key) && frontObj.get(key).isNumber()) {
                double val = frontObj.get(key).doubleValue();
                if (Double.isFinite(val)) {
                    probs.add(val);
                }
            }
        }
        if (probs.isEmpty()) {
            return null;
        }
        return probs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private String uploadFile(RestClient restClient, byte[] fileBytes, String fileName, String title, String mime) {
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

        try {
            String rawResponse = restClient.post()
                    .uri(properties.getUploadFilePath())
                    .header(HttpHeaders.AUTHORIZATION, getAuthHeader())
                    .header("Token-id", properties.getTokenId().trim())
                    .header("Token-key", properties.getTokenKey().trim())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = parseJsonStrict(rawResponse, "VNPT_UPLOAD_INVALID_RESPONSE");
            if (isValidVnptResponse(root) && root.path("object").has("hash")) {
                String hash = root.path("object").path("hash").asText(null);
                if (!isBlank(hash)) {
                    return hash;
                }
            }
            throw new VnptApiCallException(
                    "VNPT_UPLOAD_FAILED",
                    "Không thể tải ảnh xác minh lên hệ thống VNPT eKYC. Vui lòng thử lại sau."
            );
        } catch (VnptApiCallException e) {
            throw e;
        } catch (Exception e) {
            throw translateHttpException(e, "VNPT_UPLOAD");
        }
    }

    private JsonNode callVnptJsonEndpoint(
            RestClient restClient,
            String path,
            Map<String, Object> payload,
            String stepPrefix) {
        try {
            String rawResponse = restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, getAuthHeader())
                    .header("Token-id", properties.getTokenId().trim())
                    .header("Token-key", properties.getTokenKey().trim())
                    .header("mac-address", properties.getMacAddress().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            return parseJsonStrict(rawResponse, stepPrefix + "_INVALID_FORMAT");
        } catch (VnptApiCallException e) {
            throw e;
        } catch (Exception e) {
            throw translateHttpException(e, stepPrefix);
        }
    }

    private JsonNode parseJsonStrict(String rawResponse, String errorCode) {
        if (isBlank(rawResponse)) {
            throw new VnptApiCallException(errorCode, "VNPT eKYC trả về phản hồi rỗng. Vui lòng thử lại sau.");
        }
        try {
            return objectMapper.readTree(rawResponse);
        } catch (JsonProcessingException e) {
            throw new VnptApiCallException(errorCode, "VNPT eKYC trả về dữ liệu sai định dạng JSON. Vui lòng thử lại sau.");
        }
    }

    private VnptApiCallException translateHttpException(Exception e, String stepPrefix) {
        if (e instanceof HttpClientErrorException clientEx) {
            int code = clientEx.getStatusCode().value();
            if (code == 401 || code == 403) {
                return new VnptApiCallException(
                        "VNPT_AUTH_INVALID",
                        "Xác thực kết nối tới VNPT eKYC thất bại (token không hợp lệ hoặc đã hết hạn). Vui lòng thử lại sau."
                );
            }
            return new VnptApiCallException(
                    stepPrefix + "_HTTP_" + code,
                    "Yêu cầu tới VNPT eKYC không thể xử lý (mã lỗi " + code + "). Vui lòng thử lại sau."
            );
        }
        if (e instanceof HttpServerErrorException serverEx) {
            return new VnptApiCallException(
                    "VNPT_SERVER_ERROR_" + serverEx.getStatusCode().value(),
                    "Cổng dịch vụ VNPT eKYC đang tạm thời gián đoạn. Vui lòng thử lại sau ít phút."
            );
        }
        if (e instanceof ResourceAccessException || isTimeoutException(e)) {
            if (isTimeoutException(e)) {
                return new VnptApiCallException(
                        stepPrefix + "_TIMEOUT",
                        "Kết nối tới VNPT eKYC quá thời gian chờ. Vui lòng thử lại sau."
                );
            }
            return new VnptApiCallException(
                    "VNPT_CONNECTION_ERROR",
                    "Không thể kết nối tới cổng VNPT eKYC. Vui lòng kiểm tra kết nối và thử lại."
            );
        }
        return new VnptApiCallException(
                stepPrefix + "_ERROR",
                "Dịch vụ VNPT eKYC gặp sự cố kỹ thuật tạm thời. Vui lòng thử lại sau."
        );
    }

    private boolean isTimeoutException(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor instanceof SocketTimeoutException) {
                return true;
            }
            String msg = cursor.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("timed out")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private boolean isValidVnptResponse(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        if (node.has("message")) {
            String msg = node.path("message").asText("");
            if (!isBlank(msg) && !VNPT_SUCCESS_CODE.equalsIgnoreCase(msg.trim())) {
                return false;
            }
        }
        JsonNode obj = node.get("object");
        return obj != null && obj.isObject() && !obj.isEmpty();
    }

    private Map<String, Object> buildFrontOcrPayload(String frontHash, String clientSession) {
        Map<String, Object> body = new HashMap<>();
        body.put("img_front", frontHash);
        body.put("client_session", clientSession);
        body.put("type", -1);
        body.put("validate_postcode", true);
        body.put("token", generateRandomToken());
        return body;
    }

    private Map<String, Object> buildBackOcrPayload(String backHash, String clientSession) {
        Map<String, Object> body = new HashMap<>();
        body.put("img_back", backHash);
        body.put("client_session", clientSession);
        body.put("type", -1);
        body.put("token", generateRandomToken());
        return body;
    }

    private Map<String, Object> buildFaceComparePayload(String frontHash, String selfieHash, String clientSession) {
        Map<String, Object> body = new HashMap<>();
        body.put("img_front", frontHash);
        body.put("img_face", selfieHash);
        body.put("client_session", clientSession);
        body.put("token", generateRandomToken());
        return body;
    }

    private RestClient resolveRestClient() {
        if (customRestClient != null) {
            return customRestClient;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl().trim())
                .requestFactory(factory)
                .build();
    }

    private void validateImageBytes(byte[] bytes, String fieldName) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException(fieldName + " không được để trống.");
        }
    }

    private EkycIdentityResultDto buildTechnicalFailure(String errorCode, String reason, String clientSession) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("provider", PROVIDER_NAME);
        meta.put("errorCode", errorCode);
        if (clientSession != null) {
            meta.put("clientSession", clientSession);
        }
        return EkycIdentityResultDto.builder()
                .providerName(PROVIDER_NAME)
                .providerReference(clientSession)
                .technicalFailure(true)
                .technicalErrorCode(errorCode)
                .routingDecision(EkycRoutingDecision.TECHNICAL_FAILURE.name())
                .routingReason(reason)
                .rawAnalysis(meta)
                .build();
    }

    private String getAuthHeader() {
        String token = properties.getAccessToken() != null ? properties.getAccessToken().trim() : "";
        if (token.regionMatches(true, 0, "bearer ", 0, 7)) {
            return token;
        }
        return "Bearer " + token;
    }

    private String generateClientSession() {
        return "CANI_EKYC_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "_" + clock.millis();
    }

    private String generateRandomToken() {
        return "cani" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String textOrNull(JsonNode obj, String field) {
        if (obj == null || !obj.has(field) || obj.get(field).isNull()) {
            return null;
        }
        String text = obj.get(field).asText(null);
        return (text != null && !text.isBlank()) ? text.trim() : null;
    }

    private static String cleanOptionalText(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ParsedFaceCompare(FaceCompareStatus status, Double score, String rawMessage) {}

    private record FraudCheckSummary(
            boolean isLegal,
            boolean isFake,
            boolean isCutCorner,
            boolean isScreenCaptured,
            boolean isPhotoshoped,
            boolean isBlurred
    ) {
        static FraudCheckSummary clean() {
            return new FraudCheckSummary(true, false, false, false, false, false);
        }
    }

    private static final class VnptApiCallException extends RuntimeException {
        final String errorCode;
        final String userMessage;

        VnptApiCallException(String errorCode, String userMessage) {
            super(errorCode);
            this.errorCode = errorCode;
            this.userMessage = userMessage;
        }
    }
}
