package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.SavingsGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SavingsGoalRepository extends JpaRepository<SavingsGoalEntity, UUID> {

    @Query("SELECT g FROM SavingsGoalEntity g WHERE g.firebaseUid = :firebaseUid AND COALESCE(g.isDeleted, false) = false AND COALESCE(g.isArchived, false) = false ORDER BY g.createdAt DESC")
    List<SavingsGoalEntity> findActiveByFirebaseUidOrderByCreatedAtDesc(@Param("firebaseUid") String firebaseUid);
}
