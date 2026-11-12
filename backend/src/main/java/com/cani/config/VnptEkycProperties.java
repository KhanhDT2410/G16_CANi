package com.cani.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình tham số kết nối VNPT eKYC (UC-18 & UC-89).
 */
@Configuration
@ConfigurationProperties(prefix = "cani.ekyc.vnpt")
@Data
public class VnptEkycProperties {

    /**
     * Bật/tắt gọi trực tiếp VNPT eKYC API thật (Mặc định: true khi có Access Token).
     */
    private boolean enabled = true;

    /**
     * Domain gốc của VNPT eKYC API
     */
    private String baseUrl = "https://api.idg.vnpt.vn";

    /**
     * Token ID do VNPT cấp (cấu hình qua biến môi trường VNPT_EKYC_TOKEN_ID)
     */
    private String tokenId = "5e7a3b1b-db74-1656-e063-63199f0a717f";

    /**
     * Token Key (Public Key) do VNPT cấp (cấu hình qua biến môi trường VNPT_EKYC_TOKEN_KEY)
     */
    private String tokenKey = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAlvv9EToVRDb3ZS58HaSGBNDHN9GLm9lROWE4CxyRMHib2K1XCmSS6suua/4coDMvthnv8zKmUy25BiplQtd6MMCAwEAAQ==";

    /**
     * JWT Access Token (Lưu ý: Hết hạn sau 8 giờ)
     */
    private String accessToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0cmFuc2FjdGlvbl9pZCI6IjhjOGJiM2I4LTE4ZGUtNDRkMS1iZjdlLWExMThiOTNkNzdhMSIsInN1YiI6IjYyNzc5Y2Y4LWNlOGUtMTFmMS1hYTMzLTA3OTQ3MmMwODFkMyIsImF1ZCI6WyJyZXN0c2VydmljZSJdLCJ1c2VyX25hbWUiOiJodW5naG9jZnB0QGdtYWlsLmNvbSIsInNjb3BlIjpbInJlYWQiXSwiaXNzIjoiaHR0cHM6Ly9sb2NhbGhvc3QiLCJuYW1lIjoiaHVuZ2hvY2ZwdEBnbWFpbC5jb20iLCJleHAiOjE3OTQ1ODYzMzMsInV1aWRfYWNjb3VudCI6IjYyNzc5Y2Y4LWNlOGUtMTFmMS1hYTMzLTA3OTQ3MmMwODFkMyIsImF1dGhvcml0aWVzIjpbIlVTRVIiXSwianRpIjoiZmZhZTgwYzYtMzQ5OS00NGJlLWE0NWItODNiNGM0ZDU1OThlIiwiY2xpZW50X2lkIjoiY2xpZW50YXBwIn0.zwhXILWHszWfaZrlgYTyGG9PtEp4KRFoCoO2PtVRxxXOHeXjSLrMWRfNJEQKiRYdEeNdRD27MIKC6kGrKIfwaF_qnW6v4bcDdzy6U7M5ShXGmmDXeVN-AMhZob3z--h7iBbLPlZdo-u-CuxQrQyseWOyN194N2RxTCKfTmMooxh5WaDKu0OmRngt1hGFsMmEUsXOmfxSDgj66uw52f2jm3JusR6pbIju9EeUi7lzz4TTknSt_5Hw_OwOzyzNj1v1r_RJFuQQ2VmuK6v4MG7gXbDb55U1CtNa_bSNJ0XyKH5OyAbWRG-7L5rvxcXD-k35UeRMr_WvZuTNCGB6myhVIA";

    /**
     * MAC Address định danh thiết bị gửi lên VNPT (mặc định: TEST1)
     */
    private String macAddress = "TEST1";

    /**
     * Thời gian timeout kết nối (milliseconds)
     */
    private int timeoutMs = 25000;
}
