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
        log.info("📧 [CANi OTP Service] Mã xác thực OTP cho [{}] là: [{}] (Hiệu lực: 5 phút)", toEmail, otpCode);

        if (mailSender == null || fromEmail == null || fromEmail.isBlank()) {
            log.info("ℹ️ Cấu hình SMTP (SPRING_MAIL_USERNAME) chưa được thiết lập. Mã OTP được ghi log phục vụ môi trường Dev/Test.");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "CANi Event Staffing Platform");
            helper.setTo(toEmail);
            helper.setSubject("[CANi] Mã xác thực đăng ký tài khoản: " + otpCode);

            String htmlBody = buildOtpEmailHtml(otpCode);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("✅ Đã gửi email xác thực OTP thực tế thành công tới: {}", toEmail);
        } catch (Exception e) {
            log.error("⚠️ Không thể gửi email qua SMTP tới {}: {}. OTP ghi nhận trong log để tiếp tục kiểm thử.", toEmail, e.getMessage());
        }
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
