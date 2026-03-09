package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.BillSplitGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillSplitGroupRepository extends JpaRepository<BillSplitGroupEntity, UUID> {
    List<BillSplitGroupEntity> findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(String createdBy);

    Optional<BillSplitGroupEntity> findByIdAndCreatedBy(UUID id, String createdBy);
}
