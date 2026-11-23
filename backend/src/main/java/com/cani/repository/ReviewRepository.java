package com.cani.repository;

import com.cani.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByAssignmentIdAndReviewerId(UUID assignmentId, UUID reviewerId);

    List<Review> findByAssignmentIdIn(Collection<UUID> assignmentIds);

    List<Review> findByRevieweeIdOrderByCreatedAtDesc(UUID revieweeId);

    List<Review> findByReviewerIdOrderByCreatedAtDesc(UUID reviewerId);

    @Query("SELECT r FROM Review r WHERE r.revieweeId IN :revieweeIds " +
           "ORDER BY CASE WHEN r.createdAt IS NULL THEN 1 ELSE 0 END ASC, r.createdAt DESC")
    List<Review> findLatestByRevieweeIds(@Param("revieweeIds") Collection<UUID> revieweeIds, Pageable pageable);

    @Query("SELECT r.rating FROM Review r WHERE r.revieweeId IN :revieweeIds AND r.rating IS NOT NULL AND r.rating BETWEEN 1 AND 5")
    List<Integer> findValidRatingsByRevieweeIds(@Param("revieweeIds") Collection<UUID> revieweeIds);
}
