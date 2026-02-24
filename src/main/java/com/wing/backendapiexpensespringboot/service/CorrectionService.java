package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.model.CorrectionEntity;
import com.wing.backendapiexpensespringboot.repository.CorrectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CorrectionService {

    private final CorrectionRepository correctionRepository;

    @Transactional
    public CorrectionEntity insertCorrection(String firebaseUid, UUID expenseId, UUID originalCategoryId,
                                              UUID correctedCategoryId, Double originalAmount, Double correctedAmount,
                                              String originalMerchant, String correctedMerchant) {
        CorrectionEntity correction = CorrectionEntity.builder()
                .firebaseUid(firebaseUid)
                .expenseId(expenseId)
                .originalCategoryId(originalCategoryId)
                .correctedCategoryId(correctedCategoryId)
                .originalAmount(originalAmount)
                .correctedAmount(correctedAmount)
                .originalMerchant(originalMerchant)
                .correctedMerchant(correctedMerchant)
                .createdAt(LocalDateTime.now())
                .build();

        return correctionRepository.save(correction);
    }
}
