package com.cani.service;

import com.cani.dto.directchat.ChatConversationDetailDto;
import com.cani.dto.directchat.ChatConversationSummaryDto;
import com.cani.dto.directchat.ChatMessageDto;
import com.cani.dto.directchat.SendChatMessageDto;
import com.cani.entity.*;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service quản lý tin nhắn trực tiếp giữa Event Organizer (EO) và Nhân sự (Worker).
 * Hỗ trợ 2 chế độ:
 * 1. Chat tự do 1-1 (DIRECT)
 * 2. Chat trong ca làm việc (SHIFT)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DirectChatService {

    private final ChatConversationRepository conversationRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ShiftRepository shiftRepository;

    /**
     * Mở hoặc tạo mới phòng chat 1-1 tự do giữa 2 người dùng (EO - Worker).
     */
    @Transactional
    public ChatConversationDetailDto getOrCreateDirectConversation(UUID currentUserId, UUID targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("Không thể tự chat với chính mình");
        }

        userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", targetUserId));

        List<UUID> existingIds = participantRepository.findDirectConversationIds(currentUserId, targetUserId);
        ChatConversation conversation;

        if (!existingIds.isEmpty()) {
            conversation = conversationRepository.findById(existingIds.get(0))
                    .orElseGet(() -> createDirectConversation(currentUserId, targetUserId));
        } else {
            conversation = createDirectConversation(currentUserId, targetUserId);
        }

        return getConversationDetail(conversation.getId(), currentUserId);
    }

    private ChatConversation createDirectConversation(UUID user1, UUID user2) {
        ChatConversation conv = conversationRepository.save(ChatConversation.builder()
                .type("DIRECT")
                .lastMessageAt(null)
                .lastMessagePreview(null)
                .build());

        participantRepository.save(ChatParticipant.builder()
                .conversationId(conv.getId())
                .userId(user1)
                .unreadCount(0)
                .lastReadAt(Instant.now())
                .build());

        participantRepository.save(ChatParticipant.builder()
                .conversationId(conv.getId())
                .userId(user2)
                .unreadCount(0)
                .lastReadAt(Instant.now())
                .build());

        return conv;
    }

    /**
     * Mở hoặc tạo mới phòng chat gắn liền với một Ca làm việc cụ thể.
     */
    @Transactional
    public ChatConversationDetailDto getOrCreateShiftConversation(UUID currentUserId, UUID shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Ca làm việc", "id", shiftId));

        ChatConversation conversation = conversationRepository.findByTypeAndShiftId("SHIFT", shiftId)
                .orElseGet(() -> {
                    return conversationRepository.save(ChatConversation.builder()
                            .type("SHIFT")
                            .shiftId(shiftId)
                            .lastMessageAt(null)
                            .lastMessagePreview(null)
                            .build());
                });

        // Đảm bảo người dùng hiện tại là thành viên
        if (!participantRepository.existsByConversationIdAndUserId(conversation.getId(), currentUserId)) {
            participantRepository.save(ChatParticipant.builder()
                    .conversationId(conversation.getId())
                    .userId(currentUserId)
                    .unreadCount(0)
                    .lastReadAt(Instant.now())
                    .build());
        }

        // Đảm bảo chủ quản ca (EO) cũng được thêm vào nếu người tạo ban đầu là Worker
        try {
            if (shift.getEvent() != null
                    && shift.getEvent().getEventOrganizer() != null
                    && shift.getEvent().getEventOrganizer().getUser() != null) {
                UUID eoUserId = shift.getEvent().getEventOrganizer().getUser().getUserId();
                if (!participantRepository.existsByConversationIdAndUserId(conversation.getId(), eoUserId)) {
                    participantRepository.save(ChatParticipant.builder()
                            .conversationId(conversation.getId())
                            .userId(eoUserId)
                            .unreadCount(0)
                            .lastReadAt(Instant.now())
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Không thể tự động thêm EO vào phòng chat ca: {}", e.getMessage());
        }

        return getConversationDetail(conversation.getId(), currentUserId);
    }

    /**
     * Lấy danh sách các cuộc hội thoại của người dùng hiện tại (kèm số tin chưa đọc).
     */
    public List<ChatConversationSummaryDto> getMyConversations(UUID currentUserId) {
        List<ChatParticipant> myParticipations = participantRepository.findByUserId(currentUserId);
        if (myParticipations.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChatConversationSummaryDto> result = new ArrayList<>();

        for (ChatParticipant part : myParticipations) {
            Optional<ChatConversation> convOpt = conversationRepository.findById(part.getConversationId());
            if (convOpt.isEmpty()) continue;
            ChatConversation conv = convOpt.get();

            ChatConversationSummaryDto summary = buildSummary(conv, part, currentUserId);
            result.add(summary);
        }

        // Sắp xếp hội thoại có tin nhắn mới nhất lên đầu
        result.sort((a, b) -> {
            if (a.getLastMessageAt() != null && b.getLastMessageAt() != null) {
                return b.getLastMessageAt().compareTo(a.getLastMessageAt());
            }
            if (a.getLastMessageAt() != null) return -1;
            if (b.getLastMessageAt() != null) return 1;
            return 0;
        });

        return result;
    }

    /**
     * Lấy nhanh số lượng hội thoại có tin nhắn chưa đọc và tổng số tin nhắn chưa đọc của người dùng.
     */
    public Map<String, Long> getUnreadCount(UUID currentUserId) {
        if (currentUserId == null) {
            return Map.of("unreadConversations", 0L, "unreadMessages", 0L);
        }
        long unreadConversations = participantRepository.countUnreadConversationsByUserId(currentUserId);
        long unreadMessages = participantRepository.sumUnreadMessagesByUserId(currentUserId);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("unreadConversations", Math.max(0L, unreadConversations));
        counts.put("unreadMessages", Math.max(0L, unreadMessages));
        return counts;
    }

    /**
     * Lấy chi tiết tin nhắn và đánh dấu đã đọc (reset unread_count = 0).
     */
    @Transactional
    public ChatConversationDetailDto getConversationDetail(UUID conversationId, UUID currentUserId) {
        ChatConversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng trò chuyện", "id", conversationId));

        ChatParticipant participant = participantRepository.findByConversationIdAndUserId(conversationId, currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Bạn không có quyền truy cập cuộc trò chuyện này"));

        // Reset tin chưa đọc
        if (participant.getUnreadCount() != null && participant.getUnreadCount() > 0) {
            participant.setUnreadCount(0);
            participant.setLastReadAt(Instant.now());
            participantRepository.save(participant);
        }

        List<ChatMessage> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        Map<UUID, User> userCache = new HashMap<>();

        List<ChatMessageDto> messageDtos = messages.stream().map(m -> {
            User sender = userCache.computeIfAbsent(m.getSenderId(), id -> userRepository.findById(id).orElse(null));
            return ChatMessageDto.builder()
                    .id(m.getId())
                    .conversationId(m.getConversationId())
                    .senderId(m.getSenderId())
                    .senderName(sender != null ? sender.getFullName() : "Người dùng")
                    .senderRole(sender != null && sender.getRole() != null ? sender.getRole().name() : "USER")
                    .senderAvatar(sender != null ? sender.getAvatarUrl() : null)
                    .messageText(m.getMessageText())
                    .attachmentUrl(m.getAttachmentUrl())
                    .createdAt(m.getCreatedAt())
                    .isMe(m.getSenderId().equals(currentUserId))
                    .build();
        }).collect(Collectors.toList());

        ChatConversationSummaryDto summary = buildSummary(conv, participant, currentUserId);

        return ChatConversationDetailDto.builder()
                .conversation(summary)
                .messages(messageDtos)
                .build();
    }

    /**
     * Gửi tin nhắn mới vào phòng trò chuyện.
     */
    @Transactional
    public ChatMessageDto sendMessage(UUID senderId, UUID conversationId, SendChatMessageDto req) {
        ChatConversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng trò chuyện", "id", conversationId));

        if (!participantRepository.existsByConversationIdAndUserId(conversationId, senderId)) {
            throw new AccessDeniedException("Bạn không phải thành viên của cuộc trò chuyện này");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", senderId));

        ChatMessage msg = messageRepository.save(ChatMessage.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .messageText(req.getMessageText().trim())
                .attachmentUrl(req.getAttachmentUrl())
                .build());

        // Cập nhật preview cuộc trò chuyện
        conv.setLastMessagePreview(formatMessagePreview(req.getMessageText()));
        conv.setLastMessageAt(Instant.now());
        conversationRepository.save(conv);

        // Tăng unread_count cho tất cả các thành viên khác trong phòng
        List<ChatParticipant> otherParticipants = participantRepository.findByConversationId(conversationId)
                .stream()
                .filter(p -> !p.getUserId().equals(senderId))
                .toList();

        for (ChatParticipant other : otherParticipants) {
            other.setUnreadCount((other.getUnreadCount() == null ? 0 : other.getUnreadCount()) + 1);
            participantRepository.save(other);
        }

        return ChatMessageDto.builder()
                .id(msg.getId())
                .conversationId(msg.getConversationId())
                .senderId(senderId)
                .senderName(sender.getFullName())
                .senderRole(sender.getRole() != null ? sender.getRole().name() : "USER")
                .senderAvatar(sender.getAvatarUrl())
                .messageText(msg.getMessageText())
                .attachmentUrl(msg.getAttachmentUrl())
                .createdAt(msg.getCreatedAt())
                .isMe(true)
                .build();
    }

    private ChatConversationSummaryDto buildSummary(ChatConversation conv, ChatParticipant myParticipant, UUID currentUserId) {
        String shiftCode = null;
        String shiftName = null;

        if (conv.getShiftId() != null) {
            shiftCode = formatShiftCode(conv.getShiftId());
            Optional<Shift> sOpt = shiftRepository.findById(conv.getShiftId());
            if (sOpt.isPresent()) {
                Shift s = sOpt.get();
                String evName = s.getEvent() != null ? s.getEvent().getName() : "Sự kiện";
                shiftName = evName + " - " + s.getJobType();
            }
        }

        // Tìm người đối thoại (trong chat 1-1 DIRECT) hoặc người tham gia khác (trong SHIFT)
        UUID targetUserId = null;
        String targetUserName = null;
        String targetUserAvatar = null;
        String targetUserRole = null;
        String targetUserPhone = null;

        List<ChatParticipant> allParts = participantRepository.findByConversationId(conv.getId());
        Optional<ChatParticipant> otherPart = allParts.stream()
                .filter(p -> !p.getUserId().equals(currentUserId))
                .findFirst();

        if (otherPart.isPresent()) {
            targetUserId = otherPart.get().getUserId();
            Optional<User> uOpt = userRepository.findById(targetUserId);
            if (uOpt.isPresent()) {
                User u = uOpt.get();
                targetUserName = u.getFullName();
                targetUserAvatar = u.getAvatarUrl();
                targetUserRole = u.getRole() != null ? u.getRole().name() : "USER";
                targetUserPhone = u.getPhone();
            }
        }

        return ChatConversationSummaryDto.builder()
                .id(conv.getId())
                .type(conv.getType())
                .shiftId(conv.getShiftId())
                .shiftCode(shiftCode)
                .shiftName(shiftName)
                .targetUserId(targetUserId)
                .targetUserName(targetUserName != null ? targetUserName : (shiftName != null ? shiftName : "Nhóm trao đổi"))
                .targetUserAvatar(targetUserAvatar)
                .targetUserRole(targetUserRole)
                .targetUserPhone(targetUserPhone != null ? targetUserPhone : "—")
                .lastMessagePreview(conv.getLastMessagePreview())
                .lastMessageAt(conv.getLastMessageAt() != null ? conv.getLastMessageAt() : conv.getCreatedAt())
                .unreadCount(myParticipant.getUnreadCount() != null ? myParticipant.getUnreadCount() : 0)
                .build();
    }

    private String formatShiftCode(UUID shiftId) {
        if (shiftId == null) return "SH-0000";
        String clean = shiftId.toString().replace("-", "").toUpperCase();
        return "SH-" + (clean.length() >= 6 ? clean.substring(0, 6) : clean);
    }

    private String formatMessagePreview(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 97) + "..." : trimmed;
    }
}
