package com.cani.repository;

import com.cani.entity.Blacklist;
import com.cani.entity.enums.BlacklistIdentifierType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlacklistRepository extends JpaRepository<Blacklist, UUID>, JpaSpecificationExecutor<Blacklist> {

    boolean existsByIdentifierAndIdentifierType(String identifier, BlacklistIdentifierType identifierType);

    Optional<Blacklist> findByIdentifierAndIdentifierType(String identifier, BlacklistIdentifierType identifierType);
}
