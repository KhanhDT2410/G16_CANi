package com.cani.repository;

import com.cani.entity.WageMatrix;
import com.cani.entity.enums.City;
import com.cani.entity.enums.LeadTime;
import com.cani.entity.enums.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WageMatrixRepository extends JpaRepository<WageMatrix, UUID>, JpaSpecificationExecutor<WageMatrix> {

    Optional<WageMatrix> findByCityAndJobTypeIgnoreCaseAndTimeSlotAndLeadTime(
            City city, String jobType, TimeSlot timeSlot, LeadTime leadTime
    );

    List<WageMatrix> findByCityAndJobTypeIgnoreCase(City city, String jobType);

    List<WageMatrix> findByJobTypeIgnoreCase(String jobType);
}
