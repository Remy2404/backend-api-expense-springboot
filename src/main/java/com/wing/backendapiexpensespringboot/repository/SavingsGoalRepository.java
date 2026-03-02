package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.SavingsGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

@Repository
public interface SavingsGoalRepository extends JpaRepository<SavingsGoalEntity, UUID> {

    Optional<SavingsGoalEntity> findByIdAndFirebaseUid(UUID id, String firebaseUid);

    @Query("SELECT g FROM SavingsGoalEntity g WHERE g.firebaseUid = :firebaseUid AND COALESCE(g.isDeleted, false) = false AND COALESCE(g.isArchived, false) = false ORDER BY g.createdAt DESC")
    List<SavingsGoalEntity> findActiveByFirebaseUidOrderByCreatedAtDesc(@Param("firebaseUid") String firebaseUid);

    @Query("SELECT g FROM SavingsGoalEntity g WHERE g.firebaseUid = :firebaseUid ORDER BY g.createdAt DESC")
    List<SavingsGoalEntity> findAllByFirebaseUidOrderByCreatedAtDesc(@Param("firebaseUid") String firebaseUid);

    @Query("""
            SELECT g FROM SavingsGoalEntity g
            WHERE g.firebaseUid = :firebaseUid
              AND (
                  (g.updatedAt IS NULL AND g.createdAt IS NULL)
                  OR COALESCE(g.updatedAt, g.createdAt) >= :since
                  OR (g.deletedAt IS NOT NULL AND g.deletedAt >= :since)
              )
            ORDER BY g.createdAt DESC
            """)
    List<SavingsGoalEntity> findChangedSince(
            @Param("firebaseUid") String firebaseUid,
            @Param("since") LocalDateTime since
    );
}
