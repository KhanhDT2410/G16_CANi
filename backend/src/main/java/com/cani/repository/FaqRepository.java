package com.cani.repository;

import com.cani.entity.Faq;
import com.cani.entity.enums.FaqCategory;
import com.cani.entity.enums.FaqStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FaqRepository extends JpaRepository<Faq, UUID>, JpaSpecificationExecutor<Faq> {

    List<Faq> findByStatus(FaqStatus status);

    List<Faq> findByStatusAndCategory(FaqStatus status, FaqCategory category);

    List<Faq> findByStatusOrderByViewCountDescPublishedAtDesc(FaqStatus status);

    List<Faq> findByStatusAndCategoryOrderByViewCountDescPublishedAtDesc(FaqStatus status, FaqCategory category);

    Page<Faq> findAll(Pageable pageable);

    long countByStatus(FaqStatus status);

    long countByCategoryAndStatus(FaqCategory category, FaqStatus status);

    List<Faq> findTop5ByOrderByViewCountDesc();

    List<Faq> findTop5ByStatusOrderByViewCountDesc(FaqStatus status);
}

