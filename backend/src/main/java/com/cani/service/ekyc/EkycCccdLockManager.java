package com.cani.service.ekyc;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Cơ chế khóa đồng bộ theo hash CCCD để chống Race Condition khi hai tài khoản (Worker/EO)
 * cùng gửi xác minh một số CCCD tại cùng thời điểm.
 *
 * - Tầng JVM: Striped ReentrantLock theo key hash (hiệu quả cả trên H2 và unit test).
 * - Tầng PostgreSQL: Transaction-scoped advisory lock (pg_advisory_xact_lock) khi đang chạy
 *   trong transaction PostgreSQL thật (tự động bỏ qua an toàn trên H2).
 */
@Slf4j
@Component
public class EkycCccdLockManager {

    private static final int STRIPE_COUNT = 256;
    private final ReentrantLock[] stripes = new ReentrantLock[STRIPE_COUNT];
    private final boolean isPostgres;

    @PersistenceContext
    private EntityManager entityManager;

    public EkycCccdLockManager() {
        this(false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EkycCccdLockManager(org.springframework.beans.factory.ObjectProvider<DataSource> dataSourceProvider) {
        this(detectPostgres(dataSourceProvider != null ? dataSourceProvider.getIfAvailable() : null));
    }

    private EkycCccdLockManager(boolean isPostgres) {
        this.isPostgres = isPostgres;
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripes[i] = new ReentrantLock();
        }
    }

    public <T> T executeWithCccdLock(String cccdHash, Supplier<T> action) {
        if (cccdHash == null || cccdHash.isBlank()) {
            return action.get();
        }
        int stripeIdx = Math.floorMod(cccdHash.hashCode(), STRIPE_COUNT);
        ReentrantLock lock = stripes[stripeIdx];
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gọi bên trong Transaction đang mở để khóa đồng bộ cấp database trên PostgreSQL.
     */
    public void acquireDbTransactionLockIfPostgres(String cccdHash) {
        if (!isPostgres || entityManager == null || cccdHash == null || cccdHash.isBlank()) {
            return;
        }
        entityManager.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", "ekyc_cccd:" + cccdHash)
                .getSingleResult();
    }

    private static boolean detectPostgres(DataSource dataSource) {
        if (dataSource == null) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            String product = conn.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase().contains("postgresql");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Cannot determine eKYC locking mode", e);
        }
    }
}
