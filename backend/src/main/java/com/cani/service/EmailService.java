package com.cani.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    /**
     * Gửi email chứa mã xác thực OTP 6 số đến người dùng.
     */
    public void sendOtpEmail(String toEmail, String otpCode) {

        if (mailSender == null || fromEmail == null || fromEmail.isBlank()) {
            log.warn("OTP delivery unavailable: SMTP is not configured.");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "CANi Event Staffing Platform");
            helper.setTo(toEmail);
            helper.setSubject("[CANi] Mã xác thực tài khoản");

            String htmlBody = buildOtpEmailHtml(otpCode);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("OTP email delivered.");
        } catch (Exception e) {
            log.error("OTP email delivery failed [{}]", e.getClass().getSimpleName());
        }
    }

    /**
     * Gửi email thông báo nghiệp vụ thanh toán thù lao (EO khai báo đã trả / Worker phản hồi đã nhận).
     * Ảnh chứng từ dạng Data URI (Base64 từ Web EO) được đính kèm inline + file đính kèm,
     * vì đa số trình đọc mail (Gmail, Outlook) chặn hiển thị trực tiếp Data URI trong thẻ img.
     *
     * @param details   các dòng thông tin hiển thị theo thứ tự (nhãn -> giá trị), đã là văn bản thuần
     * @param proofImage chứng từ: Data URI, URL http(s), hoặc null nếu không có
     */
    public void sendPaymentEmail(String toEmail, String subject, String heading, String intro,
                                 java.util.Map<String, String> details, String proofImage, String footerNote) {
        if (mailSender == null || fromEmail == null || fromEmail.isBlank()) {
            log.warn("Payment email skipped: SMTP is not configured.");
            return;
        }
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Payment email skipped: recipient has no email address.");
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "CANi Event Staffing Platform");
            helper.setTo(toEmail);
            helper.setSubject(subject);

            DecodedImage decoded = decodeProofImage(proofImage);
            String proofHtml = buildProofHtml(proofImage, decoded);
            helper.setText(buildPaymentEmailHtml(heading, intro, details, proofHtml, footerNote), true);

            if (decoded != null) {
                org.springframework.core.io.ByteArrayResource resource =
                        new org.springframework.core.io.ByteArrayResource(decoded.bytes());
                helper.addInline(PROOF_CONTENT_ID, resource, decoded.mimeType());
                helper.addAttachment("chung-tu-thanh-toan." + decoded.extension(), resource, decoded.mimeType());
            }

            mailSender.send(message);
            log.info("Payment email delivered [{}].", subject);
        } catch (Exception e) {
            log.error("Payment email delivery failed [{}]", e.getClass().getSimpleName());
        }
    }

    private static final String PROOF_CONTENT_ID = "paymentProof";
    private static final String EVENT_IMAGE_PREFIX = "/api/v1/eo/events/images/";
    private static final java.nio.file.Path EVENT_UPLOAD_ROOT = java.nio.file.Paths.get("uploads/events").toAbsolutePath().normalize();

    private record DecodedImage(byte[] bytes, String mimeType, String extension) {}

    private DecodedImage decodeProofImage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("data:image/")) {
            return decodeDataUri(trimmed);
        }
        String pathOnly = extractLocalEventImageFilename(trimmed);
        if (pathOnly != null) {
            return loadLocalEventImage(pathOnly);
        }
        return null;
    }

    private String extractLocalEventImageFilename(String value) {
        String path = value;
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                path = java.net.URI.create(value).getPath();
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        if (path == null || !path.startsWith(EVENT_IMAGE_PREFIX)) {
            return null;
        }
        String filename = path.substring(EVENT_IMAGE_PREFIX.length());
        if (filename.isBlank() || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return null;
        }
        return filename;
    }

    private DecodedImage loadLocalEventImage(String filename) {
        try {
            java.nio.file.Path resolved = EVENT_UPLOAD_ROOT.resolve(filename).normalize();
            if (!resolved.startsWith(EVENT_UPLOAD_ROOT) || !java.nio.file.Files.isReadable(resolved)) {
                return null;
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(resolved);
            String ext = "jpg";
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < filename.length()) {
                ext = filename.substring(dot + 1).toLowerCase();
            }
            String mimeType = switch (ext) {
                case "png" -> "image/png";
                case "webp" -> "image/webp";
                case "gif" -> "image/gif";
                default -> "image/jpeg";
            };
            return new DecodedImage(bytes, mimeType, ext);
        } catch (Exception ex) {
            log.warn("Could not read local payment proof image [{}]: {}", filename, ex.getMessage());
            return null;
        }
    }

    private DecodedImage decodeDataUri(String value) {
        int comma = value.indexOf(',');
        int semicolon = value.indexOf(';');
        if (comma < 0 || semicolon < 0 || semicolon > comma) {
            return null;
        }
        try {
            String mimeType = value.substring("data:".length(), semicolon);
            String extension = mimeType.substring("image/".length()).replace("jpeg", "jpg");
            byte[] bytes = java.util.Base64.getMimeDecoder().decode(value.substring(comma + 1));
            return new DecodedImage(bytes, mimeType, extension);
        } catch (IllegalArgumentException ex) {
            log.warn("Payment proof is not a valid Base64 image, skipping attachment.");
            return null;
        }
    }

    private String buildProofHtml(String proofImage, DecodedImage decoded) {
        if (decoded != null) {
            return "<p style=\"margin:16px 0 6px;font-weight:700;color:#0f172a;\">Chứng từ thanh toán</p>"
                    + "<img src=\"cid:" + PROOF_CONTENT_ID + "\" alt=\"Chứng từ thanh toán\" "
                    + "style=\"max-width:100%;max-height:420px;border:1px solid #e5e7eb;border-radius:8px;\"/>";
        }
        if (proofImage != null && (proofImage.startsWith("http://") || proofImage.startsWith("https://"))) {
            return "<p style=\"margin:16px 0 6px;\"><a href=\"" + escapeHtml(proofImage)
                    + "\" style=\"color:#4f46e5;font-weight:700;\">Xem ảnh chứng từ thanh toán</a></p>";
        }
        return "<p style=\"margin:16px 0 0;color:#92400e;font-size:13px;\">Không có ảnh chứng từ đính kèm.</p>";
    }

    private String buildPaymentEmailHtml(String heading, String intro, java.util.Map<String, String> details,
                                         String proofHtml, String footerNote) {
        StringBuilder rows = new StringBuilder();
        if (details != null) {
            details.forEach((label, value) -> rows
                    .append("<tr><td style=\"padding:6px 10px;color:#64748b;white-space:nowrap;\">")
                    .append(escapeHtml(label))
                    .append("</td><td style=\"padding:6px 10px;color:#0f172a;font-weight:600;\">")
                    .append(escapeHtml(value != null ? value : "—"))
                    .append("</td></tr>"));
        }
        return "<div style=\"font-family:'Segoe UI',Tahoma,sans-serif;max-width:560px;margin:0 auto;padding:24px;"
                + "border:1px solid #e5e7eb;border-radius:12px;background:#ffffff;\">"
                + "<h1 style=\"color:#4f46e5;margin:0 0 4px;font-size:24px;\">CANi</h1>"
                + "<h2 style=\"color:#0f172a;font-size:18px;margin:12px 0;\">" + escapeHtml(heading) + "</h2>"
                + "<p style=\"color:#334155;line-height:1.6;\">" + escapeHtml(intro) + "</p>"
                + "<table style=\"width:100%;border-collapse:collapse;background:#f8fafc;border-radius:8px;font-size:14px;\">"
                + rows + "</table>"
                + proofHtml
                + "<p style=\"color:#64748b;font-size:12px;margin-top:20px;border-top:1px solid #f1f5f9;padding-top:12px;\">"
                + escapeHtml(footerNote) + "</p>"
                + "</div>";
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String buildOtpEmailHtml(String otpCode) {
        return """
            <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 540px; margin: 0 auto; padding: 24px; border: 1px solid #e5e7eb; border-radius: 12px; background-color: #ffffff;">
                <div style="text-align: center; margin-bottom: 24px;">
                    <h1 style="color: #4f46e5; margin: 0; font-size: 26px; font-weight: 800;">CANi</h1>
                    <p style="color: #6b7280; font-size: 14px; margin-top: 4px;">Nền tảng Tuyển dụng & Quản lý Nhân sự Sự kiện</p>
                </div>
                <div style="color: #1f2937; line-height: 1.6;">
                    <p>Xin chào,</p>
                    <p>Bạn đang thực hiện thao tác đăng ký tài khoản trên hệ thống CANi. Dưới đây là mã xác thực OTP của bạn:</p>
                    
                    <div style="background: #f0fdf4; border: 2px dashed #10b981; border-radius: 10px; padding: 18px; text-align: center; margin: 24px 0;">
                        <span style="font-size: 32px; font-weight: 800; letter-spacing: 6px; color: #047857;">{{OTP_CODE}}</span>
                        <div style="color: #065f46; font-size: 13px; margin-top: 6px;">Hiệu lực trong vòng 5 phút</div>
                    </div>

                    <p style="color: #4b5563; font-size: 13px;">
                        ⚠️ <strong>Lưu ý:</strong> Tuyệt đối không chia sẻ mã này với bất kỳ ai để đảm bảo an toàn cho tài khoản của bạn.
                    </p>
                    <p style="color: #9ca3af; font-size: 12px; margin-top: 24px; border-top: 1px solid #f3f4f6; padding-top: 12px;">
                        Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email.
                    </p>
                </div>
            </div>
            """.replace("{{OTP_CODE}}", otpCode);
    }
}
