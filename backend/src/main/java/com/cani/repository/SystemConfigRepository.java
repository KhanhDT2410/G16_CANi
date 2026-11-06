package com.cani.repository;

import com.cani.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, UUID> {

    /**
     * Tìm kiếm cấu hình theo khóa định danh duy nhất (key).
     */
    Optional<SystemConfig> findByKey(String key);

    /**
     * Kiểm tra xem cấu hình có tồn tại theo khóa định danh hay không.
     */
    boolean existsByKey(String key);

    /**
     * Lấy toàn bộ danh sách cấu hình sắp xếp theo key theo thứ tự bảng chữ cái.
     */
    List<SystemConfig> findAllByOrderByKeyAsc();
}

