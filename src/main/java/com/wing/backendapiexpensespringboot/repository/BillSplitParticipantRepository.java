package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.BillSplitParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BillSplitParticipantRepository extends JpaRepository<BillSplitParticipantEntity, UUID> {
    List<BillSplitParticipantEntity> findByGroupIdOrderByCreatedAtAsc(UUID groupId);
}
