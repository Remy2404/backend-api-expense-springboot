package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.AiChatMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiChatMessageRepository extends JpaRepository<AiChatMessageEntity, UUID> {

    Page<AiChatMessageEntity> findByFirebaseUidOrderByCreatedAtDesc(String firebaseUid, Pageable pageable);

    long deleteByFirebaseUid(String firebaseUid);
}
