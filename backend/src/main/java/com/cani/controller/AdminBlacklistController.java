package com.cani.controller;

import com.cani.dto.admin.blacklist.BlacklistResponseDto;
import com.cani.dto.admin.blacklist.CreateBlacklistRequestDto;
import com.cani.dto.response.ApiResponse;
import com.cani.dto.response.PageResponse;
import com.cani.entity.User;
import com.cani.entity.enums.BlacklistIdentifierType;
import com.cani.service.AdminBlacklistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/blacklist")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBlacklistController {

    private final AdminBlacklistService adminBlacklistService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BlacklistResponseDto>>> getBlacklist(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "type", required = false) BlacklistIdentifierType type,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<BlacklistResponseDto> response = adminBlacklistService.getBlacklist(search, type, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy danh sách đen thành công"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BlacklistResponseDto>> addToBlacklist(
            @Valid @RequestBody CreateBlacklistRequestDto request,
            @RequestParam(value = "adminId", required = false) UUID explicitAdminId,
            Authentication authentication
    ) {
        UUID adminId = resolveAdminId(authentication, explicitAdminId);
        BlacklistResponseDto response = adminBlacklistService.addToBlacklist(adminId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đã thêm vào danh sách đen"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> removeFromBlacklist(
            @PathVariable("id") UUID id,
            @RequestParam(value = "adminId", required = false) UUID explicitAdminId,
            Authentication authentication
    ) {
        UUID adminId = resolveAdminId(authentication, explicitAdminId);
        adminBlacklistService.removeFromBlacklist(adminId, id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã xóa khỏi danh sách đen"));
    }

    private UUID resolveAdminId(Authentication authentication, UUID explicitAdminId) {
        if (explicitAdminId != null) {
            return explicitAdminId;
        }
        if (authentication != null && authentication.getPrincipal() instanceof User u) {
            return u.getUserId();
        }
        return null;
    }
}
