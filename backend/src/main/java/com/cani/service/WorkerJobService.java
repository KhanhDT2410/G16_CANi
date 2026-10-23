package com.cani.service;

import com.cani.dto.worker.AttendanceCheckRequestDto;
import com.cani.dto.worker.AttendanceCheckResponseDto;
import com.cani.dto.worker.JobProposalResponseDto;
import com.cani.dto.worker.NearbyShiftResponseDto;
import com.cani.dto.worker.ShiftAssignmentResponseDto;
import com.cani.dto.worker.WorkerOnlineStatusRequestDto;
import com.cani.entity.*;
import com.cani.entity.enums.AssignmentSource;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.AttendanceStatus;
import com.cani.entity.enums.ProposalStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerJobService {

    private final WorkerRepository workerRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final DispatchProposalRepository dispatchProposalRepository;
    private final EventRepository eventRepository;
    private final MatchingDispatchService matchingDispatchService;
    private final DirectPaymentService directPaymentService;
    private final AttendanceLogRepository attendanceLogRepository;

    /**
     * Bật / tắt trạng thái "Bắt đầu kiếm việc" (Online status)
     */
    @Transactional
    public Worker setOnlineStatus(UUID userId, WorkerOnlineStatusRequestDto dto) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Worker cho User ID: " + userId));

        worker.setIsOnline(dto.getIsOnline());
        if (dto.getCurrentLat() != null && dto.getCurrentLng() != null) {
            worker.setBaseLat(dto.getCurrentLat());
            worker.setBaseLng(dto.getCurrentLng());
        }
        worker = workerRepository.save(worker);
        log.info("Worker {} đã cập nhật trạng thái online: {}", userId, worker.getIsOnline());
        return worker;
    }

    /**
     * Lấy danh sách các đề xuất việc làm đang chờ Worker phản hồi (Status = PENDING)
     */
    @Transactional(readOnly = true)
    public List<JobProposalResponseDto> getMyProposals(UUID userId) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Worker cho User ID: " + userId));

        List<DispatchProposal> proposals = dispatchProposalRepository.findByWorker_WorkerIdAndStatusOrderByCreatedAtDesc(
                worker.getWorkerId(), ProposalStatus.PENDING);

        Instant now = Instant.now();
        return proposals.stream()
                .filter(p -> p.getExpiresAt() == null || p.getExpiresAt().isAfter(now))
                .map(this::mapToProposalDto)
                .collect(Collectors.toList());
    }

    /**
     * Worker chấp nhận lời mời làm việc (Accept Proposal)
     * Áp dụng Atomic Concurrency Locking để chống overbooking
     */
    @Transactional
    public ShiftAssignmentResponseDto acceptProposal(UUID userId, UUID proposalId) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Worker cho User ID: " + userId));

        DispatchProposal proposal = dispatchProposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề xuất việc làm ID: " + proposalId));

        if (!proposal.getWorker().getWorkerId().equals(worker.getWorkerId())) {
            throw new BadRequestException("Bạn không có quyền thao tác trên đề xuất việc làm này!");
        }

        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new BadRequestException("Đề xuất việc làm này đã được xử lý hoặc không còn khả dụng (Trạng thái: " + proposal.getStatus() + ")");
        }

        if (proposal.getExpiresAt() != null && proposal.getExpiresAt().isBefore(Instant.now())) {
            proposal.setStatus(ProposalStatus.EXPIRED);
            dispatchProposalRepository.save(proposal);
            throw new BadRequestException("Rất tiếc! Đề xuất việc làm này đã hết hạn phản hồi.");
        }

        Shift shift = proposal.getShift();

        // 1. Kiểm tra lại trùng ca làm việc (phòng trường hợp vừa nhận ca khác)
        List<AssignmentStatus> activeStatuses = List.of(AssignmentStatus.CONFIRMED, AssignmentStatus.CHECKED_IN, AssignmentStatus.PENDING);
        List<ShiftAssignment> conflicts = shiftAssignmentRepository.findConflictingAssignments(
                worker.getWorkerId(), shift.getStartTime(), shift.getEndTime(), activeStatuses);
        if (!conflicts.isEmpty()) {
            throw new BadRequestException("Bạn đã có một ca làm việc khác trùng khung giờ với ca này!");
        }

        // 2. ATOMIC CONCURRENCY CHECK: Tăng confirmed_workers một cách nguyên tử
        int updatedRows = shiftRepository.incrementConfirmedWorkersAtomic(shift.getShiftId());
        if (updatedRows == 0) {
            // Đã hết suất do người khác nhận trước
            proposal.setStatus(ProposalStatus.REJECTED);
            dispatchProposalRepository.save(proposal);
            throw new BadRequestException("Rất tiếc! Ca làm việc này vừa được người khác nhận đủ trước bạn ít giây.");
        }

        // 3. Cập nhật Proposal -> ACCEPTED
        proposal.setStatus(ProposalStatus.ACCEPTED);
        proposal.setRespondedAt(Instant.now());
        dispatchProposalRepository.save(proposal);

        // 4. Tạo ShiftAssignment -> CONFIRMED
        ShiftAssignment assignment = ShiftAssignment.builder()
                .shift(shift)
                .worker(worker)
                .proposalId(proposal.getProposalId())
                .source(AssignmentSource.MATCHING)
                .status(AssignmentStatus.CONFIRMED)
                .eoConfirmed(true)
                .eoConfirmedAt(Instant.now())
                .build();
        assignment = shiftAssignmentRepository.save(assignment);

        // Tạo bản ghi theo dõi thanh toán trực tiếp (Non-Escrow)
        directPaymentService.createInitialPayment(assignment);

        // 5. Cập nhật Event confirmed workers
        Event event = shift.getEvent();
        if (event != null) {
            event.setTotalConfirmedWorkers((event.getTotalConfirmedWorkers() != null ? event.getTotalConfirmedWorkers() : 0) + 1);
            eventRepository.save(event);
        }

        log.info("Worker {} đã nhận thành công ca làm việc {} (Assignment ID: {})",
                userId, shift.getShiftId(), assignment.getAssignmentId());

        return mapToAssignmentDto(assignment);
    }

    /**
     * Worker từ chối lời mời làm việc (Reject Proposal)
     */
    @Transactional
    public void rejectProposal(UUID userId, UUID proposalId, String reason) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Worker cho User ID: " + userId));

        DispatchProposal proposal = dispatchProposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề xuất việc làm ID: " + proposalId));

        if (!proposal.getWorker().getWorkerId().equals(worker.getWorkerId())) {
            throw new BadRequestException("Bạn không có quyền thao tác trên đề xuất việc làm này!");
        }

        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new BadRequestException("Đề xuất việc làm này đã ở trạng thái: " + proposal.getStatus());
        }

        proposal.setStatus(ProposalStatus.REJECTED);
        proposal.setRespondedAt(Instant.now());
        dispatchProposalRepository.save(proposal);

        log.info("Worker {} đã từ chối đề xuất {}. Lý do: {}", userId, proposalId, reason);

        // Tự động kích hoạt Matching Engine tìm ứng viên thay thế cho ca này
        try {
            matchingDispatchService.dispatchForShift(proposal.getShift().getShiftId());
        } catch (Exception e) {
            log.warn("Lỗi khi dispatch lại sau khi reject: {}", e.getMessage());
        }
    }

    /**
     * Lịch làm việc của Worker (Các ca đã nhận)
     */
    @Transactional(readOnly = true)
    public List<ShiftAssignmentResponseDto> getMyAssignments(UUID userId) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Worker cho User ID: " + userId));

        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId());
        return assignments.stream().map(this::mapToAssignmentDto).collect(Collectors.toList());
    }

    private JobProposalResponseDto mapToProposalDto(DispatchProposal p) {
        Shift s = p.getShift();
        Event e = s != null ? s.getEvent() : null;

        BigDecimal estimatedPay = BigDecimal.ZERO;
        if (s != null && s.getHourlyWage() != null) {
            long minutes = Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
            double hours = Math.max(1.0, minutes / 60.0);
            estimatedPay = s.getHourlyWage().multiply(BigDecimal.valueOf(hours)).add(s.getAllowance() != null ? s.getAllowance() : BigDecimal.ZERO);
        }

        return JobProposalResponseDto.builder()
                .proposalId(p.getProposalId())
                .shiftId(s != null ? s.getShiftId() : null)
                .eventId(e != null ? e.getEventId() : null)
                .eventName(e != null ? e.getName() : "Sự kiện")
                .venueAddress(s != null && s.getLocationAddress() != null ? s.getLocationAddress() : (e != null ? e.getVenueAddress() : ""))
                .jobType(s != null ? s.getJobType() : "")
                .description(s != null ? s.getDescription() : "")
                .startTime(s != null ? s.getStartTime() : null)
                .endTime(s != null ? s.getEndTime() : null)
                .hourlyWage(s != null ? s.getHourlyWage() : BigDecimal.ZERO)
                .allowance(s != null ? s.getAllowance() : BigDecimal.ZERO)
                .totalEstimatedPay(estimatedPay)
                .matchScore(p.getMatchScore())
                .scoreBreakdown(p.getScoreBreakdown())
                .status(p.getStatus())
                .expiresAt(p.getExpiresAt())
                .source(p.getSource())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private ShiftAssignmentResponseDto mapToAssignmentDto(ShiftAssignment a) {
        Shift s = a.getShift();
        Event e = s != null ? s.getEvent() : null;

        BigDecimal totalPay = BigDecimal.ZERO;
        if (s != null && s.getHourlyWage() != null && s.getStartTime() != null && s.getEndTime() != null) {
            long minutes = Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
            double hours = Math.max(1.0, minutes / 60.0);
            totalPay = s.getHourlyWage().multiply(BigDecimal.valueOf(hours)).add(s.getAllowance() != null ? s.getAllowance() : BigDecimal.ZERO);
        }

        Double venueLat = s != null && s.getLocationLat() != null ? s.getLocationLat() : (e != null && e.getVenueLat() != null ? e.getVenueLat() : 10.7769);
        Double venueLng = s != null && s.getLocationLng() != null ? s.getLocationLng() : (e != null && e.getVenueLng() != null ? e.getVenueLng() : 106.7009);

        // Tra cứu lịch sử điểm danh nếu có
        Optional<AttendanceLog> attendanceOpt = attendanceLogRepository.findByShiftAssignment_AssignmentId(a.getAssignmentId());
        String attendanceStatus = attendanceOpt.map(att -> att.getAttendanceStatus().name()).orElse("NOT_CHECKED_IN");
        Instant checkinTime = attendanceOpt.map(AttendanceLog::getCheckinTime).orElse(null);
        Instant checkoutTime = attendanceOpt.map(AttendanceLog::getCheckoutTime).orElse(null);
        BigDecimal hoursWorked = attendanceOpt.map(AttendanceLog::getHoursWorked).orElse(BigDecimal.ZERO);

        return ShiftAssignmentResponseDto.builder()
                .assignmentId(a.getAssignmentId())
                .shiftId(s != null ? s.getShiftId() : null)
                .eventId(e != null ? e.getEventId() : null)
                .eventName(e != null ? e.getName() : "Sự kiện")
                .venueAddress(s != null && s.getLocationAddress() != null ? s.getLocationAddress() : (e != null ? e.getVenueAddress() : ""))
                .jobType(s != null ? s.getJobType() : "")
                .startTime(s != null ? s.getStartTime() : null)
                .endTime(s != null ? s.getEndTime() : null)
                .hourlyWage(s != null ? s.getHourlyWage() : BigDecimal.ZERO)
                .totalPay(totalPay)
                .status(a.getStatus())
                .source(a.getSource())
                .eoConfirmed(a.getEoConfirmed())
                .venueLat(venueLat)
                .venueLng(venueLng)
                .attendanceStatus(attendanceStatus)
                .checkinTime(checkinTime)
                .checkoutTime(checkoutTime)
                .hoursWorked(hoursWorked)
                .createdAt(a.getCreatedAt())
                .build();
    }

    /**
     * Quét QR Check-in vào ca làm việc
     */
    @Transactional
    public AttendanceCheckResponseDto checkinAssignment(UUID userId, UUID assignmentId, AttendanceCheckRequestDto request) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Worker cho User: " + userId));

        ShiftAssignment assignment = shiftAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc đã nhận: " + assignmentId));

        if (!assignment.getWorker().getWorkerId().equals(worker.getWorkerId())) {
            throw new BadRequestException("Bạn không có quyền điểm danh cho ca làm việc này.");
        }

        Shift shift = assignment.getShift();
        Instant now = Instant.now();

        AttendanceLog attendanceLog = attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)
                .orElseGet(() -> AttendanceLog.builder()
                        .shiftAssignment(assignment)
                        .worker(worker)
                        .build());

        if (attendanceLog.getCheckinTime() != null) {
            throw new BadRequestException("Bạn đã thực hiện check-in vào ca làm việc này rồi.");
        }

        boolean isOnTime = shift == null || shift.getStartTime() == null ||
                now.isBefore(shift.getStartTime().plus(Duration.ofMinutes(15)));

        AttendanceStatus status = isOnTime ? AttendanceStatus.ON_TIME : AttendanceStatus.LATE;

        attendanceLog.setCheckinTime(now);
        attendanceLog.setCheckinLat(request.getLat());
        attendanceLog.setCheckinLng(request.getLng());
        attendanceLog.setCheckinQrHash(request.getQrContent() != null ? hashString(request.getQrContent()) : "MANUAL_CHECKIN");
        attendanceLog.setIsOnTime(isOnTime);
        attendanceLog.setAttendanceStatus(status);

        attendanceLog = attendanceLogRepository.save(attendanceLog);

        assignment.setStatus(AssignmentStatus.CHECKED_IN);
        shiftAssignmentRepository.save(assignment);

        String msg = isOnTime
                ? "Điểm danh Check-in thành công! Bạn đã có mặt đúng giờ."
                : "Điểm danh Check-in thành công (Ghi nhận có mặt muộn).";

        return AttendanceCheckResponseDto.builder()
                .assignmentId(assignmentId)
                .attendanceId(attendanceLog.getAttendanceId())
                .attendanceStatus(status)
                .assignmentStatus(assignment.getStatus().name())
                .checkinTime(now)
                .isOnTime(isOnTime)
                .message(msg)
                .build();
    }

    /**
     * Quét QR Check-out hoàn thành ca làm việc
     */
    @Transactional
    public AttendanceCheckResponseDto checkoutAssignment(UUID userId, UUID assignmentId, AttendanceCheckRequestDto request) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Worker cho User: " + userId));

        ShiftAssignment assignment = shiftAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc đã nhận: " + assignmentId));

        if (!assignment.getWorker().getWorkerId().equals(worker.getWorkerId())) {
            throw new BadRequestException("Bạn không có quyền điểm danh cho ca làm việc này.");
        }

        AttendanceLog attendanceLog = attendanceLogRepository.findByShiftAssignment_AssignmentId(assignmentId)
                .orElseThrow(() -> new BadRequestException("Bạn chưa check-in vào ca làm việc này nên không thể check-out."));

        if (attendanceLog.getCheckoutTime() != null) {
            throw new BadRequestException("Bạn đã check-out hoàn thành ca làm việc này rồi.");
        }

        Instant now = Instant.now();
        Instant checkin = attendanceLog.getCheckinTime() != null ? attendanceLog.getCheckinTime() : now.minus(Duration.ofHours(4));
        long minutes = Math.max(30, Duration.between(checkin, now).toMinutes());
        BigDecimal hoursWorked = BigDecimal.valueOf(minutes / 60.0);

        attendanceLog.setCheckoutTime(now);
        attendanceLog.setCheckoutQrHash(request.getQrContent() != null ? hashString(request.getQrContent()) : "MANUAL_CHECKOUT");
        attendanceLog.setHoursWorked(hoursWorked);
        attendanceLog.setAttendanceStatus(AttendanceStatus.CHECKED_OUT);

        attendanceLogRepository.save(attendanceLog);

        assignment.setStatus(AssignmentStatus.COMPLETED);
        shiftAssignmentRepository.save(assignment);

        worker.setTotalShiftsCompleted((worker.getTotalShiftsCompleted() != null ? worker.getTotalShiftsCompleted() : 0) + 1);
        workerRepository.save(worker);

        return AttendanceCheckResponseDto.builder()
                .assignmentId(assignmentId)
                .attendanceId(attendanceLog.getAttendanceId())
                .attendanceStatus(AttendanceStatus.CHECKED_OUT)
                .assignmentStatus(assignment.getStatus().name())
                .checkinTime(attendanceLog.getCheckinTime())
                .checkoutTime(now)
                .hoursWorked(hoursWorked)
                .isOnTime(attendanceLog.getIsOnTime())
                .message("Điểm danh Check-out thành công! Cảm ơn bạn đã hoàn thành ca làm việc.")
                .build();
    }

    private String hashString(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(raw.hashCode());
        }
    }

    /**
     * Quét và tìm kiếm các ca làm việc xung quanh vị trí của Worker trên bản đồ
     */
    @Transactional(readOnly = true)
    public List<NearbyShiftResponseDto> getNearbyShifts(Double userLat, Double userLng, Double radiusKm) {
        double searchRadius = (radiusKm != null && radiusKm > 0) ? radiusKm : 15.0;
        double centerLat = (userLat != null) ? userLat : 10.7769; // Mặc định trung tâm TP.HCM
        double centerLng = (userLng != null) ? userLng : 106.7009;

        List<Shift> activeShifts = shiftRepository.findByStatus(ShiftStatus.ACTIVE);
        List<NearbyShiftResponseDto> nearbyList = new java.util.ArrayList<>();

        for (Shift s : activeShifts) {
            Double sLat = s.getLocationLat() != null ? s.getLocationLat() : (s.getEvent() != null ? s.getEvent().getVenueLat() : null);
            Double sLng = s.getLocationLng() != null ? s.getLocationLng() : (s.getEvent() != null ? s.getEvent().getVenueLng() : null);

            double distance = MatchingDispatchService.calculateDistance(centerLat, centerLng, sLat, sLng);
            if (distance <= searchRadius) {
                Event e = s.getEvent();
                nearbyList.add(NearbyShiftResponseDto.builder()
                        .shiftId(s.getShiftId())
                        .eventId(e != null ? e.getEventId() : null)
                        .eventName(e != null ? e.getName() : "Sự kiện")
                        .jobType(s.getJobType())
                        .description(s.getDescription())
                        .locationAddress(s.getLocationAddress() != null ? s.getLocationAddress() : (e != null ? e.getVenueAddress() : ""))
                        .locationLat(sLat != null ? sLat : centerLat)
                        .locationLng(sLng != null ? sLng : centerLng)
                        .hourlyWage(s.getHourlyWage())
                        .allowance(s.getAllowance())
                        .startTime(s.getStartTime())
                        .endTime(s.getEndTime())
                        .distanceKm(Math.round(distance * 10.0) / 10.0)
                        .requiredWorkers(s.getRequiredWorkers())
                        .confirmedWorkers(s.getConfirmedWorkers())
                        .remainingWorkers(s.getRemainingWorkers())
                        .build());
            }
        }

        nearbyList.sort(java.util.Comparator.comparingDouble(NearbyShiftResponseDto::getDistanceKm));
        return nearbyList;
    }
}
