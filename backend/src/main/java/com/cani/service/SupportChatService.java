package com.cani.service;

import com.cani.dto.chat.ChatMessageResponseDto;
import com.cani.dto.chat.SendChatMessageRequestDto;
import com.cani.dto.chat.StaffChatUnreadSummaryDto;
import com.cani.dto.chat.SupportConversationDetailDto;
import com.cani.dto.chat.SupportConversationSummaryDto;
import com.cani.entity.Shift;
import com.cani.entity.SupportConversation;
import com.cani.entity.SupportMessage;
import com.cani.entity.User;
import com.cani.entity.enums.UserRole;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.ShiftRepository;
import com.cani.repository.SupportConversationRepository;
import com.cani.repository.SupportMessageRepository;
import com.cani.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dịch vụ xử lý hội thoại tin nhắn trực tuyến thời gian thực (Live Chat).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SupportChatService {

        private final SupportConversationRepository conversationRepository;
        private final SupportMessageRepository messageRepository;
        private final UserRepository userRepository;
        private final ShiftRepository shiftRepository;

        /**
         * Lấy hoặc khởi tạo phiên chat của Người dùng hiện tại.
         */
        @Transactional
        public SupportConversationDetailDto getMyConversation(UUID userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));

                SupportConversation conv = conversationRepository.findByUserIdAndDisputeIdIsNull(userId)
                                .orElseGet(() -> conversationRepository.save(
                                                SupportConversation.builder()
                                                                .userId(userId)
                                                                .status("ACTIVE")
                                                                .lastMessageAt(Instant.now())
                                                                .lastMessagePreview(
                                                                                "Chào mừng bạn đến với kênh hỗ trợ trực tuyến CANi")
                                                                .unreadStaffCount(0)
                                                                .unreadUserCount(0)
                                                                .build()));

                // Đánh dấu các tin nhắn gửi từ CSKH là đã đọc khi User mở khung chat
                List<SupportMessage> unreadFromStaff = messageRepository
                                .findByConversationIdAndIsReadFalseAndSenderRole(conv.getId(), "CUSTOMER_CARE");
                unreadFromStaff.addAll(messageRepository
                                .findByConversationIdAndIsReadFalseAndSenderRole(conv.getId(), "ADMIN"));

                if (!unreadFromStaff.isEmpty()) {
                        unreadFromStaff.forEach(m -> m.setIsRead(true));
                        messageRepository.saveAll(unreadFromStaff);
                        conv.setUnreadUserCount(0);
                        conversationRepository.save(conv);
                }

                List<SupportMessage> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId());

                return SupportConversationDetailDto.builder()
                                .conversation(toSummaryDto(conv, user))
                                .messages(messages.stream().map(this::toMessageDto).toList())
                                .build();
        }

        /**
         * Lấy danh sách toàn bộ tin nhắn trao đổi trực tiếp trong một Ca làm việc (giữa EO, Worker và CSKH).
         */
        public List<ChatMessageResponseDto> getShiftMessages(UUID shiftId) {
                return messageRepository.findByShiftIdOrderByCreatedAtAsc(shiftId)
                                .stream()
                                .map(this::toMessageDto)
                                .toList();
        }



        /**
         * Người dùng gửi tin nhắn hỗ trợ.
         */
        @Transactional
        public ChatMessageResponseDto sendUserMessage(UUID userId, SendChatMessageRequestDto req) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));

                SupportConversation conv = conversationRepository.findByUserIdAndDisputeIdIsNull(userId)
                                .orElseGet(() -> conversationRepository.save(
                                                SupportConversation.builder()
                                                                .userId(userId)
                                                                .status("ACTIVE")
                                                                .build()));

                SupportMessage msg = SupportMessage.builder()
                                .conversationId(conv.getId())
                                .senderId(userId)
                                .senderRole(user.getRole() != null ? user.getRole().name() : "USER")
                                .senderName(user.getFullName())
                                .messageText(req.getMessageText())
                                .attachmentUrl(req.getAttachmentUrl())
                                .shiftId(req.getShiftId())
                                .isRead(false)
                                .build();

                msg = messageRepository.save(msg);

                // Cập nhật preview (cắt ngắn chống tràn cột 500 ký tự) và số tin chưa đọc phía CSKH
                conv.setLastMessagePreview(formatMessagePreview(req.getMessageText()));
                conv.setLastMessageAt(Instant.now());
                conv.setUnreadStaffCount((conv.getUnreadStaffCount() == null ? 0 : conv.getUnreadStaffCount()) + 1);
                conversationRepository.save(conv);

                return toMessageDto(msg);
        }

        /**
         * CSKH / Admin lấy danh sách toàn bộ người dùng thuộc Role EVENT_ORGANIZER và
         * WORKER
         * kèm trạng thái tin nhắn trực tuyến để có thể chủ động chat trực tiếp.
         */
        public List<SupportConversationSummaryDto> getStaffConversations(String query) {
                List<User> targetUsers = userRepository
                                .findByRoleIn(List.of(UserRole.EVENT_ORGANIZER, UserRole.WORKER));
                List<SupportConversation> allConvs = conversationRepository.findByDisputeIdIsNull();

                Map<UUID, SupportConversation> convMap = allConvs.stream()
                                .collect(Collectors.toMap(SupportConversation::getUserId, c -> c, (a, b) -> a));

                // Batch-load tin nhắn ca làm gần nhất tránh N+1 truy vấn
                List<UUID> activeConvIds = allConvs.stream()
                                .map(SupportConversation::getId)
                                .filter(Objects::nonNull)
                                .toList();

                Map<UUID, UUID> convToShiftId = new HashMap<>();
                if (!activeConvIds.isEmpty()) {
                        List<SupportMessage> shiftMsgs = messageRepository.findShiftMessagesForConversations(activeConvIds);
                        for (SupportMessage sm : shiftMsgs) {
                                convToShiftId.putIfAbsent(sm.getConversationId(), sm.getShiftId());
                        }
                }

                Set<UUID> allShiftIds = new HashSet<>(convToShiftId.values());
                Map<UUID, Shift> shiftMap = new HashMap<>();
                if (!allShiftIds.isEmpty()) {
                        shiftRepository.findAllById(allShiftIds).forEach(s -> shiftMap.put(s.getShiftId(), s));
                }

                String q = query != null ? query.trim().toLowerCase() : "";
                List<SupportConversationSummaryDto> list = new ArrayList<>();

                for (User u : targetUsers) {
                        SupportConversation conv = convMap.get(u.getUserId());
                        if (conv != null) {
                                UUID sId = convToShiftId.get(conv.getId());
                                Shift s = sId != null ? shiftMap.get(sId) : null;
                                list.add(toSummaryDtoWithShift(conv, u, sId, s));
                        } else {
                                list.add(SupportConversationSummaryDto.builder()
                                                .id(null)
                                                .userId(u.getUserId())
                                                .userName(u.getFullName() != null ? u.getFullName() : "Người dùng CANi")
                                                .userPhone(u.getPhone() != null ? u.getPhone() : "—")
                                                .userEmail(u.getEmail() != null ? u.getEmail() : "—")
                                                .userRole(u.getRole() != null ? u.getRole().name() : "USER")
                                                .userAvatar(u.getAvatarUrl())
                                                .lastMessagePreview("Chưa có tin nhắn")
                                                .lastMessageAt(null)
                                                .unreadStaffCount(0)
                                                .unreadUserCount(0)
                                                .status("NEW")
                                                .build());
                        }
                }

                // Lọc theo từ khóa tìm kiếm (tên, SĐT, email) nếu có
                List<SupportConversationSummaryDto> filtered = list.stream()
                                .filter(dto -> {
                                        if (q.isEmpty())
                                                 return true;
                                        boolean matchName = dto.getUserName() != null
                                                        && dto.getUserName().toLowerCase().contains(q);
                                        boolean matchPhone = dto.getUserPhone() != null
                                                        && dto.getUserPhone().toLowerCase().contains(q);
                                        boolean matchEmail = dto.getUserEmail() != null
                                                        && dto.getUserEmail().toLowerCase().contains(q);
                                        return matchName || matchPhone || matchEmail;
                                })
                                .collect(Collectors.toList());

                // Sắp xếp ưu tiên:
                // 1. Có tin nhắn chưa đọc phía CSKH (unreadStaffCount giảm dần)
                // 2. Có tin nhắn gần nhất (lastMessageAt giảm dần)
                // 3. Người dùng chưa từng chat (theo tên A-Z)
                filtered.sort((a, b) -> {
                        int unreadA = a.getUnreadStaffCount() != null ? a.getUnreadStaffCount() : 0;
                        int unreadB = b.getUnreadStaffCount() != null ? b.getUnreadStaffCount() : 0;
                        if (unreadA != unreadB) {
                                return Integer.compare(unreadB, unreadA);
                        }
                        if (a.getLastMessageAt() != null && b.getLastMessageAt() != null) {
                                return b.getLastMessageAt().compareTo(a.getLastMessageAt());
                        }
                        if (a.getLastMessageAt() != null)
                                return -1;
                        if (b.getLastMessageAt() != null)
                                return 1;
                        String nameA = a.getUserName() != null ? a.getUserName() : "";
                        String nameB = b.getUserName() != null ? b.getUserName() : "";
                        return nameA.compareToIgnoreCase(nameB);
                });

                return filtered;
        }

        /**
         * Lấy tóm tắt số lượng tin nhắn chưa đọc và các cuộc hội thoại mới nhất cho thanh điều hướng Header & Sidebar.
         * Tối ưu hóa cực đại (siêu nhẹ ~15ms) phục vụ Polling thời gian thực không gây nghẽn Connection Pool.
         */
        public StaffChatUnreadSummaryDto getStaffUnreadSummary() {
                Integer totalUnread = conversationRepository.sumUnreadStaffCount();
                int unreadConvs = conversationRepository.countUnreadStaffConversations();
                List<SupportConversation> recentConvs = conversationRepository
                                .findRecentSupportConversations(org.springframework.data.domain.PageRequest.of(0, 10));

                List<UUID> userIds = recentConvs.stream()
                                .map(SupportConversation::getUserId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList();
                Map<UUID, User> userMap = new HashMap<>();
                if (!userIds.isEmpty()) {
                        userRepository.findAllById(userIds).forEach(u -> userMap.put(u.getUserId(), u));
                }

                List<SupportConversationSummaryDto> recent = recentConvs.stream()
                                .map(c -> toSummaryDtoFast(c, userMap.get(c.getUserId())))
                                .toList();

                return StaffChatUnreadSummaryDto.builder()
                                .totalUnreadCount(totalUnread != null ? totalUnread : 0)
                                .unreadConversationsCount(unreadConvs)
                                .recentConversations(recent)
                                .build();
        }

        /**
         * CSKH khởi tạo hoặc lấy phiên trò chuyện với một Người dùng (EO/Worker) theo
         * userId.
         */
        @Transactional
        public SupportConversationDetailDto getOrCreateStaffConversationForUser(UUID targetUserId, UUID staffId) {
                User user = userRepository.findById(targetUserId)
                                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", targetUserId));

                SupportConversation conv = conversationRepository.findByUserIdAndDisputeIdIsNull(targetUserId)
                                .orElseGet(() -> conversationRepository.save(
                                                SupportConversation.builder()
                                                                .userId(targetUserId)
                                                                .status("ACTIVE")
                                                                .lastMessageAt(Instant.now())
                                                                .lastMessagePreview(
                                                                                "Phiên hỗ trợ trực tiếp được mở bởi CSKH")
                                                                .unreadStaffCount(0)
                                                                .unreadUserCount(0)
                                                                .build()));

                // Đánh dấu các tin nhắn chưa đọc từ User là đã đọc khi CSKH mở hội thoại
                List<SupportMessage> unreadFromUser = messageRepository
                                .findByConversationIdAndIsReadFalseAndSenderRole(conv.getId(), "USER");
                unreadFromUser.addAll(messageRepository
                                .findByConversationIdAndIsReadFalseAndSenderRole(conv.getId(), "WORKER"));
                unreadFromUser.addAll(messageRepository
                                .findByConversationIdAndIsReadFalseAndSenderRole(conv.getId(), "EVENT_ORGANIZER"));

                if (!unreadFromUser.isEmpty()) {
                        unreadFromUser.forEach(m -> m.setIsRead(true));
                        messageRepository.saveAll(unreadFromUser);
                        conv.setUnreadStaffCount(0);
                        conversationRepository.save(conv);
                }

                List<SupportMessage> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId());

                return SupportConversationDetailDto.builder()
                                .conversation(toSummaryDto(conv, user))
                                .messages(messages.stream().map(this::toMessageDto).toList())
                                .build();
        }

        /**
         * CSKH / Admin mở chi tiết một hội thoại để đọc tin nhắn và đánh dấu đã đọc.
         */
        @Transactional
        public SupportConversationDetailDto getStaffConversationDetail(UUID conversationId, UUID staffId) {
                SupportConversation conv = conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new ResourceNotFoundException("Phiên trò chuyện", "id",
                                                conversationId));

                User customer = userRepository.findById(conv.getUserId()).orElse(null);

                // Đánh dấu các tin nhắn chưa đọc từ User là đã đọc
                List<SupportMessage> unreadFromUser = messageRepository
                                .findByConversationIdAndIsReadFalseAndSenderRole(conv.getId(), "USER");
                unreadFromUser.addAll(messageRepository
                                .findByConversationIdAndIsReadFalseAndSenderRole(conv.getId(), "WORKER"));
                unreadFromUser.addAll(messageRepository
                                .findByConversationIdAndIsReadFalseAndSenderRole(conv.getId(), "EVENT_ORGANIZER"));

                if (!unreadFromUser.isEmpty()) {
                        unreadFromUser.forEach(m -> m.setIsRead(true));
                        messageRepository.saveAll(unreadFromUser);
                        conv.setUnreadStaffCount(0);
                        conversationRepository.save(conv);
                }

                List<SupportMessage> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId());

                return SupportConversationDetailDto.builder()
                                .conversation(toSummaryDto(conv, customer))
                                .messages(messages.stream().map(this::toMessageDto).toList())
                                .build();
        }

        /**
         * CSKH / Admin phản hồi tin nhắn cho Người dùng.
         */
        @Transactional
        public ChatMessageResponseDto sendStaffMessage(UUID staffId, UUID conversationId,
                        SendChatMessageRequestDto req) {
                SupportConversation conv = conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new ResourceNotFoundException("Phiên trò chuyện", "id",
                                                conversationId));

                User staff = userRepository.findById(staffId)
                                .orElseThrow(() -> new ResourceNotFoundException("Nhân viên CSKH", "id", staffId));

                String role = staff.getRole() != null ? staff.getRole().name() : "CUSTOMER_CARE";

                SupportMessage msg = SupportMessage.builder()
                                .conversationId(conv.getId())
                                .senderId(staffId)
                                .senderRole(role)
                                .senderName(staff.getFullName() != null ? staff.getFullName() : "CSKH CANi")
                                .messageText(req.getMessageText())
                                .attachmentUrl(req.getAttachmentUrl())
                                .isRead(false)
                                .build();

                msg = messageRepository.save(msg);

                // Cập nhật preview (cắt ngắn chống tràn cột 500 ký tự) và tăng unread của User
                conv.setLastMessagePreview(formatMessagePreview(req.getMessageText()));
                conv.setLastMessageAt(Instant.now());
                conv.setUnreadUserCount((conv.getUnreadUserCount() == null ? 0 : conv.getUnreadUserCount()) + 1);
                conversationRepository.save(conv);

                return toMessageDto(msg);
        }

        private SupportConversationSummaryDto toSummaryDtoFast(SupportConversation conv, User user) {
                return SupportConversationSummaryDto.builder()
                                .id(conv.getId())
                                .userId(conv.getUserId())
                                .userName(user != null && user.getFullName() != null ? user.getFullName() : "Khách hàng")
                                .userPhone(user != null && user.getPhone() != null ? user.getPhone() : "—")
                                .userEmail(user != null && user.getEmail() != null ? user.getEmail() : "—")
                                .userRole(user != null && user.getRole() != null ? user.getRole().name() : "USER")
                                .userAvatar(user != null ? user.getAvatarUrl() : null)
                                .lastMessagePreview(conv.getLastMessagePreview())
                                .lastMessageAt(conv.getLastMessageAt() != null ? conv.getLastMessageAt()
                                                : conv.getCreatedAt())
                                .unreadStaffCount(conv.getUnreadStaffCount() != null ? conv.getUnreadStaffCount() : 0)
                                .unreadUserCount(conv.getUnreadUserCount() != null ? conv.getUnreadUserCount() : 0)
                                .status(conv.getStatus())
                                .build();
        }

        private SupportConversationSummaryDto toSummaryDtoWithShift(SupportConversation conv, User user, UUID shiftId, Shift shift) {
                String shiftCode = null;
                String shiftName = null;
                if (shiftId != null) {
                        shiftCode = formatShiftCode(shiftId);
                        if (shift != null) {
                                String evName = shift.getEvent() != null ? shift.getEvent().getName() : "Sự kiện";
                                shiftName = evName + " - " + shift.getJobType();
                        }
                }
                return SupportConversationSummaryDto.builder()
                                .id(conv.getId())
                                .userId(conv.getUserId())
                                .userName(user != null && user.getFullName() != null ? user.getFullName() : "Khách hàng")
                                .userPhone(user != null && user.getPhone() != null ? user.getPhone() : "—")
                                .userEmail(user != null && user.getEmail() != null ? user.getEmail() : "—")
                                .userRole(user != null && user.getRole() != null ? user.getRole().name() : "USER")
                                .userAvatar(user != null ? user.getAvatarUrl() : null)
                                .lastMessagePreview(conv.getLastMessagePreview())
                                .lastMessageAt(conv.getLastMessageAt() != null ? conv.getLastMessageAt()
                                                : conv.getCreatedAt())
                                .unreadStaffCount(conv.getUnreadStaffCount() != null ? conv.getUnreadStaffCount() : 0)
                                .unreadUserCount(conv.getUnreadUserCount() != null ? conv.getUnreadUserCount() : 0)
                                .status(conv.getStatus())
                                .currentShiftId(shiftId)
                                .currentShiftCode(shiftCode)
                                .currentShiftName(shiftName)
                                .build();
        }

        private SupportConversationSummaryDto toSummaryDto(SupportConversation conv, User user) {
                UUID shiftId = null;
                String shiftCode = null;
                String shiftName = null;

                if (conv.getId() != null) {
                        Optional<SupportMessage> lastShiftMsg = messageRepository
                                        .findFirstByConversationIdAndShiftIdIsNotNullOrderByCreatedAtDesc(conv.getId());
                        if (lastShiftMsg.isPresent()) {
                                shiftId = lastShiftMsg.get().getShiftId();
                                shiftCode = formatShiftCode(shiftId);
                                Optional<Shift> sOpt = shiftRepository.findById(shiftId);
                                if (sOpt.isPresent()) {
                                        Shift s = sOpt.get();
                                        String evName = s.getEvent() != null ? s.getEvent().getName() : "Sự kiện";
                                        shiftName = evName + " - " + s.getJobType();
                                }
                        }
                }

                return SupportConversationSummaryDto.builder()
                                .id(conv.getId())
                                .userId(conv.getUserId())
                                .userName(user != null ? user.getFullName() : "Khách hàng")
                                .userPhone(user != null ? user.getPhone() : "—")
                                .userEmail(user != null ? user.getEmail() : "—")
                                .userRole(user != null && user.getRole() != null ? user.getRole().name() : "USER")
                                .userAvatar(user != null ? user.getAvatarUrl() : null)
                                .lastMessagePreview(conv.getLastMessagePreview())
                                .lastMessageAt(conv.getLastMessageAt() != null ? conv.getLastMessageAt()
                                                : conv.getCreatedAt())
                                .unreadStaffCount(conv.getUnreadStaffCount() != null ? conv.getUnreadStaffCount() : 0)
                                .unreadUserCount(conv.getUnreadUserCount() != null ? conv.getUnreadUserCount() : 0)
                                .status(conv.getStatus())
                                .currentShiftId(shiftId)
                                .currentShiftCode(shiftCode)
                                .currentShiftName(shiftName)
                                .build();
        }

        private ChatMessageResponseDto toMessageDto(SupportMessage msg) {
                String shiftCode = null;
                String shiftName = null;
                if (msg.getShiftId() != null) {
                        shiftCode = formatShiftCode(msg.getShiftId());
                        Optional<Shift> shiftOpt = shiftRepository.findById(msg.getShiftId());
                        if (shiftOpt.isPresent()) {
                                Shift s = shiftOpt.get();
                                String evName = s.getEvent() != null ? s.getEvent().getName() : "Sự kiện";
                                shiftName = evName + " - " + s.getJobType();
                        }
                }

                return ChatMessageResponseDto.builder()
                                .id(msg.getId())
                                .conversationId(msg.getConversationId())
                                .senderId(msg.getSenderId())
                                .senderRole(msg.getSenderRole())
                                .senderName(msg.getSenderName())
                                .messageText(msg.getMessageText())
                                .attachmentUrl(msg.getAttachmentUrl())
                                .shiftId(msg.getShiftId())
                                .shiftCode(shiftCode)
                                .shiftName(shiftName)
                                .isRead(msg.getIsRead())
                                .createdAt(msg.getCreatedAt())
                                .build();
        }

        private String formatShiftCode(UUID shiftId) {
                if (shiftId == null) return null;
                String hex = shiftId.toString().replace("-", "").toUpperCase();
                return "SH-" + hex.substring(0, Math.min(6, hex.length()));
        }

        private String formatMessagePreview(String text) {
                if (text == null) return "";
                String trimmed = text.trim();
                return trimmed.length() > 490 ? trimmed.substring(0, 487) + "..." : trimmed;
        }

        // ------------------------------------------------------------------
        // Quản lý upload & phục vụ file đính kèm trong hội thoại hỗ trợ
        // ------------------------------------------------------------------

        private static final String CHAT_UPLOAD_DIR = "uploads/chat";
        private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
                "jpg", "jpeg", "png", "webp", "gif", "pdf", "doc", "docx", "txt"
        );

        public String uploadChatAttachment(MultipartFile file) {
                if (file == null || file.isEmpty()) {
                        throw new BadRequestException("Tệp tải lên không được để trống");
                }

                String originalFilename = file.getOriginalFilename();
                String ext = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                        ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
                }

                if (!ALLOWED_EXTENSIONS.contains(ext)) {
                        throw new BadRequestException("Chỉ chấp nhận các định dạng tệp: " + String.join(", ", ALLOWED_EXTENSIONS));
                }

                if (file.getSize() > 10 * 1024 * 1024) {
                        throw new BadRequestException("Dung lượng tệp không được vượt quá 10MB");
                }

                try {
                        Path uploadPath = Paths.get(CHAT_UPLOAD_DIR);
                        if (!Files.exists(uploadPath)) {
                                Files.createDirectories(uploadPath);
                        }

                        String savedFilename = "chat_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
                        Path targetPath = uploadPath.resolve(savedFilename);
                        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                        return "/api/v1/support/chat/attachments/" + savedFilename;
                } catch (IOException e) {
                        log.error("Lỗi khi lưu tệp đính kèm chat: {}", e.getMessage(), e);
                        throw new BadRequestException("Không thể lưu tệp đính kèm: " + e.getMessage());
                }
        }

        public Resource loadChatAttachment(String filename) {
                try {
                        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                                throw new BadRequestException("Tên tệp không hợp lệ");
                        }

                        Path filePath = Paths.get(CHAT_UPLOAD_DIR).resolve(filename).normalize();
                        Resource resource = new UrlResource(filePath.toUri());

                        if (resource.exists() && resource.isReadable()) {
                                return resource;
                        } else {
                                throw new ResourceNotFoundException("Không tìm thấy tệp đính kèm", "filename", filename);
                        }
                } catch (MalformedURLException e) {
                        throw new ResourceNotFoundException("Đường dẫn tệp không hợp lệ", "filename", filename);
                }
        }
}
