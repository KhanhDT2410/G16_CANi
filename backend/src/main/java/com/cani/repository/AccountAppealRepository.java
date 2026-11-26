package com.cani.repository;

import com.cani.entity.AccountAppeal;
import com.cani.entity.enums.AccountAppealStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountAppealRepository extends JpaRepository<AccountAppeal, UUID> {

    Optional<AccountAppeal> findTopByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    boolean existsByUser_UserIdAndStatusIn(UUID userId, Collection<AccountAppealStatus> statuses);

    Page<AccountAppeal> findByStatusOrderByCreatedAtDesc(AccountAppealStatus status, Pageable pageable);

    Page<AccountAppeal> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(AccountAppealStatus status);

    long countByStatusIn(Collection<AccountAppealStatus> statuses);
}
