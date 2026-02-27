package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.InsightEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InsightRepository extends JpaRepository<InsightEntity, UUID> {

    List<InsightEntity> findByFirebaseUidOrderByCreatedAtDesc(String firebaseUid);
}
