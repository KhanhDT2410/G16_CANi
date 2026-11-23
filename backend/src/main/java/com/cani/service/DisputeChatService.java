package com.cani.service;

import com.cani.dto.dispute.*;
import com.cani.entity.*;
import com.cani.entity.enums.DisputeStatus;
import com.cani.entity.enums.UserRole;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service quản lý kênh chat đối chất tranh chấp đa bên theo mô hình Hub-and-Spoke.
 * Trọng tài (Admin/CSKH) đối thoại với cả 2 bên (Worker & EO).
 * Mỗi bên (Worker/EO) chỉ trao đổi độc lập với Trọng tài mà không nhắn trực tiếp cho nhau.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeChatService {

    private final DisputeRepository disputeRepository;
    private final SupportConversationRepository supportConversationRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final UserRepository userRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public DisputeDualChatDto getDisputeChat(UUID disputeId, UUID currentUserId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vụ việc tranh chấp: " + disputeId));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        DisputeParties parties = resolveDisputeParties(dispute);

        boolean isArbitrator = currentUser.getRole() == UserRole.ADMIN || currentUser.getRole() == UserRole.CUSTOMER_CARE;
        boolean isWorker = parties.workerUser != null && parties.workerUser.getUserId().equals(currentUserId);
        boolean isEo = parties.eoUser != null && parties.eoUser.getUserId().equals(currentUserId);

        if (!isArbitrator && !isWorker && !isEo) {
            throw new AccessDeniedException("Bạn không có quyền truy cập kênh hội thoại tranh chấp này");
        }

        SupportConversation workerConv = null;
        if (parties.workerUser != null) {
            workerConv = getOrCreateConversation(disputeId, parties.workerUser.getUserId(), "WORKER");
        }

        SupportConversation eoConv = null;
        if (parties.eoUser != null) {
            eoConv = getOrCreateConversation(disputeId, parties.eoUser.getUserId(), "EO");
        }

        ShiftContext shiftCtx = resolveShiftContext(dispute);

        DisputePartyChatDto workerDto = null;
        if ((isArbitrator || isWorker) && workerConv != null && parties.workerUser != null) {
            List<SupportMessage> messages = supportMessageRepository.findByConversationIdOrderByCreatedAtAsc(workerConv.getId());
            workerDto = toPartyChatDto(workerConv, parties.workerUser, messages, currentUserId);
        }

        DisputePartyChatDto eoDto = null;
        if ((isArbitrator || isEo) && eoConv != null && parties.eoUser != null) {
            List<SupportMessage> messages = supportMessageRepository.findByConversationIdOrderByCreatedAtAsc(eoConv.getId());
            eoDto = toPartyChatDto(eoConv, parties.eoUser, messages, currentUserId);
        }

        return DisputeDualChatDto.builder()
                .disputeId(dispute.getDisputeId())
                .disputeTitle(dispute.getTitle())
                .disputeStatus(dispute.getStatus() != null ? dispute.getStatus().name() : "OPEN")
                .assignmentId(dispute.getAssignmentId())
                .shiftId(shiftCtx.shiftId)
                .shiftTitle(shiftCtx.shiftTitle)
                .eventName(shiftCtx.eventName)
                .shiftStartTime(shiftCtx.startTime)
                .shiftEndTime(shiftCtx.endTime)
                .currentRole(currentUser.getRole().name())
                .workerConversation(workerDto)
                .eoConversation(eoDto)
                .build();
    }

    @Transactional
    public DisputeChatMessageDto sendMessage(UUID disputeId, UUID currentUserId, SendDisputeChatMessageDto request) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vụ việc tranh chấp: " + disputeId));

        if (dispute.getStatus() == DisputeStatus.FINAL_RESOLVED) {
            throw new BadRequestException("Vụ việc đã có phán quyết cuối cùng (FINAL_RESOLVED), kênh trao đổi đã được khép lại");
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        DisputeParties parties = resolveDisputeParties(dispute);
        boolean isArbitrator = currentUser.getRole() == UserRole.ADMIN || currentUser.getRole() == UserRole.CUSTOMER_CARE;
        boolean isWorker = parties.workerUser != null && parties.workerUser.getUserId().equals(currentUserId);
        boolean isEo = parties.eoUser != null && parties.eoUser.getUserId().equals(currentUserId);

        if (!isArbitrator && !isWorker && !isEo) {
            throw new AccessDeniedException("Bạn không có quyền nhắn tin trong vụ việc này");
        }

        String targetParty = request.getTargetParty();
        if (isWorker) {
            targetParty = "WORKER";
        } else if (isEo) {
            targetParty = "EO";
        } else {
            if (targetParty == null || (!"WORKER".equalsIgnoreCase(targetParty) && !"EO".equalsIgnoreCase(targetParty))) {
                throw new BadRequestException("Bên nhận chỉ định không hợp lệ (phải là WORKER hoặc EO)");
            }
            targetParty = targetParty.toUpperCase();
        }

        User partyUser = "WORKER".equals(targetParty) ? parties.workerUser : parties.eoUser;
        if (partyUser == null) {
            throw new BadRequestException("Không tìm thấy người tham gia tương ứng với bên: " + targetParty);
        }

        SupportConversation conv = getOrCreateConversation(disputeId, partyUser.getUserId(), targetParty);
        ShiftContext shiftCtx = resolveShiftContext(dispute);

        SupportMessage message = SupportMessage.builder()
                .conversationId(conv.getId())
                .senderId(currentUserId)
                .senderRole(currentUser.getRole().name())
                .senderName(currentUser.getFullName())
                .messageText(request.getMessageText().trim())
                .attachmentUrl(request.getAttachmentUrl())
                .shiftId(shiftCtx.shiftId)
                .isRead(false)
                .build();

        SupportMessage saved = supportMessageRepository.save(message);

        conv.setLastMessageAt(Instant.now());
        conv.setLastMessagePreview(saved.getMessageText());
        if (isArbitrator) {
            conv.setUnreadUserCount(conv.getUnreadUserCount() + 1);
        } else {
            conv.setUnreadStaffCount(conv.getUnreadStaffCount() + 1);
        }
        supportConversationRepository.save(conv);

        return DisputeChatMessageDto.builder()
                .id(saved.getId())
                .conversationId(saved.getConversationId())
                .senderId(saved.getSenderId())
                .senderName(saved.getSenderName())
                .senderRole(saved.getSenderRole())
                .messageText(saved.getMessageText())
                .attachmentUrl(saved.getAttachmentUrl())
                .shiftId(saved.getShiftId())
                .isRead(saved.getIsRead())
                .isMe(true)
                .createdAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : Instant.now())
                .build();
    }

    @Transactional
    public void purgeExpiredRegularChats() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = chatMessageRepository.deleteByCreatedAtBefore(cutoff);
        log.info("[Retention] Đã dọn dẹp {} tin nhắn chat ca làm việc quá 7 ngày", deleted);
    }

    @Transactional
    public void cleanupResolvedDisputeChats() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.DAYS);
        List<SupportConversation> disputeConvs = supportConversationRepository.findByDisputeIdIsNotNull();
        List<UUID> convIdsToDelete = new ArrayList<>();

        for (SupportConversation conv : disputeConvs) {
            if (conv.getDisputeId() != null) {
                Dispute d = disputeRepository.findById(conv.getDisputeId()).orElse(null);
                if (d != null && (d.getStatus() == DisputeStatus.RESOLVED || d.getStatus() == DisputeStatus.FINAL_RESOLVED)) {
                    Instant resolvedTime = d.getFinalDecidedAt() != null ? d.getFinalDecidedAt() : d.getResolvedAt();
                    if (resolvedTime != null && resolvedTime.isBefore(cutoff)) {
                        convIdsToDelete.add(conv.getId());
                    }
                }
            }
        }

        if (!convIdsToDelete.isEmpty()) {
            supportMessageRepository.deleteByConversationIdIn(convIdsToDelete);
            supportConversationRepository.deleteAllById(convIdsToDelete);
            log.info("[Retention] Đã lưu trữ và xoá {} phiên chat tranh chấp đã kết luận quá 5 ngày", convIdsToDelete.size());
        }
    }

    private SupportConversation getOrCreateConversation(UUID disputeId, UUID userId, String disputeParty) {
        return supportConversationRepository.findByDisputeIdAndDisputeParty(disputeId, disputeParty)
                .orElseGet(() -> {
                    SupportConversation newConv = SupportConversation.builder()
                            .userId(userId)
                            .disputeId(disputeId)
                            .disputeParty(disputeParty)
                            .status("ACTIVE")
                            .unreadStaffCount(0)
                            .unreadUserCount(0)
                            .build();
                    return supportConversationRepository.save(newConv);
                });
    }

    private DisputePartyChatDto toPartyChatDto(SupportConversation conv, User user, List<SupportMessage> messages, UUID currentUserId) {
        List<DisputeChatMessageDto> msgDtos = messages.stream().map(m -> DisputeChatMessageDto.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .senderId(m.getSenderId())
                .senderName(m.getSenderName())
                .senderRole(m.getSenderRole())
                .messageText(m.getMessageText())
                .attachmentUrl(m.getAttachmentUrl())
                .shiftId(m.getShiftId())
                .isRead(m.getIsRead())
                .isMe(m.getSenderId().equals(currentUserId))
                .createdAt(m.getCreatedAt())
                .build()
        ).toList();

        return DisputePartyChatDto.builder()
                .conversationId(conv.getId())
                .party(conv.getDisputeParty())
                .userId(user.getUserId())
                .userName(user.getFullName())
                .userPhone(user.getPhone())
                .userAvatar(user.getAvatarUrl())
                .status(conv.getStatus())
                .unreadCount(conv.getUnreadStaffCount())
                .lastMessageAt(conv.getLastMessageAt())
                .messages(msgDtos)
                .build();
    }

    @Transactional(readOnly = true)
    public DisputeUnreadSummaryDto getUnreadSummary(UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        boolean isArbitrator = currentUser.getRole() == UserRole.ADMIN || currentUser.getRole() == UserRole.CUSTOMER_CARE;

        if (isArbitrator) {
            List<SupportConversation> unreadConvs = supportConversationRepository
                    .findByDisputeIdIsNotNullAndUnreadStaffCountGreaterThan(0);
            long totalDisputes = unreadConvs.stream()
                    .map(SupportConversation::getDisputeId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            int totalMsgs = unreadConvs.stream()
                    .mapToInt(c -> c.getUnreadStaffCount() != null ? c.getUnreadStaffCount() : 0)
                    .sum();
            return DisputeUnreadSummaryDto.builder()
                    .totalUnreadDisputes((int) totalDisputes)
                    .totalUnreadMessages(totalMsgs)
                    .build();
        } else {
            List<SupportConversation> unreadConvs = supportConversationRepository
                    .findByDisputeIdIsNotNullAndUserIdAndUnreadUserCountGreaterThan(currentUserId, 0);
            long totalDisputes = unreadConvs.stream()
                    .map(SupportConversation::getDisputeId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
            int totalMsgs = unreadConvs.stream()
                    .mapToInt(c -> c.getUnreadUserCount() != null ? c.getUnreadUserCount() : 0)
                    .sum();
            return DisputeUnreadSummaryDto.builder()
                    .totalUnreadDisputes((int) totalDisputes)
                    .totalUnreadMessages(totalMsgs)
                    .build();
        }
    }

    @Transactional
    public void markChatRead(UUID disputeId, UUID currentUserId, String party) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        boolean isArbitrator = currentUser.getRole() == UserRole.ADMIN || currentUser.getRole() == UserRole.CUSTOMER_CARE;

        if (isArbitrator) {
            if ("WORKER".equalsIgnoreCase(party)) {
                supportConversationRepository.findByDisputeIdAndDisputeParty(disputeId, "WORKER")
                        .ifPresent(c -> {
                            c.setUnreadStaffCount(0);
                            supportConversationRepository.save(c);
                        });
            } else if ("EO".equalsIgnoreCase(party)) {
                supportConversationRepository.findByDisputeIdAndDisputeParty(disputeId, "EO")
                        .ifPresent(c -> {
                            c.setUnreadStaffCount(0);
                            supportConversationRepository.save(c);
                        });
            } else {
                List<SupportConversation> convs = supportConversationRepository.findByDisputeId(disputeId);
                for (SupportConversation c : convs) {
                    c.setUnreadStaffCount(0);
                }
                supportConversationRepository.saveAll(convs);
            }
        } else if (currentUser.getRole() == UserRole.EVENT_ORGANIZER) {
            supportConversationRepository.findByDisputeIdAndDisputeParty(disputeId, "EO")
                    .filter(c -> c.getUserId().equals(currentUserId))
                    .ifPresent(c -> {
                        c.setUnreadUserCount(0);
                        supportConversationRepository.save(c);
                    });
        } else if (currentUser.getRole() == UserRole.WORKER) {
            supportConversationRepository.findByDisputeIdAndDisputeParty(disputeId, "WORKER")
                    .filter(c -> c.getUserId().equals(currentUserId))
                    .ifPresent(c -> {
                        c.setUnreadUserCount(0);
                        supportConversationRepository.save(c);
                    });
        }
    }

    private record DisputeParties(User workerUser, User eoUser) {}

    private DisputeParties resolveDisputeParties(Dispute dispute) {
        User workerUser = null;
        User eoUser = null;

        if (dispute.getAssignmentId() != null) {
            ShiftAssignment sa = shiftAssignmentRepository.findById(dispute.getAssignmentId()).orElse(null);
            if (sa != null) {
                if (sa.getWorker() != null && sa.getWorker().getUser() != null) {
                    workerUser = sa.getWorker().getUser();
                }
                if (sa.getShift() != null && sa.getShift().getEvent() != null
                        && sa.getShift().getEvent().getEventOrganizer() != null
                        && sa.getShift().getEvent().getEventOrganizer().getUser() != null) {
                    eoUser = sa.getShift().getEvent().getEventOrganizer().getUser();
                }
            }
        }

        User complainant = dispute.getComplainantId() != null
                ? userRepository.findById(dispute.getComplainantId()).orElse(null) : null;
        User respondent = dispute.getRespondentId() != null
                ? userRepository.findById(dispute.getRespondentId()).orElse(null) : null;

        if (complainant != null) {
            if (complainant.getRole() == UserRole.WORKER && workerUser == null) workerUser = complainant;
            else if (complainant.getRole() == UserRole.EVENT_ORGANIZER && eoUser == null) eoUser = complainant;
        }
        if (respondent != null) {
            if (respondent.getRole() == UserRole.WORKER && workerUser == null) workerUser = respondent;
            else if (respondent.getRole() == UserRole.EVENT_ORGANIZER && eoUser == null) eoUser = respondent;
        }

        // Bổ sung dự phòng tối đa: Không để rơi vào trạng thái null nếu có người tham gia
        if (workerUser == null) {
            if (complainant != null && !complainant.equals(eoUser)) workerUser = complainant;
            else if (respondent != null && !respondent.equals(eoUser)) workerUser = respondent;
            else workerUser = complainant != null ? complainant : respondent;
        }
        if (eoUser == null) {
            if (respondent != null && !respondent.equals(workerUser)) eoUser = respondent;
            else if (complainant != null && !complainant.equals(workerUser)) eoUser = complainant;
            else eoUser = respondent != null ? respondent : complainant;
        }

        return new DisputeParties(workerUser, eoUser);
    }

    private record ShiftContext(UUID shiftId, String shiftTitle, String eventName, Instant startTime, Instant endTime) {}

    private ShiftContext resolveShiftContext(Dispute dispute) {
        if (dispute.getAssignmentId() != null) {
            ShiftAssignment sa = shiftAssignmentRepository.findById(dispute.getAssignmentId()).orElse(null);
            if (sa != null && sa.getShift() != null) {
                Shift shift = sa.getShift();
                String eventName = shift.getEvent() != null ? shift.getEvent().getName() : null;
                return new ShiftContext(shift.getShiftId(), shift.getJobType(), eventName, shift.getStartTime(), shift.getEndTime());
            }
        }
        return new ShiftContext(null, null, null, null, null);
    }
}
