package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.PendingAiActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingAiActionRepository extends JpaRepository<PendingAiActionEntity, UUID> {

    Optional<PendingAiActionEntity> findByIdAndFirebaseUidAndStatus(
            UUID id, String firebaseUid, String status);

    void deleteByExpiresAtBefore(Instant cutoff);
}
