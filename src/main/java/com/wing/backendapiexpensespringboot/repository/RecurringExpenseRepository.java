package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.RecurringExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
@Repository
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpenseEntity, UUID> {
    Optional<RecurringExpenseEntity> findByIdAndFirebaseUid(UUID id, String firebaseUid);
    @Query("SELECT r FROM RecurringExpenseEntity r WHERE r.firebaseUid = :firebaseUid AND COALESCE(r.isDeleted, false) = false AND COALESCE(r.isActive, true) = true ORDER BY r.nextDueDate ASC")
    List<RecurringExpenseEntity> findActiveByFirebaseUidOrderByNextDueDateAsc(@Param("firebaseUid") String firebaseUid);
    @Query("SELECT r FROM RecurringExpenseEntity r WHERE r.firebaseUid = :firebaseUid ORDER BY r.nextDueDate ASC")
    List<RecurringExpenseEntity> findAllByFirebaseUidOrderByNextDueDateAsc(@Param("firebaseUid") String firebaseUid);
    @Query("""
            SELECT r FROM RecurringExpenseEntity r
            WHERE r.firebaseUid = :firebaseUid
              AND (
                  (r.updatedAt IS NULL AND r.createdAt IS NULL)
                  OR COALESCE(r.updatedAt, r.createdAt) >= :since
                  OR (r.deletedAt IS NOT NULL AND r.deletedAt >= :since)
              )
            ORDER BY r.nextDueDate ASC
            """)
    List<RecurringExpenseEntity> findChangedSince(
            @Param("firebaseUid") String firebaseUid,
            @Param("since") OffsetDateTime since
    );
}
