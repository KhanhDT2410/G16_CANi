package com.cani.repository;

import com.cani.entity.EoWorker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EoWorkerRepository extends JpaRepository<EoWorker, UUID> {

    Optional<EoWorker> findByEventOrganizer_EoIdAndWorker_WorkerId(UUID eoId, UUID workerId);

    boolean existsByEventOrganizer_EoIdAndWorker_WorkerId(UUID eoId, UUID workerId);

    @Query("SELECT ew FROM EoWorker ew " +
           "JOIN FETCH ew.worker w " +
           "JOIN FETCH w.user u " +
           "WHERE ew.eventOrganizer.eoId = :eoId " +
           "AND (ew.isBlacklisted IS NULL OR ew.isBlacklisted = false) " +
           "ORDER BY ew.isFavorited DESC, ew.createdAt DESC")
    List<EoWorker> findActiveTeamByEoId(@Param("eoId") UUID eoId);

    default List<EoWorker> findMyTeamMembers(UUID eoId, String search, Boolean favoritedOnly) {
        List<EoWorker> all = findActiveTeamByEoId(eoId);
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        boolean onlyFav = Boolean.TRUE.equals(favoritedOnly);
        String q = (search != null && !search.isBlank()) ? search.trim().toLowerCase() : null;
        String digits = q != null ? q.replaceAll("[^0-9]", "") : "";
        String altPhone = null;
        if (digits.length() >= 8) {
            altPhone = digits.startsWith("84") ? "0" + digits.substring(2)
                    : (digits.startsWith("0") ? "84" + digits.substring(1) : digits);
        }
        final String alt = altPhone;

        return all.stream()
                .filter(ew -> !onlyFav || Boolean.TRUE.equals(ew.getIsFavorited()))
                .filter(ew -> {
                    if (q == null) return true;
                    String name = ew.getWorker() != null && ew.getWorker().getFullName() != null
                            ? ew.getWorker().getFullName().toLowerCase() : "";
                    String phone = (ew.getWorker() != null && ew.getWorker().getUser() != null && ew.getWorker().getUser().getPhone() != null)
                            ? ew.getWorker().getUser().getPhone().toLowerCase() : "";
                    String email = (ew.getWorker() != null && ew.getWorker().getUser() != null && ew.getWorker().getUser().getEmail() != null)
                            ? ew.getWorker().getUser().getEmail().toLowerCase() : "";
                    return name.contains(q)
                            || phone.contains(q)
                            || email.contains(q)
                            || (alt != null && phone.contains(alt));
                })
                .toList();
    }
}
