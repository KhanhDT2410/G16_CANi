package com.cani.service;

import com.cani.dto.invite.CreateInviteRequestDto;
import com.cani.dto.invite.InviteResponseDto;
import com.cani.dto.invite.JoinInviteResponseDto;
import com.cani.entity.*;
import com.cani.entity.enums.AssignmentSource;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.InviteTargetType;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InviteService {

    private final InviteRepository inviteRepository;
    private final ShiftRepository shiftRepository;
    private final EventRepository eventRepository;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final WorkerRepository workerRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * UC-72: EO sinh Link & Mã mời trực tiếp cho Ca làm việc (Invite Mode)
     */
    @Transactional
    public InviteResponseDto generateShiftInvite(UUID eoUserId, UUID shiftId, CreateInviteRequestDto request) {
        EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(eoUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ Ban tổ chức sự kiện"));

        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc ID: " + shiftId));

        if (!shift.getEvent().getEventOrganizer().getEoId().equals(eo.getEoId())) {
            throw new AppException("Bạn không có quyền quản lý ca làm việc này", HttpStatus.FORBIDDEN);
        }

        // Kiểm tra xem đã có mã mời đang kích hoạt chưa
        Optional<Invite> existingInvite = inviteRepository.findFirstByShift_ShiftIdAndIsActiveTrueOrderByCreatedAtDesc(shiftId);
        if (existingInvite.isPresent()) {
            Invite inv = existingInvite.get();
            if (inv.getExpiresAt() == null || inv.getExpiresAt().isAfter(Instant.now())) {
                return mapToDto(inv);
            } else {
                inv.setIsActive(false);
                inviteRepository.save(inv);
            }
        }

        int maxUses = request != null && request.getMaxUses() != null
                ? request.getMaxUses()
                : Math.max(1, (shift.getRequiredWorkers() != null ? shift.getRequiredWorkers() : 1) - (shift.getConfirmedWorkers() != null ? shift.getConfirmedWorkers() : 0));

        int expiryHours = request != null && request.getExpiryHours() != null
                ? request.getExpiryHours()
                : 48;

        String code = generateUniqueCode();

        Invite invite = Invite.builder()
                .event(shift.getEvent())
                .shift(shift)
                .targetType(InviteTargetType.SHIFT)
                .code(code)
                .maxUses(maxUses)
                .usedCount(0)
                .expiresAt(Instant.now().plus(expiryHours, ChronoUnit.HOURS))
                .isActive(true)
                .build();

        Invite saved = inviteRepository.save(invite);
        log.info("EO {} đã sinh mã mời trực tiếp {} cho ca {}", eo.getEoId(), code, shiftId);
        return mapToDto(saved);
    }

    /**
     * Lấy mã mời đang hoạt động của ca
     */
    @Transactional(readOnly = true)
    public InviteResponseDto getShiftInvite(UUID eoUserId, UUID shiftId) {
        EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(eoUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ Ban tổ chức sự kiện"));

        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc ID: " + shiftId));

        if (!shift.getEvent().getEventOrganizer().getEoId().equals(eo.getEoId())) {
            throw new AppException("Bạn không có quyền quản lý ca làm việc này", HttpStatus.FORBIDDEN);
        }

        Invite invite = inviteRepository.findFirstByShift_ShiftIdAndIsActiveTrueOrderByCreatedAtDesc(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Ca làm việc này chưa có mã mời trực tiếp nào"));

        return mapToDto(invite);
    }

    /**
     * Tra cứu thông tin chi tiết qua Mã mời (Công khai cho Worker xem trước khi bấm nhận)
     */
    @Transactional(readOnly = true)
    public InviteResponseDto getInviteDetails(String code) {
        String cleanCode = code != null ? code.trim().toUpperCase() : "";
        Invite invite = inviteRepository.findByCodeAndIsActiveTrue(cleanCode)
                .orElseThrow(() -> new ResourceNotFoundException("Mã mời không tồn tại hoặc đã hết hiệu lực"));

        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Mã mời này đã hết hạn sử dụng");
        }

        if (invite.getUsedCount() >= invite.getMaxUses()) {
            throw new BadRequestException("Mã mời này đã đủ số lượng người đăng ký");
        }

        return mapToDto(invite);
    }

    /**
     * UC-73: Worker tham gia ca làm việc qua Mã mời hoặc Quét QR mời (Invite Mode)
     */
    @Transactional
    public JoinInviteResponseDto joinViaInviteCode(UUID workerUserId, String code) {
        Worker worker = workerRepository.findByUser_UserId(workerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản chưa đăng ký hồ sơ Người lao động (Worker)"));

        String cleanCode = code != null ? code.trim().toUpperCase() : "";
        Invite invite = inviteRepository.findByCodeAndIsActiveTrue(cleanCode)
                .orElseThrow(() -> new BadRequestException("Mã mời không hợp lệ hoặc đã bị vô hiệu hóa"));

        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Mã mời này đã hết hạn hiệu lực");
        }

        if (invite.getUsedCount() >= invite.getMaxUses()) {
            throw new BadRequestException("Ca làm việc đã đủ số lượng nhân sự đăng ký qua mã mời này");
        }

        Shift shift = invite.getShift();
        if (shift == null) {
            throw new BadRequestException("Mã mời không gắn với ca làm việc cụ thể nào");
        }

        // Kiểm tra xem worker đã tham gia ca này chưa
        Optional<ShiftAssignment> existing = shiftAssignmentRepository.findByShift_ShiftIdAndWorker_WorkerId(
                shift.getShiftId(), worker.getWorkerId()
        );
        if (existing.isPresent()) {
            ShiftAssignment sa = existing.get();
            if (sa.getStatus() != AssignmentStatus.CANCELLED) {
                throw new BadRequestException("Bạn đã đăng ký tham gia ca làm việc này rồi (Trạng thái: " + sa.getStatus() + ")");
            }
        }

        // Tính thù lao dự kiến
        long hours = 4;
        if (shift.getStartTime() != null && shift.getEndTime() != null) {
            hours = Math.max(1, Duration.between(shift.getStartTime(), shift.getEndTime()).toHours());
        }
        BigDecimal hourlyWage = shift.getHourlyWage() != null ? shift.getHourlyWage() : BigDecimal.valueOf(50000);
        BigDecimal agreedAmount = hourlyWage.multiply(BigDecimal.valueOf(hours));

        // Tạo ShiftAssignment với source = DIRECT
        ShiftAssignment assignment = ShiftAssignment.builder()
                .shift(shift)
                .worker(worker)
                .inviteId(invite.getInviteId())
                .source(AssignmentSource.DIRECT)
                .status(AssignmentStatus.CONFIRMED)
                .build();

        ShiftAssignment savedAssignment = shiftAssignmentRepository.save(assignment);

        // Cập nhật lượt sử dụng mã mời
        invite.setUsedCount(invite.getUsedCount() + 1);
        if (invite.getUsedCount() >= invite.getMaxUses()) {
            invite.setIsActive(false);
        }
        inviteRepository.save(invite);

        // Cập nhật số lượng nhân sự đã xác nhận trong ca
        int currentConfirmed = shift.getConfirmedWorkers() != null ? shift.getConfirmedWorkers() : 0;
        shift.setConfirmedWorkers(currentConfirmed + 1);
        shiftRepository.save(shift);

        log.info("Worker {} đã tham gia thành công ca {} qua mã mời {}", worker.getWorkerId(), shift.getShiftId(), cleanCode);

        return JoinInviteResponseDto.builder()
                .assignmentId(savedAssignment.getAssignmentId())
                .shiftId(shift.getShiftId())
                .jobType(shift.getJobType())
                .eventName(shift.getEvent().getName())
                .venueAddress(shift.getEvent().getVenueAddress())
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .agreedAmount(agreedAmount)
                .status(AssignmentStatus.CONFIRMED.name())
                .message("Đăng ký nhận ca thành công qua mã mời! Vui lòng có mặt đúng giờ.")
                .build();
    }

    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        do {
            StringBuilder sb = new StringBuilder("CANI-");
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
            attempts++;
            if (attempts > 20) {
                code = "CANI-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                break;
            }
        } while (inviteRepository.findByCode(code).isPresent());
        return code;
    }

    private InviteResponseDto mapToDto(Invite inv) {
        Shift shift = inv.getShift();
        Event event = inv.getEvent();

        String shareLink = "http://localhost:5173/join?code=" + inv.getCode();
        String qrContent = "CANI_INVITE:" + inv.getCode();

        int max = inv.getMaxUses() != null ? inv.getMaxUses() : 1;
        int used = inv.getUsedCount() != null ? inv.getUsedCount() : 0;
        int remaining = Math.max(0, max - used);

        return InviteResponseDto.builder()
                .inviteId(inv.getInviteId())
                .code(inv.getCode())
                .shareLink(shareLink)
                .qrContent(qrContent)
                .eventId(event != null ? event.getEventId() : null)
                .eventName(event != null ? event.getName() : null)
                .shiftId(shift != null ? shift.getShiftId() : null)
                .jobType(shift != null ? shift.getJobType() : null)
                .venueAddress(event != null ? event.getVenueAddress() : null)
                .hourlyWage(shift != null ? shift.getHourlyWage() : null)
                .startTime(shift != null ? shift.getStartTime() : null)
                .endTime(shift != null ? shift.getEndTime() : null)
                .maxUses(max)
                .usedCount(used)
                .remainingSlots(remaining)
                .expiresAt(inv.getExpiresAt())
                .isActive(inv.getIsActive())
                .createdAt(inv.getCreatedAt())
                .build();
    }
}

