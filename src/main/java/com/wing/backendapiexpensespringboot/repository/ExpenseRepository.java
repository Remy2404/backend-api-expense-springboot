package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<ExpenseEntity, UUID> {

    List<ExpenseEntity> findByFirebaseUidOrderByDateDesc(String firebaseUid);

    List<ExpenseEntity> findByFirebaseUidAndDateBetweenOrderByDateDesc(
            String firebaseUid, LocalDate start, LocalDate end);

    @Query("SELECT DISTINCT e.categoryId FROM ExpenseEntity e WHERE e.firebaseUid = :firebaseUid")
    List<UUID> findDistinctCategoryIdsByFirebaseUid(@Param("firebaseUid") String firebaseUid);

    @Query("SELECT e FROM ExpenseEntity e WHERE e.firebaseUid = :firebaseUid AND e.categoryId = :categoryId")
    List<ExpenseEntity> findByFirebaseUidAndCategoryId(
            @Param("firebaseUid") String firebaseUid,
            @Param("categoryId") UUID categoryId);

    @Query("SELECT SUM(e.amount) FROM ExpenseEntity e WHERE e.firebaseUid = :firebaseUid AND e.date BETWEEN :start AND :end")
    Double sumAmountByFirebaseUidAndDateBetween(
            @Param("firebaseUid") String firebaseUid,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT e FROM ExpenseEntity e WHERE e.firebaseUid = :firebaseUid AND e.categoryId = :categoryId AND e.date BETWEEN :start AND :end")
    List<ExpenseEntity> findByFirebaseUidAndCategoryIdAndDateBetween(
            @Param("firebaseUid") String firebaseUid,
            @Param("categoryId") UUID categoryId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
