package com.cani.repository;

import com.cani.entity.WageMatrix;
import com.cani.entity.enums.City;
import com.cani.entity.enums.LeadTime;
import com.cani.entity.enums.TimeSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WageMatrixRepository extends JpaRepository<WageMatrix, UUID> {

    Optional<WageMatrix> findByCityAndJobTypeIgnoreCaseAndTimeSlotAndLeadTime(
            City city, String jobType, TimeSlot timeSlot, LeadTime leadTime
    );

    List<WageMatrix> findByCityAndJobTypeIgnoreCase(City city, String jobType);

    List<WageMatrix> findByJobTypeIgnoreCase(String jobType);

    @Query("SELECT wm FROM WageMatrix wm WHERE " +
           "(:city IS NULL OR wm.city = :city) AND " +
           "(:jobType IS NULL OR LOWER(wm.jobType) LIKE LOWER(CONCAT('%', :jobType, '%'))) AND " +
           "(:isActive IS NULL OR wm.isActive = :isActive)")
    Page<WageMatrix> filterWageMatrix(
            @Param("city") City city,
            @Param("jobType") String jobType,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );
}
