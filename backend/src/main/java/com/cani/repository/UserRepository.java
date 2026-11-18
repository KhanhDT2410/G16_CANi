package com.cani.repository;

import com.cani.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    long countByRole(com.cani.entity.enums.UserRole role);

    long countByStatus(com.cani.entity.enums.UserStatus status);

    List<com.cani.entity.User> findByRole(com.cani.entity.enums.UserRole role);

    List<com.cani.entity.User> findByRoleAndStatus(com.cani.entity.enums.UserRole role,
            com.cani.entity.enums.UserStatus status);

    List<com.cani.entity.User> findByRoleIn(java.util.Collection<com.cani.entity.enums.UserRole> roles);
}
