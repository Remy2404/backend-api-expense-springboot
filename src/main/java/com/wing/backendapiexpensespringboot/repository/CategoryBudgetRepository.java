package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.CategoryBudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryBudgetRepository extends JpaRepository<CategoryBudgetEntity, UUID> {

    @Query("SELECT cb FROM CategoryBudgetEntity cb JOIN BudgetEntity b ON b.id = cb.budgetId WHERE cb.budgetId = :budgetId AND b.firebaseUid = :firebaseUid AND COALESCE(b.isDeleted, false) = false")
    List<CategoryBudgetEntity> findByBudgetIdAndFirebaseUid(
            @Param("budgetId") UUID budgetId,
            @Param("firebaseUid") String firebaseUid);
}
