package com.wing.backendapiexpensespringboot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_memories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "firebase_uid", nullable = false)
    private String firebaseUid;

    @Column(name = "merchant", nullable = false)
    private String merchant;

    @Column(name = "resolved_category_id")
    private UUID resolvedCategoryId;

    @Column(name = "override_count")
    private Integer overrideCount;

    @Column(name = "last_corrected_at")
    private LocalDateTime lastCorrectedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
