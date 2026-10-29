package com.cani.repository;

import com.cani.entity.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, UUID> {

    Optional<UserDeviceToken> findByFcmToken(String fcmToken);

    List<UserDeviceToken> findByUser_UserIdAndIsActiveTrue(UUID userId);

    @Modifying
    @Query("UPDATE UserDeviceToken t SET t.isActive = false, t.updatedAt = CURRENT_TIMESTAMP WHERE t.fcmToken = :token")
    void deactivateToken(@Param("token") String token);

    @Modifying
    @Query("UPDATE UserDeviceToken t SET t.isActive = false, t.updatedAt = CURRENT_TIMESTAMP WHERE t.user.userId = :userId AND t.fcmToken = :token")
    void deactivateTokenForUser(@Param("userId") UUID userId, @Param("token") String token);
}
