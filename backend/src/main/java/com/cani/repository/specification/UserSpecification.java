package com.cani.repository.specification;

import com.cani.dto.admin.user.UserFilterRequest;
import com.cani.entity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {

    public static Specification<User> filterUsers(UserFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                // Tìm kiếm theo từ khóa (fullName, email, phone)
                if (filter.getSearch() != null && !filter.getSearch().trim().isEmpty()) {
                    String pattern = "%" + filter.getSearch().trim().toLowerCase() + "%";
                    Predicate namePredicate = cb.like(cb.lower(root.get("fullName")), pattern);
                    Predicate emailPredicate = cb.like(cb.lower(root.get("email")), pattern);
                    Predicate phonePredicate = cb.like(cb.lower(root.get("phone")), pattern);
                    predicates.add(cb.or(namePredicate, emailPredicate, phonePredicate));
                }

                // Lọc theo role
                if (filter.getRole() != null) {
                    predicates.add(cb.equal(root.get("role"), filter.getRole()));
                }

                // Lọc theo status
                if (filter.getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), filter.getStatus()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
