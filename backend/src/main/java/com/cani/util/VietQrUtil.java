package com.cani.util;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Tiện ích tạo URL hình ảnh VietQR chuẩn Napas 247 (https://vietqr.io).
 * Cho phép người trả tiền (Event Organizer) dùng mọi ứng dụng ngân hàng quét mã và chuyển tiền ngay lập tức.
 * Cú pháp chuẩn: https://img.vietqr.io/image/{BANK_CODE}-{ACCOUNT_NO}-compact2.png?amount={AMOUNT}&accountName={ACCOUNT_NAME}
 */
public final class VietQrUtil {

    private VietQrUtil() {}

    /**
     * Sinh URL VietQR Quick Link
     *
     * @param bankCode Mã ngân hàng (ví dụ: MB, VCB, TCB, VPB, ACB...)
     * @param accountNumber Số tài khoản ngân hàng
     * @param accountName Tên chủ tài khoản
     * @param amount Số tiền cần chuyển (nếu có)
     * @return Chuỗi URL ảnh VietQR hoặc null nếu thiếu thông tin bắt buộc
     */
    public static String buildVietQrUrl(String bankCode, String accountNumber, String accountName, BigDecimal amount) {
        if (bankCode == null || bankCode.isBlank() || accountNumber == null || accountNumber.isBlank()) {
            return null;
        }

        String cleanBank = bankCode.trim().toUpperCase();
        String cleanAcc = accountNumber.trim().replaceAll("\\s+", "");

        StringBuilder sb = new StringBuilder("https://img.vietqr.io/image/");
        sb.append(cleanBank).append("-").append(cleanAcc).append("-compact2.png");

        boolean hasParam = false;
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("?amount=").append(amount.longValue());
            hasParam = true;
        }

        if (accountName != null && !accountName.isBlank()) {
            sb.append(hasParam ? "&accountName=" : "?accountName=")
                    .append(URLEncoder.encode(accountName.trim().toUpperCase(), StandardCharsets.UTF_8));
        }

        return sb.toString();
    }
}
