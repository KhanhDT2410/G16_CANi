package com.cani.service;

import com.cani.dto.admin.disputerule.CreateDisputeRuleRequestDto;
import com.cani.entity.AuditLog;
import com.cani.entity.DisputeRule;
import com.cani.entity.enums.DisputeOutcome;
import com.cani.entity.enums.DisputeType;
import com.cani.repository.DisputeRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeRuleServiceTest {

    @Mock
    private DisputeRuleRepository disputeRuleRepository;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DisputeRuleService disputeRuleService;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
    }

    @Test
    @DisplayName("createRule - Tạo quy tắc thành công và ghi AuditLog")
    void testCreateRule_Success() {
        CreateDisputeRuleRequestDto request = CreateDisputeRuleRequestDto.builder()
                .code("RULE_TEST")
                .behavior("Hành vi kiểm thử")
                .violatingParty("WORKER")
                .sanctionForm("Trừ điểm")
                .specificPenalty("Khấu trừ -10 RS theo chuẩn")
                .ruleType(DisputeType.ATTENDANCE)
                .faultOutcome(DisputeOutcome.WORKER_FAULT)
                .sanctionText("Khấu trừ 10 RS và cảnh cáo")
                .build();

        when(disputeRuleRepository.save(any(DisputeRule.class))).thenAnswer(inv -> {
            DisputeRule r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        var response = disputeRuleService.createRule(adminId, request);

        assertThat(response.getCode()).isEqualTo("RULE_TEST");
        assertThat(response.getRuleType()).isEqualTo(DisputeType.ATTENDANCE);
        verify(disputeRuleRepository, times(1)).save(any(DisputeRule.class));
        verify(auditLogService, times(1)).logAction(any(AuditLog.class));
    }

    @Test
    @DisplayName("deleteRule - Soft delete thiết lập enabled = false")
    void testDeleteRule_SoftDelete() {
        UUID ruleId = UUID.randomUUID();
        DisputeRule rule = DisputeRule.builder()
                .id(ruleId)
                .code("RULE_EXISTING")
                .enabled(true)
                .build();

        when(disputeRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule));

        disputeRuleService.deleteRule(adminId, ruleId);

        assertThat(rule.getEnabled()).isFalse();
        verify(disputeRuleRepository, times(1)).save(rule);
        verify(auditLogService, times(1)).logAction(any(AuditLog.class));
    }
}
