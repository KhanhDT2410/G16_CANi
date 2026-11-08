package com.cani.repository;

import com.cani.entity.RsAppeal;
import com.cani.entity.enums.RsAppealStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RsAppealRepository extends JpaRepository<RsAppeal, UUID> {

    List<RsAppeal> findByWorker_WorkerIdOrderByCreatedAtDesc(UUID workerId);

    Page<RsAppeal> findByStatus(RsAppealStatus status, Pageable pageable);

    Page<RsAppeal> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByReliabilityLog_LogId(UUID logId);
}

