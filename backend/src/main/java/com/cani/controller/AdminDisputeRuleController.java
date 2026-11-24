package com.cani.controller;

import com.cani.dto.admin.disputerule.CreateDisputeRuleRequestDto;
import com.cani.dto.admin.disputerule.DisputeRuleResponseDto;
import com.cani.dto.admin.disputerule.UpdateDisputeRuleRequestDto;
import com.cani.dto.response.ApiResponse;
import com.cani.entity.User;
import com.cani.entity.enums.DisputeType;
import com.cani.service.DisputeRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/dispute-rules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER_CARE')")
public class AdminDisputeRuleController {

    private final DisputeRuleService disputeRuleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DisputeRuleResponseDto>>> getRules(
            @RequestParam(value = "rule_type", required = false) DisputeType ruleType,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        List<DisputeRuleResponseDto> rules = disputeRuleService.getRules(ruleType, enabled);
        return ResponseEntity.ok(ApiResponse.ok(rules, "Lấy danh sách quy tắc khiếu nại thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DisputeRuleResponseDto>> getRuleById(@PathVariable("id") UUID id) {
        DisputeRuleResponseDto rule = disputeRuleService.getRuleById(id);
        return ResponseEntity.ok(ApiResponse.ok(rule, "Lấy thông tin quy tắc thành công"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DisputeRuleResponseDto>> createRule(
            @Valid @RequestBody CreateDisputeRuleRequestDto request,
            @RequestParam(value = "adminId", required = false) UUID explicitAdminId,
            Authentication authentication
    ) {
        UUID adminId = resolveAdminId(authentication, explicitAdminId);
        DisputeRuleResponseDto response = disputeRuleService.createRule(adminId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Tạo quy tắc khiếu nại thành công"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DisputeRuleResponseDto>> updateRule(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateDisputeRuleRequestDto request,
            @RequestParam(value = "adminId", required = false) UUID explicitAdminId,
            Authentication authentication
    ) {
        UUID adminId = resolveAdminId(authentication, explicitAdminId);
        DisputeRuleResponseDto response = disputeRuleService.updateRule(adminId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Cập nhật quy tắc khiếu nại thành công"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRule(
            @PathVariable("id") UUID id,
            @RequestParam(value = "adminId", required = false) UUID explicitAdminId,
            Authentication authentication
    ) {
        UUID adminId = resolveAdminId(authentication, explicitAdminId);
        disputeRuleService.deleteRule(adminId, id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã vô hiệu hóa quy tắc khiếu nại"));
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
