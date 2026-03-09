package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.ExpenseListItemDto;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseFilterQueryServiceTest {

    @Mock
    private ExpenseService expenseService;

    @InjectMocks
    private ExpenseFilterQueryService expenseFilterQueryService;

    @Test
    void getFilteredExpensesSortsByDateThenUpdatedAtThenCreatedAt() {
        String firebaseUid = "firebase-user-1";

        ExpenseEntity latestByTime = expense(
                "a4fa62ed-dc6d-4e1e-b236-c67a6d907ff0",
                "2026-03-09T09:29:22.973Z",
                "2026-03-09T02:29:25",
                "2026-03-09T02:29:25");
        ExpenseEntity sameDayLaterUpdate = expense(
                "a3919ac3-514b-49a9-b908-9b6cf8ff9669",
                "2026-03-09T00:00:00Z",
                "2026-03-09T08:21:46",
                "2026-03-09T08:49:26");
        ExpenseEntity sameDayEarlierUpdate = expense(
                "425a3bb8-f0a2-497e-8afe-f41f2c246e68",
                "2026-03-09T00:00:00Z",
                "2026-03-09T09:15:16",
                "2026-03-09T09:15:16");
        ExpenseEntity olderDay = expense(
                "d6c23c80-e1d8-4005-a92a-153e1d8dd7cc",
                "2026-03-07T17:00:00Z",
                "2026-03-08T13:24:34",
                "2026-03-08T13:24:34");

        when(expenseService.getExpenses(firebaseUid)).thenReturn(List.of(
                olderDay,
                sameDayEarlierUpdate,
                latestByTime,
                sameDayLaterUpdate
        ));

        List<ExpenseListItemDto> result = expenseFilterQueryService.getFilteredExpenses(
                firebaseUid,
                0,
                10,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(List.of(
                latestByTime.getId(),
                sameDayEarlierUpdate.getId(),
                sameDayLaterUpdate.getId(),
                olderDay.getId()
        ), result.stream().map(ExpenseListItemDto::getId).toList());
    }

    private ExpenseEntity expense(
            String id,
            String date,
            String createdAt,
            String updatedAt
    ) {
        return ExpenseEntity.builder()
                .id(UUID.fromString(id))
                .firebaseUid("firebase-user-1")
                .amount(1.0)
                .transactionType("EXPENSE")
                .date(OffsetDateTime.parse(date))
                .createdAt(LocalDateTime.parse(createdAt))
                .updatedAt(LocalDateTime.parse(updatedAt))
                .build();
    }
}
