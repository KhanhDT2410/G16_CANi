package com.cani.client.gemini;

import com.cani.dto.skill.GeminiVerificationResultDto;
import com.cani.entity.enums.RoutingDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GeminiVisionClientLiveTest {

    @Test
    @DisplayName("Live Integration Test: Call Gemini AI Studio API with user API key and verify structured JSON output")
    void testLiveGeminiVisionVerification() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            Map<String, Object> envProps = com.cani.config.DotenvLoader.load();
            if (envProps != null && envProps.containsKey("GEMINI_API_KEY")) {
                apiKey = (String) envProps.get("GEMINI_API_KEY");
            }
        }
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("GEMINI_API_KEY is not set in environment or .env. Skipping live test.");
            return;
        }

        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey(apiKey);
        properties.setModel("gemini-3.5-flash-lite");
        properties.setUrl("https://generativelanguage.googleapis.com/v1beta/models");
        properties.setTimeoutMs(25000);

        ObjectMapper objectMapper = new ObjectMapper();
        GeminiVisionClient client = new GeminiVisionClient(properties, objectMapper);

        // Sample certificate document text
        String sampleCertText = """
                BỘ GIÁO DỤC VÀ ĐÀO TẠO
                HỌC VIỆN PHA CHẾ Á ÂU (HNAAU)
                ---------------------------
                CHỨNG CHỈ NGHỀ
                Cấp cho học viên: NGUYỄN VĂN AN
                Sinh ngày: 15/05/2001
                Số CCCD: 079201008888
                Đã hoàn thành xuất sắc khóa đào tạo:
                CHUYÊN VIÊN PHA CHẾ BARTENDER CHUYÊN NGHIỆP
                Thời lượng: 60 giờ thực hành và lý thuyết nghiệp vụ F&B.
                Xếp loại: Giỏi
                Ngày cấp: 15/08/2024
                Hiệu trưởng: Trần Văn Bình (Đã ký và đóng dấu tròn đỏ)
                """;

        byte[] certBytes = sampleCertText.getBytes(StandardCharsets.UTF_8);
        List<String> taxonomy = List.of(
                "Phục vụ bàn",
                "Phụ bếp",
                "Pha chế (Bartender/Barista)",
                "Thu ngân",
                "Điều phối sự kiện"
        );

        GeminiVerificationResultDto result = client.verifyDocument(certBytes, "text/plain", taxonomy);

        System.out.println("=== LIVE GEMINI AI VERIFICATION RESULT ===");
        System.out.println("Recipient Name: " + result.getRecipientName());
        System.out.println("Institution: " + result.getInstitution());
        System.out.println("Has Seal/Signature: " + result.getHasSealOrSignature());
        System.out.println("Overall Confidence: " + result.getOverallConfidence());
        System.out.println("Routing Decision: " + result.getRoutingDecision());
        System.out.println("Routing Reason: " + result.getRoutingReason());
        if (result.getExtractedSkills() != null && !result.getExtractedSkills().isEmpty()) {
            System.out.println("Extracted Skill: " + result.getExtractedSkills().get(0).getSkillName()
                    + " -> Taxonomy Role: " + result.getExtractedSkills().get(0).getTaxonomyRole()
                    + " (Confidence: " + result.getExtractedSkills().get(0).getConfidenceScore() + ")");
        }
        System.out.println("==========================================");

        assertNotNull(result);
        assertNotNull(result.getRoutingDecision());
        assertTrue(result.getOverallConfidence() > 0.0);
        // Live response should AUTO_APPROVE or ADMIN_REVIEW
        assertNotEquals(RoutingDecision.REJECT, result.getRoutingDecision());
    }
}
