package com.cani.repository;

import com.cani.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, UUID> {

    Optional<Worker> findByUser_UserId(UUID userId);

    List<Worker> findByIsOnlineTrue();

    boolean existsByCccdNumberInAndWorkerIdNot(Collection<String> cccdNumbers, UUID workerId);

    boolean existsByCccdNumberIn(Collection<String> cccdNumbers);
}
