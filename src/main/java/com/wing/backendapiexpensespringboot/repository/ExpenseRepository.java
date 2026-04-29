package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<ExpenseEntity, UUID> {

    interface FinanceSummaryAggregate {
        Double getTotalIncome();

        Double getTotalExpense();

        Long getTransactionCount();
    }

    List<ExpenseEntity> findByFirebaseUidOrderByDateDesc(String firebaseUid);

    @Query("""
            SELECT e FROM ExpenseEntity e
            WHERE e.firebaseUid = :firebaseUid
              AND COALESCE(e.isDeleted, false) = false
            ORDER BY e.date DESC
            """)
    List<ExpenseEntity> findActiveByFirebaseUidOrderByDateDesc(
            @Param("firebaseUid") String firebaseUid,
            Pageable pageable);

    Optional<ExpenseEntity> findByIdAndFirebaseUid(UUID id, String firebaseUid);

    @Query("""
            SELECT e FROM ExpenseEntity e
            WHERE e.firebaseUid = :firebaseUid
              AND e.date >= :startInclusive
              AND e.date < :endExclusive
            ORDER BY e.date DESC
            """)
    List<ExpenseEntity> findByFirebaseUidAndDateBetweenOrderByDateDesc(
            @Param("firebaseUid") String firebaseUid,
            @Param("startInclusive") OffsetDateTime startInclusive,
            @Param("endExclusive") OffsetDateTime endExclusive);

    @Query("""
            SELECT e FROM ExpenseEntity e
            WHERE e.firebaseUid = :firebaseUid
              AND (
                  COALESCE(e.updatedAt, e.createdAt) >= :since
                  OR (e.deletedAt IS NOT NULL AND e.deletedAt >= :since)
              )
            ORDER BY COALESCE(e.updatedAt, e.createdAt) ASC
            """)
    List<ExpenseEntity> findChangedSince(
            @Param("firebaseUid") String firebaseUid,
            @Param("since") OffsetDateTime since);

    @Query("SELECT DISTINCT e.categoryId FROM ExpenseEntity e WHERE e.firebaseUid = :firebaseUid")
    List<UUID> findDistinctCategoryIdsByFirebaseUid(@Param("firebaseUid") String firebaseUid);

    @Query("SELECT e FROM ExpenseEntity e WHERE e.firebaseUid = :firebaseUid AND e.categoryId = :categoryId")
    List<ExpenseEntity> findByFirebaseUidAndCategoryId(
            @Param("firebaseUid") String firebaseUid,
            @Param("categoryId") UUID categoryId);

    @Query("""
            SELECT SUM(e.amount) FROM ExpenseEntity e
            WHERE e.firebaseUid = :firebaseUid
              AND e.date >= :startInclusive
              AND e.date < :endExclusive
            """)
    Double sumAmountByFirebaseUidAndDateBetween(
            @Param("firebaseUid") String firebaseUid,
            @Param("startInclusive") OffsetDateTime startInclusive,
            @Param("endExclusive") OffsetDateTime endExclusive);

    @Query("""
            SELECT e FROM ExpenseEntity e
            WHERE e.firebaseUid = :firebaseUid
              AND e.categoryId = :categoryId
              AND e.date >= :startInclusive
              AND e.date < :endExclusive
            ORDER BY e.date DESC
            """)
    List<ExpenseEntity> findByFirebaseUidAndCategoryIdAndDateBetween(
            @Param("firebaseUid") String firebaseUid,
            @Param("categoryId") UUID categoryId,
            @Param("startInclusive") OffsetDateTime startInclusive,
            @Param("endExclusive") OffsetDateTime endExclusive);

    @Query("""
            SELECT
                COALESCE(SUM(CASE WHEN UPPER(COALESCE(e.transactionType, 'EXPENSE')) = 'INCOME' THEN e.amount ELSE 0 END), 0) AS totalIncome,
                COALESCE(SUM(CASE WHEN UPPER(COALESCE(e.transactionType, 'EXPENSE')) = 'EXPENSE' THEN e.amount ELSE 0 END), 0) AS totalExpense,
                COUNT(e) AS transactionCount
            FROM ExpenseEntity e
            WHERE e.firebaseUid = :firebaseUid
              AND COALESCE(e.isDeleted, false) = false
            """)
    FinanceSummaryAggregate summarizeByFirebaseUid(
            @Param("firebaseUid") String firebaseUid
    );

    @Query("""
            SELECT
                COALESCE(SUM(CASE WHEN UPPER(COALESCE(e.transactionType, 'EXPENSE')) = 'INCOME' THEN e.amount ELSE 0 END), 0) AS totalIncome,
                COALESCE(SUM(CASE WHEN UPPER(COALESCE(e.transactionType, 'EXPENSE')) = 'EXPENSE' THEN e.amount ELSE 0 END), 0) AS totalExpense,
                COUNT(e) AS transactionCount
            FROM ExpenseEntity e
            WHERE e.firebaseUid = :firebaseUid
              AND COALESCE(e.isDeleted, false) = false
              AND e.date >= :dateFrom
              AND e.date < :dateToExclusive
            """)
    FinanceSummaryAggregate summarizeByFirebaseUidAndDateBetween(
            @Param("firebaseUid") String firebaseUid,
            @Param("dateFrom") OffsetDateTime dateFrom,
            @Param("dateToExclusive") OffsetDateTime dateToExclusive
    );
}
