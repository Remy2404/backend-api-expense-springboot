package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.RecurringExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpenseEntity, UUID> {

    @Query("SELECT r FROM RecurringExpenseEntity r WHERE r.firebaseUid = :firebaseUid AND COALESCE(r.isDeleted, false) = false AND COALESCE(r.isActive, true) = true ORDER BY r.nextDueDate ASC")
    List<RecurringExpenseEntity> findActiveByFirebaseUidOrderByNextDueDateAsc(@Param("firebaseUid") String firebaseUid);
}
