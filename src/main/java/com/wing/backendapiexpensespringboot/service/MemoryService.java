package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.model.MemoryEntity;
import com.wing.backendapiexpensespringboot.repository.MemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        return memoryRepository.findByFirebaseUidAndMerchantIgnoreCase(firebaseUid, merchant);
    }

    public Optional<MemoryEntity> getByCategory(String firebaseUid, UUID categoryId) {
        return memoryRepository.findByFirebaseUidAndResolvedCategoryId(firebaseUid, categoryId)
                .stream().findFirst();
    }

    @Transactional
    public int applyCorrection(String firebaseUid, String merchant, UUID correctedCategoryId) {
        Optional<MemoryEntity> existing = memoryRepository.findByFirebaseUidAndMerchantIgnoreCase(firebaseUid, merchant);

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
