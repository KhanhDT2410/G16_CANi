package com.cani.controller;

import com.cani.dto.block.BlockUserRequestDto;
import com.cani.dto.block.BlockedUserResponseDto;
import com.cani.dto.response.ApiResponse;
import com.cani.security.SecurityUtils;
import com.cani.service.BlockListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/blocks")
@RequiredArgsConstructor
@Tag(name = "Block List", description = "Quản lý danh sách chặn người dùng (UC-38, UC-39)")
public class BlockListController {

    private final BlockListService blockListService;

    /**
     * UC-39: Xem danh sách chặn của người dùng
     */
    @Operation(summary = "Xem danh sách chặn", description = "Lấy danh sách người dùng đã bị chặn bởi tài khoản đang đăng nhập.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BlockedUserResponseDto>>> getBlockedList(Authentication authentication) {
        UUID currentUserId = SecurityUtils.getCurrentUserId(authentication);
        List<BlockedUserResponseDto> list = blockListService.getBlockedList(currentUserId);
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách chặn thành công"));
    }

    /**
     * UC-38: Chặn một người dùng (Event Organizer hoặc Worker)
     */
    @Operation(summary = "Chặn người dùng", description = "Thêm một người dùng vào danh sách chặn cá nhân kèm lý do tùy chọn.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ApiResponse<BlockedUserResponseDto>> blockUser(
            @Valid @RequestBody BlockUserRequestDto request,
            Authentication authentication
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId(authentication);
        BlockedUserResponseDto result = blockListService.blockUser(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(result, "Đã thêm người dùng vào danh sách chặn thành công"));
    }

    /**
     * UC-39: Bỏ chặn một người dùng
     */
    @Operation(summary = "Bỏ chặn người dùng", description = "Xóa một người dùng khỏi danh sách chặn để khôi phục tương tác và dispatch ca làm.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{blockedUserId}")
    public ResponseEntity<ApiResponse<Void>> unblockUser(
            @PathVariable UUID blockedUserId,
            Authentication authentication
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId(authentication);
        blockListService.unblockUser(currentUserId, blockedUserId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Bỏ chặn người dùng thành công"));
    }
}
