package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.BillSplitShareEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BillSplitShareRepository extends JpaRepository<BillSplitShareEntity, UUID> {
    List<BillSplitShareEntity> findByExpenseIdIn(List<UUID> expenseId);
}
