package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.BillSplitExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BillSplitExpenseRepository extends JpaRepository<BillSplitExpenseEntity, UUID> {
    List<BillSplitExpenseEntity> findByGroupIdOrderByDateDesc(UUID groupId);
}
