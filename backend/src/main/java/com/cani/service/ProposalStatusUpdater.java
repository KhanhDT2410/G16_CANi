package com.cani.service;

import com.cani.entity.enums.ProposalStatus;
import com.cani.repository.DispatchProposalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalStatusUpdater {

    private final DispatchProposalRepository dispatchProposalRepository;

    /**
     * Cập nhật trạng thái của DispatchProposal trong một Transaction độc lập (REQUIRES_NEW).
     * Ngay cả khi giao dịch nghiệp vụ chính bị rollback (do ném BadRequestException),
     * trạng thái EXPIRED hoặc REJECTED của đề xuất vẫn được commit an toàn vào Database.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatusInNewTransaction(UUID proposalId, ProposalStatus status) {
        dispatchProposalRepository.findById(proposalId).ifPresent(p -> {
            p.setStatus(status);
            p.setRespondedAt(Instant.now());
            dispatchProposalRepository.save(p);
            log.info("Transaction độc lập: Đã cập nhật đề xuất việc làm {} thành trạng thái {}", proposalId, status);
        });
    }
}
