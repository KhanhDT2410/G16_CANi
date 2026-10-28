package com.cani.repository;

import com.cani.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByAssignmentIdAndReviewerId(UUID assignmentId, UUID reviewerId);

    List<Review> findByRevieweeIdOrderByCreatedAtDesc(UUID revieweeId);

    List<Review> findByReviewerIdOrderByCreatedAtDesc(UUID reviewerId);
}
