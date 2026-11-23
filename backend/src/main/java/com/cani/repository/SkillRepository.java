package com.cani.repository;

import com.cani.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID>, JpaSpecificationExecutor<Skill> {

    List<Skill> findByIsActiveTrue();
    Optional<Skill> findByName(String name);
    Optional<Skill> findByNameIgnoreCase(String name);

    @Query("SELECT DISTINCT s.category FROM Skill s WHERE s.category IS NOT NULL AND TRIM(s.category) != '' ORDER BY s.category ASC")
    List<String> findAllCategories();
}


