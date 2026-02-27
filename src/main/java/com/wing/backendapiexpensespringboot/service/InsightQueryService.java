package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.InsightDto;
import com.wing.backendapiexpensespringboot.model.InsightEntity;
import com.wing.backendapiexpensespringboot.repository.InsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InsightQueryService {

    private final InsightRepository insightRepository;

    public List<InsightDto> getInsights(String firebaseUid, int offset, int limit) {
        QueryPagination.validate(offset, limit);

        return QueryPagination.slice(
                        insightRepository.findByFirebaseUidOrderByCreatedAtDesc(firebaseUid),
                        offset,
                        limit
                )
                .stream()
                .map(this::toDto)
                .toList();
    }

    private InsightDto toDto(InsightEntity entity) {
        return InsightDto.builder()
                .id(entity.getId())
                .insightType(entity.getInsightType())
                .periodStart(entity.getPeriodStart())
                .periodEnd(entity.getPeriodEnd())
                .summaryText(entity.getSummaryText())
                .dataSnapshot(entity.getDataSnapshot())
                .isRead(entity.getIsRead())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
