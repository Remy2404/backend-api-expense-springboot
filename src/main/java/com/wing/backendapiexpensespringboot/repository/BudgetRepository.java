package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.BudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<BudgetEntity, UUID> {

    Optional<BudgetEntity> findByIdAndFirebaseUid(UUID id, String firebaseUid);

    Optional<BudgetEntity> findByMonthAndFirebaseUid(String month, String firebaseUid);

    @Query("""
            SELECT b FROM BudgetEntity b
            WHERE b.month = :month
              AND b.firebaseUid = :firebaseUid
              AND COALESCE(b.isDeleted, false) = false
            """)
    Optional<BudgetEntity> findActiveByMonthAndFirebaseUid(
            @Param("month") String month,
            @Param("firebaseUid") String firebaseUid);

    @Query("SELECT b FROM BudgetEntity b WHERE b.firebaseUid = :firebaseUid AND COALESCE(b.isDeleted, false) = false ORDER BY b.month DESC")
    List<BudgetEntity> findActiveByFirebaseUidOrderByMonthDesc(@Param("firebaseUid") String firebaseUid);

    @Query("SELECT b FROM BudgetEntity b WHERE b.firebaseUid = :firebaseUid ORDER BY b.month DESC")
    List<BudgetEntity> findAllByFirebaseUidOrderByMonthDesc(@Param("firebaseUid") String firebaseUid);

    @Query("""
            SELECT b FROM BudgetEntity b
            WHERE b.firebaseUid = :firebaseUid
              AND (
                  (b.updatedAt IS NULL AND b.createdAt IS NULL)
                  OR COALESCE(b.updatedAt, b.createdAt) >= :since
                  OR (b.deletedAt IS NOT NULL AND b.deletedAt >= :since)
              )
            ORDER BY b.month DESC
            """)
    List<BudgetEntity> findChangedSince(
            @Param("firebaseUid") String firebaseUid,
            @Param("since") OffsetDateTime since);
}
