package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.model.MemoryEntity;
import com.wing.backendapiexpensespringboot.repository.MemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MemoryRepository memoryRepository;

    public Optional<MemoryEntity> getByMerchant(String firebaseUid, String merchant) {
        try {
            return memoryRepository.findByFirebaseUidAndMerchantIgnoreCase(firebaseUid, merchant);
        } catch (DataAccessException exception) {
            log.warn("Memory lookup failed for merchant '{}' and user '{}': {}", merchant, firebaseUid, exception.getMessage());
            return Optional.empty();
        }
    }

    public Optional<MemoryEntity> getByCategory(String firebaseUid, UUID categoryId) {
        try {
            return memoryRepository.findByFirebaseUidAndResolvedCategoryId(firebaseUid, categoryId)
                    .stream()
                    .findFirst();
        } catch (DataAccessException exception) {
            log.warn("Memory lookup failed for category '{}' and user '{}': {}", categoryId, firebaseUid, exception.getMessage());
            return Optional.empty();
        }
    }

    @Transactional
    public int applyCorrection(String firebaseUid, String merchant, UUID correctedCategoryId) {
        Optional<MemoryEntity> existing;
        try {
            existing = memoryRepository.findByFirebaseUidAndMerchantIgnoreCase(firebaseUid, merchant);
        } catch (DataAccessException exception) {
            log.warn("Skipping memory correction persistence for merchant '{}' and user '{}': {}", merchant, firebaseUid, exception.getMessage());
            return 0;
        }

        if (existing.isPresent()) {
            MemoryEntity memory = existing.get();
            memory.setResolvedCategoryId(correctedCategoryId);
            memory.setOverrideCount(memory.getOverrideCount() + 1);
            memory.setLastCorrectedAt(LocalDateTime.now());
            memoryRepository.save(memory);
            return memory.getOverrideCount();
        } else {
            MemoryEntity memory = MemoryEntity.builder()
                    .firebaseUid(firebaseUid)
                    .merchant(merchant.toLowerCase().trim())
                    .resolvedCategoryId(correctedCategoryId)
                    .overrideCount(1)
                    .lastCorrectedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();
            memoryRepository.save(memory);
            return 1;
        }
    }
}
