package com.cani.service;

import com.cani.entity.DispatchProposal;
import com.cani.entity.enums.ProposalStatus;
import com.cani.repository.DispatchProposalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProposalStatusUpdaterTest {

    @Mock
    private DispatchProposalRepository dispatchProposalRepository;

    @InjectMocks
    private ProposalStatusUpdater proposalStatusUpdater;

    @Test
    @DisplayName("Cập nhật trạng thái proposal thành công trong transaction riêng")
    void updateStatusInNewTransaction_Success() {
        UUID proposalId = UUID.randomUUID();
        DispatchProposal proposal = DispatchProposal.builder()
                .proposalId(proposalId)
                .status(ProposalStatus.PENDING)
                .build();

        when(dispatchProposalRepository.findById(proposalId)).thenReturn(Optional.of(proposal));

        proposalStatusUpdater.updateStatusInNewTransaction(proposalId, ProposalStatus.EXPIRED);

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.EXPIRED);
        assertThat(proposal.getRespondedAt()).isNotNull();
        verify(dispatchProposalRepository, times(1)).save(proposal);
    }

    @Test
    @DisplayName("Proposal không tồn tại thì không gọi save")
    void updateStatusInNewTransaction_NotFound_DoesNothing() {
        UUID proposalId = UUID.randomUUID();
        when(dispatchProposalRepository.findById(proposalId)).thenReturn(Optional.empty());

        proposalStatusUpdater.updateStatusInNewTransaction(proposalId, ProposalStatus.REJECTED);

        verify(dispatchProposalRepository, never()).save(any());
    }
}
