package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.GoalTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GoalTransactionRepository extends JpaRepository<GoalTransactionEntity, UUID> {

        List<GoalTransactionEntity> findByGoalIdOrderByDateDesc(UUID goalId);

        @Query("SELECT t FROM GoalTransactionEntity t WHERE t.goalId IN :goalIds ORDER BY t.date DESC")
        List<GoalTransactionEntity> findByGoalIdInOrderByDateDesc(
                        @Param("goalIds") List<UUID> goalIds);

        void deleteByGoalId(UUID goalId);

        @org.springframework.data.jpa.repository.Query("DELETE FROM GoalTransactionEntity t WHERE t.goalId = :goalId AND t.id NOT IN :retainedIds")
        @org.springframework.data.jpa.repository.Modifying
        void deleteByGoalIdAndIdNotIn(
                        @org.springframework.data.repository.query.Param("goalId") UUID goalId,
                        @org.springframework.data.repository.query.Param("retainedIds") List<UUID> retainedIds);
}
