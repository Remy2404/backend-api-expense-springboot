package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.BudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<BudgetEntity, UUID> {

    @Query("SELECT b FROM BudgetEntity b WHERE b.firebaseUid = :firebaseUid AND COALESCE(b.isDeleted, false) = false ORDER BY b.month DESC")
    List<BudgetEntity> findActiveByFirebaseUidOrderByMonthDesc(@Param("firebaseUid") String firebaseUid);

    @Query("SELECT b FROM BudgetEntity b WHERE b.firebaseUid = :firebaseUid ORDER BY b.month DESC")
    List<BudgetEntity> findAllByFirebaseUidOrderByMonthDesc(@Param("firebaseUid") String firebaseUid);
}
