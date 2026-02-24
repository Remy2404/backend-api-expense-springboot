package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.CorrectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CorrectionRepository extends JpaRepository<CorrectionEntity, UUID> {

    List<CorrectionEntity> findByFirebaseUidOrderByCreatedAtDesc(String firebaseUid);

    List<CorrectionEntity> findByFirebaseUidAndCorrectedCategoryId(
            String firebaseUid, UUID correctedCategoryId);
}
