package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    interface TotalsView {
        BigDecimal getTotalIncome();

        BigDecimal getTotalExpense();
    }

    Optional<TransactionEntity> findByFirebaseUidAndIdempotencyKey(String firebaseUid, String idempotencyKey);

    @Query("""
            SELECT
                COALESCE(SUM(CASE WHEN t.transactionType = 'INCOME' THEN t.amount ELSE 0 END), 0) AS totalIncome,
                COALESCE(SUM(CASE WHEN t.transactionType = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS totalExpense
            FROM TransactionEntity t
            WHERE t.firebaseUid = :firebaseUid
            """)
    TotalsView getTotalsByFirebaseUid(@Param("firebaseUid") String firebaseUid);
}
