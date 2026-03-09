package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.BillSplitSettlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BillSplitSettlementRepository extends JpaRepository<BillSplitSettlementEntity, UUID> {
    List<BillSplitSettlementEntity> findByGroupIdOrderByCreatedAtDesc(UUID groupId);
}
