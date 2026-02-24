package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.MemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemoryRepository extends JpaRepository<MemoryEntity, UUID> {

    Optional<MemoryEntity> findByFirebaseUidAndMerchantIgnoreCase(String firebaseUid, String merchant);

    List<MemoryEntity> findByFirebaseUidOrderByOverrideCountDesc(String firebaseUid);

    List<MemoryEntity> findByFirebaseUidAndResolvedCategoryId(String firebaseUid, UUID resolvedCategoryId);
}
