package com.cani.controller;

import com.cani.dto.response.ApiResponse;
import com.cani.dto.team.AddMyTeamMemberRequestDto;
import com.cani.dto.team.MyTeamMemberResponseDto;
import com.cani.dto.team.UpdateMyTeamMemberRequestDto;
import com.cani.security.SecurityUtils;
import com.cani.service.MyTeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/eo/my-team")
@RequiredArgsConstructor
@Tag(name = "EO My Team", description = "Quản lý Đội thợ quen của Event Organizer (UC-65, UC-66, UC-67)")
public class EoMyTeamController {

    private final MyTeamService myTeamService;

    /**
     * UC-65: Xem danh sách thợ quen trong My Team của Event Organizer
     */
    @Operation(summary = "Xem danh sách My Team", description = "Lấy danh sách Worker quen thuộc đã từng cộng tác, hỗ trợ tìm kiếm theo tên/SĐT và lọc danh sách yêu thích.")
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MyTeamMemberResponseDto>>> getMyTeam(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean favoritedOnly,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        List<MyTeamMemberResponseDto> members = myTeamService.getMyTeam(userId, search, favoritedOnly);
        return ResponseEntity.ok(ApiResponse.ok(members, "Lấy danh sách My Team thành công"));
    }

    /**
     * Xem chi tiết thông tin và lịch sử làm việc của một thành viên trong My Team
     */
    @Operation(summary = "Xem chi tiết thành viên My Team", description = "Xem chi tiết đánh giá, số ca đã làm chung và ghi chú của một Worker trong My Team.")
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @GetMapping("/{workerId}")
    public ResponseEntity<ApiResponse<MyTeamMemberResponseDto>> getMyTeamMemberDetail(
            @PathVariable UUID workerId,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        MyTeamMemberResponseDto detail = myTeamService.getMyTeamMemberDetail(userId, workerId);
        return ResponseEntity.ok(ApiResponse.ok(detail, "Lấy thông tin chi tiết thành viên My Team thành công"));
    }

    /**
     * UC-66: Thêm Worker vào My Team (qua workerId hoặc số điện thoại)
     */
    @Operation(summary = "Thêm Worker vào My Team", description = "Thêm một nhân sự vào danh sách My Team thông qua workerId hoặc số điện thoại.")
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<MyTeamMemberResponseDto>> addWorkerToMyTeam(
            @Valid @RequestBody AddMyTeamMemberRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        MyTeamMemberResponseDto added = myTeamService.addWorkerToMyTeam(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(added, "Thêm nhân sự vào My Team thành công"));
    }

    /**
     * Cập nhật ghi chú nội bộ, gắn sao yêu thích hoặc đánh dấu blacklist cho thành viên My Team
     */
    @Operation(summary = "Cập nhật thông tin thành viên My Team", description = "Cập nhật ghi chú nội bộ, gắn cờ yêu thích hoặc cấm đối với thành viên trong My Team.")
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PutMapping("/{workerId}")
    public ResponseEntity<ApiResponse<MyTeamMemberResponseDto>> updateMyTeamMember(
            @PathVariable UUID workerId,
            @Valid @RequestBody UpdateMyTeamMemberRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        MyTeamMemberResponseDto updated = myTeamService.updateMyTeamMember(userId, workerId, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Cập nhật thành viên My Team thành công"));
    }

    /**
     * UC-67: Xóa Worker khỏi My Team (Silent removal)
     */
    @Operation(summary = "Xóa Worker khỏi My Team", description = "Xóa Worker khỏi My Team một cách êm dịu (Silent removal) không ảnh hưởng tới ca đang nhận của Worker.")
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @DeleteMapping("/{workerId}")
    public ResponseEntity<ApiResponse<Void>> removeWorkerFromMyTeam(
            @PathVariable UUID workerId,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        myTeamService.removeWorkerFromMyTeam(userId, workerId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã xóa nhân sự khỏi danh sách My Team"));
    }

    /**
     * UC-68 (WBS 209.0): Gửi lời mời làm việc hàng loạt tới các thành viên My Team
     */
    @Operation(summary = "Mời hàng loạt từ My Team", description = "Gửi lời mời ưu tiên cho toàn bộ hoặc danh sách thành viên được chọn từ My Team tham gia ca làm việc.")
    @PreAuthorize("hasAnyRole('EVENT_ORGANIZER', 'ADMIN')")
    @PostMapping("/bulk-invite")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> sendBulkInvite(
            @Valid @RequestBody com.cani.dto.team.BulkInviteMyTeamRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        int sentCount = myTeamService.sendBulkInvite(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(
                java.util.Map.of("shiftId", request.getShiftId(), "sentCount", sentCount),
                "Đã gửi thành công " + sentCount + " lời mời ưu tiên tới thành viên My Team!"
        ));
    }
}
