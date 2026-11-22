package com.cani.controller;

import com.cani.dto.group.*;
import com.cani.dto.response.ApiResponse;
import com.cani.dto.response.PageResponse;
import com.cani.security.SecurityUtils;
import com.cani.service.WorkerGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/worker-groups")
@RequiredArgsConstructor
@Tag(name = "Worker Group", description = "Quản lý Đội nhóm thợ sự kiện (UC-70, UC-71, FE-07)")
public class WorkerGroupController {

    private final WorkerGroupService workerGroupService;

    /**
     * UC-70: Worker (Trưởng nhóm) tạo nhóm thợ sự kiện mới
     */
    @Operation(summary = "Tạo nhóm thợ mới", description = "Tạo một nhóm thợ sự kiện mới do tài khoản hiện tại làm Trưởng nhóm.")
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<WorkerGroupResponseDto>> createGroup(
            @Valid @RequestBody CreateWorkerGroupRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        WorkerGroupResponseDto group = workerGroupService.createGroup(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(group, "Tạo nhóm thợ sự kiện thành công!"));
    }

    /**
     * Gia nhập nhóm bằng mã mời (Invite Code)
     */
    @Operation(summary = "Gia nhập nhóm bằng mã", description = "Worker nhập mã mời duy nhất của nhóm để tự động gia nhập.")
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/join-by-code")
    public ResponseEntity<ApiResponse<WorkerGroupResponseDto>> joinByCode(
            @Valid @RequestBody JoinGroupByCodeRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        WorkerGroupResponseDto group = workerGroupService.joinByCode(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(group, "Gia nhập đội nhóm thành công!"));
    }

    /**
     * Xem danh sách nhóm thợ mà Worker hiện tại đang tham gia
     */
    @Operation(summary = "Nhóm thợ của tôi", description = "Lấy danh sách các nhóm thợ mà Worker đang là thành viên hoặc trưởng nhóm.")
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<WorkerGroupResponseDto>>> getMyGroups(Authentication authentication) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        List<WorkerGroupResponseDto> list = workerGroupService.getMyGroups(userId);
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách nhóm thợ của bạn thành công"));
    }

    /**
     * Xem danh sách các lời mời gia nhập nhóm đang chờ xác nhận
     */
    @Operation(summary = "Lời mời vào nhóm thợ", description = "Lấy danh sách các nhóm thợ mà Worker được mời tham gia nhưng chưa xác nhận.")
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/my/invites")
    public ResponseEntity<ApiResponse<List<WorkerGroupResponseDto>>> getMyPendingInvites(Authentication authentication) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        List<WorkerGroupResponseDto> list = workerGroupService.getMyPendingInvites(userId);
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách lời mời vào nhóm thợ thành công"));
    }

    /**
     * Tìm kiếm và duyệt danh sách các nhóm thợ
     */
    @Operation(summary = "Tìm kiếm nhóm thợ", description = "Duyệt danh sách các nhóm thợ có phân trang và tìm kiếm theo tên hoặc kỹ năng.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<WorkerGroupResponseDto>>> searchGroups(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<WorkerGroupResponseDto> page = workerGroupService.searchGroups(search, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page, "Lấy danh sách nhóm thợ thành công"));
    }

    /**
     * Xem chi tiết thông tin một nhóm thợ
     */
    @Operation(summary = "Xem chi tiết nhóm thợ", description = "Xem thông tin tổng quan, số lượng thành viên, điểm uy tín và đánh giá trung bình của nhóm.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkerGroupResponseDto>> getGroupDetail(@PathVariable UUID id) {
        WorkerGroupResponseDto dto = workerGroupService.getGroupDetail(id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy thông tin nhóm thợ thành công"));
    }

    /**
     * UC-71: Cập nhật thông tin nhóm thợ (Chỉ Trưởng nhóm)
     */
    @Operation(summary = "Cập nhật nhóm thợ", description = "Chỉnh sửa tên, mô tả, kỹ năng trọng tâm và quy mô nhóm.")
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkerGroupResponseDto>> updateGroup(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkerGroupRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        WorkerGroupResponseDto dto = workerGroupService.updateGroup(userId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Cập nhật thông tin nhóm thợ thành công"));
    }

    /**
     * Xem danh sách thành viên trong nhóm thợ (Chỉ hiển thị đầy đủ SĐT cho thành viên trong nhóm hoặc Admin)
     */
    @Operation(summary = "Xem thành viên nhóm", description = "Lấy danh sách thành viên, chức danh, điểm uy tín và lịch sử làm việc của từng người trong nhóm.")
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN', 'EVENT_ORGANIZER')")
    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<WorkerGroupMemberResponseDto>>> getGroupMembers(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId(authentication);
        List<WorkerGroupMemberResponseDto> members = workerGroupService.getGroupMembers(currentUserId, id);
        return ResponseEntity.ok(ApiResponse.ok(members, "Lấy danh sách thành viên nhóm thành công"));
    }

    /**
     * UC-75 (WBS 223.0): Xem danh sách các ca làm việc của Nhóm thợ (View Group Shifts)
     */
    @Operation(summary = "Xem ca làm của nhóm", description = "Xem danh sách các ca sự kiện mà nhóm thợ được mời hoặc tham gia kèm tiến độ quân số.")
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @GetMapping("/{id}/shifts")
    public ResponseEntity<ApiResponse<List<GroupShiftResponseDto>>> getGroupShifts(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        List<GroupShiftResponseDto> shifts = workerGroupService.getGroupShifts(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(shifts, "Lấy danh sách ca làm việc của nhóm thành công"));
    }

    /**
     * Trưởng nhóm mời thêm Worker vào nhóm
     */
    @Operation(summary = "Mời thành viên vào nhóm", description = "Trưởng nhóm gửi lời mời gia nhập cho một Worker khác.")
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/{id}/members/invite")
    public ResponseEntity<ApiResponse<WorkerGroupMemberResponseDto>> inviteMember(
            @PathVariable UUID id,
            @Valid @RequestBody InviteGroupMemberRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        WorkerGroupMemberResponseDto member = workerGroupService.inviteMember(userId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(member, "Đã gửi lời mời tham gia nhóm thợ thành công"));
    }

    /**
     * Worker chấp nhận lời mời gia nhập nhóm
     */
    @Operation(summary = "Chấp nhận vào nhóm", description = "Xác nhận đồng ý tham gia nhóm thợ khi nhận được lời mời.")
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<WorkerGroupMemberResponseDto>> acceptInvite(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        WorkerGroupMemberResponseDto member = workerGroupService.acceptGroupInvite(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(member, "Chúc mừng! Bạn đã chính thức gia nhập nhóm thợ."));
    }

    /**
     * Trưởng nhóm xóa thành viên hoặc thành viên tự rời nhóm
     */
    @Operation(summary = "Xóa hoặc rời nhóm", description = "Trưởng nhóm xóa một thành viên, hoặc thành viên tự rút khỏi nhóm.")
    @PreAuthorize("hasAnyRole('WORKER', 'ADMIN')")
    @DeleteMapping("/{id}/members/{workerId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID workerId,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        workerGroupService.removeMember(userId, id, workerId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã cập nhật trạng thái thành viên nhóm"));
    }

    /**
     * Task 3: Xem danh sách đề xuất ca làm việc gửi cho nhóm (chỉ Leader hoặc ADMIN)
     */
    @Operation(summary = "Đề xuất ca cho nhóm", description = "Lấy danh sách đề xuất ca (PENDING + EXPIRED 24h qua) gửi đến nhóm, chỉ Trưởng nhóm hoặc Admin.")
    @PreAuthorize("hasAnyRole('WORKER','ADMIN')")
    @GetMapping("/{id}/group-proposals")
    public ResponseEntity<ApiResponse<List<GroupProposalResponseDto>>> listGroupProposals(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        List<GroupProposalResponseDto> list = workerGroupService.listGroupProposals(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(list, "Lấy danh sách đề xuất ca cho nhóm thành công"));
    }

    /**
     * Task 3: Re-check thành viên đủ điều kiện của một đề xuất ca (chỉ Leader hoặc ADMIN)
     */
    @Operation(summary = "Thành viên đủ điều kiện", description = "Re-check (không tạo data) trạng thái đủ điều kiện từng thành viên ACTIVE cho một đề xuất ca.")
    @PreAuthorize("hasAnyRole('WORKER','ADMIN')")
    @GetMapping("/{id}/group-proposals/eligible")
    public ResponseEntity<ApiResponse<GroupProposalResponseDto>> getGroupProposalEligible(
            @PathVariable UUID id,
            @RequestParam UUID proposalId,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        GroupProposalResponseDto dto = workerGroupService.getEligibleMembers(userId, id, proposalId);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Lấy danh sách thành viên đủ điều kiện thành công"));
    }

    /**
     * Task 3: Leader chấp nhận đề xuất ca cho nhóm (toàn bộ hoặc không, giữ slot atomic)
     */
    @Operation(summary = "Chấp nhận đề xuất ca cho nhóm", description = "Trưởng nhóm nhận ca trọn đội (Squad Pack); k×(gán ca + thanh toán + counter + FCM), rollback toàn bộ nếu thất bại.")
    @PreAuthorize("hasAnyRole('WORKER','ADMIN')")
    @PostMapping("/{id}/group-proposals/{proposalId}/accept")
    public ResponseEntity<ApiResponse<AcceptGroupProposalResponseDto>> acceptGroupProposal(
            @PathVariable UUID id,
            @PathVariable UUID proposalId,
            @Valid @RequestBody(required = false) GroupProposalAcceptRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        List<UUID> memberWorkerIds = request != null ? request.getMemberWorkerIds() : null;
        AcceptGroupProposalResponseDto dto = workerGroupService.acceptGroupProposal(
                userId, id, proposalId, memberWorkerIds);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Đội của bạn đã nhận ca làm việc thành công!"));
    }

    /**
     * Task 3: Leader từ chối đề xuất ca cho nhóm (re-dispatch slot còn thiếu)
     */
    @Operation(summary = "Từ chối đề xuất ca cho nhóm", description = "Trưởng nhóm từ chối đề xuất ca; hệ thống sẽ dispatch lại slot còn thiếu cho các kênh khác.")
    @PreAuthorize("hasAnyRole('WORKER','ADMIN')")
    @PostMapping("/{id}/group-proposals/{proposalId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectGroupProposal(
            @PathVariable UUID id,
            @PathVariable UUID proposalId,
            @Valid @RequestBody GroupProposalRejectRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        workerGroupService.rejectGroupProposal(userId, id, proposalId, request.getReason());
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã từ chối đề xuất ca cho nhóm"));
    }

    /**
     * Task 5: Chuyển giao Trưởng nhóm cho một thành viên ACTIVE khác (leader hiện tại hoặc ADMIN)
     */
    @Operation(summary = "Chuyển giao Trưởng nhóm", description = "Trưởng nhóm (hoặc Quản trị viên) chuyển quyền Trưởng nhóm sang một thành viên ACTIVE khác; các đề xuất ca PENDING của nhóm bị hết hạn.")
    @PreAuthorize("hasAnyRole('WORKER','ADMIN')")
    @PostMapping("/{id}/transfer-leader")
    public ResponseEntity<ApiResponse<Void>> transferLeader(
            @PathVariable UUID id,
            @Valid @RequestBody TransferLeaderRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        workerGroupService.transferLeader(userId, id, request.getNewLeaderWorkerId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã chuyển giao Trưởng nhóm thành công"));
    }
}
