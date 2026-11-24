package com.cani.service;

import com.cani.dto.admin.blacklist.CreateBlacklistRequestDto;
import com.cani.entity.AuditLog;
import com.cani.entity.Blacklist;
import com.cani.entity.User;
import com.cani.entity.enums.BlacklistIdentifierType;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.BlacklistRepository;
import com.cani.repository.UserRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBlacklistServiceTest {

    @Mock
    private BlacklistRepository blacklistRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AdminBlacklistService adminBlacklistService;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
    }

    @Test
    @DisplayName("addToBlacklist - Thành công và ghi nhận AuditLog")
    void testAddToBlacklist_Success() {
        CreateBlacklistRequestDto request = CreateBlacklistRequestDto.builder()
                .identifier("012345678901")
                .identifierType(BlacklistIdentifierType.CCCD)
                .permanent(true)
                .reason("Làm giả hồ sơ eKYC")
                .build();

        when(blacklistRepository.existsByIdentifierAndIdentifierType("012345678901", BlacklistIdentifierType.CCCD))
                .thenReturn(false);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(User.builder().userId(adminId).fullName("Admin").build()));
        when(blacklistRepository.save(any(Blacklist.class))).thenAnswer(inv -> {
            Blacklist b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        var response = adminBlacklistService.addToBlacklist(adminId, request);

        assertThat(response.getIdentifier()).isEqualTo("012345678901");
        assertThat(response.getPermanent()).isTrue();
        verify(blacklistRepository, times(1)).save(any(Blacklist.class));
        verify(auditLogService, times(1)).logAction(any(AuditLog.class));
    }

    @Test
    @DisplayName("removeFromBlacklist - Ném BadRequest 400 khi cố xóa bản ghi permanent=true")
    void testRemoveFromBlacklist_Permanent_ThrowsBadRequest() {
        UUID blacklistId = UUID.randomUUID();
        Blacklist permanentBlacklist = Blacklist.builder()
                .id(blacklistId)
                .identifier("012345678901")
                .identifierType(BlacklistIdentifierType.CCCD)
                .permanent(true)
                .build();

        when(blacklistRepository.findById(blacklistId)).thenReturn(Optional.of(permanentBlacklist));

        assertThatThrownBy(() -> adminBlacklistService.removeFromBlacklist(adminId, blacklistId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không thể xóa bản ghi danh sách đen vĩnh viễn");

        verify(blacklistRepository, never()).delete(any(Blacklist.class));
        verify(auditLogService, never()).logAction(any());
    }

    @Test
    @DisplayName("removeFromBlacklist - Xóa thành công khi permanent=false")
    void testRemoveFromBlacklist_NonPermanent_Success() {
        UUID blacklistId = UUID.randomUUID();
        Blacklist nonPermanent = Blacklist.builder()
                .id(blacklistId)
                .identifier("0987654321")
                .identifierType(BlacklistIdentifierType.PHONE)
                .permanent(false)
                .build();

        when(blacklistRepository.findById(blacklistId)).thenReturn(Optional.of(nonPermanent));

        adminBlacklistService.removeFromBlacklist(adminId, blacklistId);

        verify(blacklistRepository, times(1)).delete(nonPermanent);
        verify(auditLogService, times(1)).logAction(any(AuditLog.class));
    }
}
