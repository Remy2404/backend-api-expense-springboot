package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.GoalTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GoalTransactionRepository extends JpaRepository<GoalTransactionEntity, UUID> {

    List<GoalTransactionEntity> findByGoalIdOrderByDateDesc(UUID goalId);
}
