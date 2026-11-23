package com.cani.controller;

import com.cani.dto.appeal.AccountAppealResponseDto;
import com.cani.dto.appeal.SubmitAccountAppealRequestDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.User;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.UserRepository;
import com.cani.service.AccountAppealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Controller công khai phục vụ người dùng (đặc biệt là Event Organizer)
 * nộp đơn giải trình và theo dõi tiến độ khi tài khoản bị khóa (Locked Account Appeal).
 */
@RestController
@RequestMapping("/api/v1/public/account-appeals")
@RequiredArgsConstructor
public class AccountAppealController {

    private final AccountAppealService accountAppealService;
    private final UserRepository userRepository;

    /**
     * Nộp đơn giải trình kèm tài liệu khắc phục (Public)
     */
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<AccountAppealResponseDto>> submitAppeal(
            @Valid @RequestBody SubmitAccountAppealRequestDto request
    ) {
        AccountAppealResponseDto response = accountAppealService.submitAppeal(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Nộp đơn giải trình mở khóa tài khoản thành công! Ban Quản trị sẽ xem xét trong thời gian sớm nhất."));
    }

    /**
     * Tra cứu tiến độ đơn giải trình mới nhất theo Email (Public)
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<AccountAppealResponseDto>> getAppealStatus(
            @RequestParam("email") String email
    ) {
        AccountAppealResponseDto response = accountAppealService.getLatestAppealByEmail(email);
        return ResponseEntity.ok(ApiResponse.ok(response, "Tra cứu tiến độ đơn giải trình thành công"));
    }

    /**
     * Lấy thông tin lý do khóa tài khoản theo Email (Public)
     */
    @GetMapping("/lock-info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLockInfo(
            @RequestParam("email") String email
    ) {
        String cleanEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(cleanEmail).orElse(null);

        if (user == null) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("found", false), "Không tìm thấy thông tin tài khoản"));
        }

        boolean isLocked = user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.SUSPENDED;
        String lockReason = isLocked ? accountAppealService.findLatestLockReason(user.getUserId()) : null;

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "found", true,
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "role", user.getRole() != null ? user.getRole().name() : "EVENT_ORGANIZER",
                "status", user.getStatus().name(),
                "isLocked", isLocked,
                "lockReason", lockReason != null ? lockReason : ""
        ), "Lấy thông tin trạng thái tài khoản thành công"));
    }

    /**
     * Upload tài liệu / hình ảnh minh chứng phục vụ giải trình (Public)
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadEvidence(
            @RequestParam("file") MultipartFile file
    ) {
        String url = accountAppealService.uploadEvidence(file);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url), "Tải tài liệu minh chứng lên thành công!"));
    }
}
