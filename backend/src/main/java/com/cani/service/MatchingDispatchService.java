package com.cani.service;

import com.cani.entity.*;
import com.cani.entity.enums.AssignmentStatus;
import com.cani.entity.enums.ProposalStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.DispatchProposalRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
import com.cani.repository.WorkerRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingDispatchService {

    private final WorkerRepository workerRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final DispatchProposalRepository dispatchProposalRepository;

    private static final double MAX_DISTANCE_KM = 15.0;
    private static final int MIN_RELIABILITY_SCORE = 60;
    private static final int DEFAULT_TIMEOUT_HOURS = 1;
    private static final double EARTH_RADIUS_KM = 6371.0;

    @Data
    @Builder
    public static class CandidateMatch {
        private Worker worker;
        private double distanceKm;
        private double finalScore;
        private String scoreBreakdownJson;
    }

    /**
     * Kích hoạt matching và dispatch cho tất cả ca làm việc đang ACTIVE của một Event
     */
    @Transactional
    public int dispatchForEvent(UUID eventId) {
        List<Shift> activeShifts = shiftRepository.findByEvent_EventIdAndStatus(eventId, ShiftStatus.ACTIVE);
        int totalProposalsCreated = 0;
        for (Shift shift : activeShifts) {
            totalProposalsCreated += dispatchForShift(shift.getShiftId());
        }
        return totalProposalsCreated;
    }

    /**
     * Thuật toán Matching & Dispatch cho một ca làm việc cụ thể
     */
    @Transactional
    public int dispatchForShift(UUID shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ca làm việc ID: " + shiftId));

        if (shift.getStatus() != ShiftStatus.ACTIVE) {
            log.info("Bỏ qua dispatch: Ca làm việc {} không ở trạng thái ACTIVE (hiện tại: {})", shiftId, shift.getStatus());
            return 0;
        }

        int needed = shift.getRemainingWorkers() != null ? shift.getRemainingWorkers() : shift.getRequiredWorkers();
        if (needed <= 0) {
            log.info("Ca làm việc {} đã đủ nhân sự.", shiftId);
            return 0;
        }

        // Tọa độ mục tiêu của ca làm việc
        Double targetLat = shift.getLocationLat() != null ? shift.getLocationLat() : (shift.getEvent() != null ? shift.getEvent().getVenueLat() : null);
        Double targetLng = shift.getLocationLng() != null ? shift.getLocationLng() : (shift.getEvent() != null ? shift.getEvent().getVenueLng() : null);

        // Lấy danh sách tất cả Worker
        List<Worker> allWorkers = workerRepository.findAll();

        List<CandidateMatch> matchedCandidates = new ArrayList<>();

        for (Worker worker : allWorkers) {
            // STEP 1: HARD FILTER
            if (!passHardFilter(worker, shift)) {
                continue;
            }

            // Tính khoảng cách Haversine
            double distanceKm = calculateDistance(targetLat, targetLng, worker.getBaseLat(), worker.getBaseLng());
            if (distanceKm > MAX_DISTANCE_KM) {
                continue;
            }

            // STEP 2: SOFT RANKING SCORE
            CandidateMatch match = calculateSoftScore(worker, distanceKm);
            matchedCandidates.add(match);
        }

        // Sắp xếp ứng viên theo điểm số giảm dần
        matchedCandidates.sort(Comparator.comparingDouble(CandidateMatch::getFinalScore).reversed());

        // Dispatch ratio: tuyển N người -> dispatch cho min(N * 2 + 3, số ứng viên phù hợp)
        int targetDispatchCount = Math.max(needed * 2 + 3, needed);
        List<CandidateMatch> topCandidates = matchedCandidates.stream()
                .limit(targetDispatchCount)
                .collect(Collectors.toList());

        int createdCount = 0;
        Instant expiresAt = Instant.now().plus(Duration.ofHours(DEFAULT_TIMEOUT_HOURS));

        for (CandidateMatch candidate : topCandidates) {
            Worker worker = candidate.getWorker();
            // Nếu đã có proposal hoặc assignment thì không tạo trùng
            if (dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerId(shiftId, worker.getWorkerId()) ||
                shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(shiftId, worker.getWorkerId())) {
                continue;
            }

            DispatchProposal proposal = DispatchProposal.builder()
                    .shift(shift)
                    .worker(worker)
                    .matchScore(BigDecimal.valueOf(candidate.getFinalScore()).setScale(2, RoundingMode.HALF_UP))
                    .scoreBreakdown(candidate.getScoreBreakdownJson())
                    .status(ProposalStatus.PENDING)
                    .expiresAt(expiresAt)
                    .build();

            dispatchProposalRepository.save(proposal);
            createdCount++;
        }

        log.info("Matching hoàn tất cho ca {}: Tìm thấy {} ứng viên, đã gửi {} đề xuất (Proposals).",
                shiftId, matchedCandidates.size(), createdCount);
        return createdCount;
    }

    /**
     * Hard Filter: Loại bỏ các ứng viên không đạt tiêu chuẩn tối thiểu
     */
    private boolean passHardFilter(Worker worker, Shift shift) {
        // 1. Phải bật trạng thái "Bắt đầu kiếm việc" (isOnline = true)
        if (Boolean.FALSE.equals(worker.getIsOnline())) {
            return false;
        }

        // 2. Tài khoản User phải ACTIVE
        if (worker.getUser() == null || worker.getUser().getStatus() != UserStatus.ACTIVE) {
            return false;
        }

        // 3. Điểm tin cậy (Reliability Score) >= 60 (hoặc theo yêu cầu của shift)
        int minRs = shift.getMinReliabilityScore() != null && shift.getMinReliabilityScore() > 0 
                ? shift.getMinReliabilityScore() : MIN_RELIABILITY_SCORE;
        if (worker.getReliabilityScore() == null || worker.getReliabilityScore() < minRs) {
            return false;
        }

        // 4. Kiểm tra xung đột lịch làm việc (Không được trùng ca đã xác nhận/đang chờ)
        List<AssignmentStatus> activeStatuses = List.of(
                AssignmentStatus.CONFIRMED,
                AssignmentStatus.CHECKED_IN,
                AssignmentStatus.PENDING
        );
        List<ShiftAssignment> conflicts = shiftAssignmentRepository.findConflictingAssignments(
                worker.getWorkerId(),
                shift.getStartTime(),
                shift.getEndTime(),
                activeStatuses
        );
        if (!conflicts.isEmpty()) {
            return false;
        }

        return true;
    }

    /**
     * Soft Ranking Score: Tính điểm xếp hạng ứng viên dựa trên 4 thành phần + Điểm thưởng
     */
    private CandidateMatch calculateSoftScore(Worker worker, double distanceKm) {
        // 1. Distance Score: 100 - (distance / 15) * 100 (Trọng số 0.4)
        double distanceScore = Math.max(0.0, 100.0 - (distanceKm / MAX_DISTANCE_KM) * 100.0);

        // 2. Rating Score: (avg_rating / 5) * 100 (Trọng số 0.3)
        double rating = worker.getRatingAvg() != null ? worker.getRatingAvg().doubleValue() : 5.0;
        double ratingScore = Math.min(100.0, (rating / 5.0) * 100.0);

        // 3. Experience Score: (completed_shifts / 10) * 100 (Trọng số 0.2)
        int completedShifts = worker.getTotalShiftsCompleted() != null ? worker.getTotalShiftsCompleted() : 0;
        double expScore = Math.min(100.0, (completedShifts / 10.0) * 100.0);

        // 4. Reliability Score: Trọng số 0.1
        double rsScore = worker.getReliabilityScore() != null ? Math.min(100.0, worker.getReliabilityScore()) : 80.0;

        // Bonus: Người mới (< 3 ca) hoặc nghỉ lâu (> 7 ngày)
        double newWorkerBonus = (completedShifts < 3) ? 10.0 : 0.0;
        double idleBonus = 10.0; // Mặc định khuyến khích quay lại làm việc

        double finalScore = (0.4 * distanceScore) + (0.3 * ratingScore) + (0.2 * expScore) + (0.1 * rsScore) + newWorkerBonus + idleBonus;

        String breakdownJson = String.format(
                Locale.US,
                "{\"distanceKm\":%.2f,\"distScore\":%.1f,\"ratingScore\":%.1f,\"expScore\":%.1f,\"rsScore\":%.1f,\"newWorkerBonus\":%.1f,\"idleBonus\":%.1f,\"finalScore\":%.2f}",
                distanceKm, distanceScore, ratingScore, expScore, rsScore, newWorkerBonus, idleBonus, finalScore
        );

        return CandidateMatch.builder()
                .worker(worker)
                .distanceKm(distanceKm)
                .finalScore(finalScore)
                .scoreBreakdownJson(breakdownJson)
                .build();
    }

    /**
     * Tính khoảng cách Haversine giữa 2 tọa độ GPS (km)
     */
    public static double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 3.0; // Khoảng cách giả định nếu thiếu tọa độ
        }
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(rLat1) * Math.cos(rLat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
