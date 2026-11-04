package com.cani.service;

import com.cani.dto.dispute.CreateDisputeRequestDto;
import com.cani.dto.dispute.DisputeResponseDto;
import com.cani.entity.Dispute;
import com.cani.entity.ShiftAssignment;
import com.cani.entity.User;
import com.cani.entity.enums.DisputeStatus;
import com.cani.exception.AppException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.DisputeRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final UserRepository userRepository;

    /**
     * UC-96: Nộp đơn khiếu nại tranh chấp (Worker hoặc EO)
     */
    @Transactional
    public DisputeResponseDto submitDispute(UUID callerUserId, CreateDisputeRequestDto request) {
        ShiftAssignment assignment = shiftAssignmentRepository.findById(request.getAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân công ca làm việc ID: " + request.getAssignmentId()));

        UUID workerUserId = assignment.getWorker().getUser().getUserId();
        UUID eoUserId = assignment.getShift().getEvent().getEventOrganizer().getUser().getUserId();

        UUID respondentId;
        if (callerUserId.equals(workerUserId)) {
            respondentId = eoUserId;
        } else if (callerUserId.equals(eoUserId)) {
            respondentId = workerUserId;
        } else {
            throw new AppException("Bạn không phải là các bên liên quan trực tiếp trong ca làm này để gửi khiếu nại", HttpStatus.FORBIDDEN);
        }

        Dispute dispute = Dispute.builder()
                .assignmentId(assignment.getAssignmentId())
                .complainantId(callerUserId)
                .respondentId(respondentId)
                .type(request.getType())
                .title(request.getTitle())
                .description(request.getDescription())
                .evidence(request.getEvidence())
                .status(DisputeStatus.SUBMITTED)
                .build();

        Dispute saved = disputeRepository.save(dispute);
        log.info("Người dùng {} đã gửi khiếu nại {} đối với người dùng {} cho phân công ca {}",
                callerUserId, saved.getDisputeId(), respondentId, assignment.getAssignmentId());

        return mapToDto(saved, assignment);
    }

    /**
     * UC-104: Xem lịch sử danh sách khiếu nại của tôi
     */
    @Transactional(readOnly = true)
    public List<DisputeResponseDto> getMyDisputes(UUID callerUserId) {
        List<Dispute> list = disputeRepository.findByComplainantIdOrRespondentIdOrderByCreatedAtDesc(callerUserId, callerUserId);
        return list.stream()
                .map(d -> {
                    ShiftAssignment sa = d.getAssignmentId() != null
                            ? shiftAssignmentRepository.findById(d.getAssignmentId()).orElse(null)
                            : null;
                    return mapToDto(d, sa);
                })
                .collect(Collectors.toList());
    }

    /**
     * UC-97: Xem chi tiết một khiếu nại
     */
    @Transactional(readOnly = true)
    public DisputeResponseDto getDisputeDetail(UUID callerUserId, UUID disputeId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ khiếu nại ID: " + disputeId));

        if (!callerUserId.equals(dispute.getComplainantId()) && !callerUserId.equals(dispute.getRespondentId())) {
            throw new AppException("Bạn không có quyền truy cập hồ sơ khiếu nại này", HttpStatus.FORBIDDEN);
        }

        ShiftAssignment sa = dispute.getAssignmentId() != null
                ? shiftAssignmentRepository.findById(dispute.getAssignmentId()).orElse(null)
                : null;

        return mapToDto(dispute, sa);
    }

    private DisputeResponseDto mapToDto(Dispute d, ShiftAssignment sa) {
        String complainantName = userRepository.findById(d.getComplainantId())
                .map(User::getFullName).orElse("Người dùng " + d.getComplainantId().toString().substring(0, 8));

        String respondentName = userRepository.findById(d.getRespondentId())
                .map(User::getFullName).orElse("Người dùng " + d.getRespondentId().toString().substring(0, 8));

        String eventName = sa != null && sa.getShift() != null && sa.getShift().getEvent() != null
                ? sa.getShift().getEvent().getName()
                : "Sự kiện";

        String jobType = sa != null && sa.getShift() != null
                ? sa.getShift().getJobType()
                : "Vị trí ca";

        return DisputeResponseDto.builder()
                .disputeId(d.getDisputeId())
                .assignmentId(d.getAssignmentId())
                .eventName(eventName)
                .jobType(jobType)
                .complainantId(d.getComplainantId())
                .complainantName(complainantName)
                .respondentId(d.getRespondentId())
                .respondentName(respondentName)
                .type(d.getType())
                .title(d.getTitle())
                .description(d.getDescription())
                .evidence(d.getEvidence())
                .status(d.getStatus())
                .resolution(d.getResolution())
                .resolvedAt(d.getResolvedAt())
                .createdAt(d.getCreatedAt())
                .build();
    }
}

