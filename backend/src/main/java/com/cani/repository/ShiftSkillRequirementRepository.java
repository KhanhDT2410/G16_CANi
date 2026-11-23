package com.cani.repository;

import com.cani.entity.ShiftSkillRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShiftSkillRequirementRepository extends JpaRepository<ShiftSkillRequirement, UUID> {

    @Query("SELECT r FROM ShiftSkillRequirement r JOIN FETCH r.skill WHERE r.shift.shiftId = :shiftId")
    List<ShiftSkillRequirement> findByShiftIdWithSkill(@Param("shiftId") UUID shiftId);

    @Query("SELECT r FROM ShiftSkillRequirement r JOIN FETCH r.skill WHERE r.shift.shiftId IN :shiftIds")
    List<ShiftSkillRequirement> findByShiftIdsWithSkill(@Param("shiftIds") Collection<UUID> shiftIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ShiftSkillRequirement r WHERE r.shift.shiftId = :shiftId")
    void deleteByShift_ShiftId(@Param("shiftId") UUID shiftId);
}
