-- Widen email_otp_verifications.otp_code to store 64-char HMAC/SHA-256 hex digest instead of plaintext OTP
ALTER TABLE email_otp_verifications
    ALTER COLUMN otp_code TYPE VARCHAR(64);
