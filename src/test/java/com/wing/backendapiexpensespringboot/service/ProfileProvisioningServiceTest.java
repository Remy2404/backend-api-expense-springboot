package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.config.AppConfig;
import com.wing.backendapiexpensespringboot.repository.ProfileUpsertRepository;
import com.wing.backendapiexpensespringboot.security.AppRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileProvisioningServiceTest {

    @Mock
    private ProfileUpsertRepository profileUpsertRepository;

    private ProfileProvisioningService profileProvisioningService;

    @BeforeEach
    void setUp() {
        AppConfig appConfig = new AppConfig();
        DatabaseRetryExecutor databaseRetryExecutor = new DatabaseRetryExecutor(appConfig);
        profileProvisioningService = new ProfileProvisioningService(profileUpsertRepository, databaseRetryExecutor);
    }

    @Test
    void syncProfileReturnsPersistedRoleFromUpsert() {
        when(profileUpsertRepository.upsertProfile(
                "firebase-uid-1",
                "qa@example.com",
                "QA User",
                "https://example.com/avatar.png",
                AppRole.USER)).thenReturn(AppRole.ADMIN);

        AppRole result = profileProvisioningService.syncProfile(
                "firebase-uid-1",
                "qa@example.com",
                "QA User",
                "https://example.com/avatar.png",
                AppRole.USER);

        assertEquals(AppRole.ADMIN, result);
        verify(profileUpsertRepository).upsertProfile(
                "firebase-uid-1",
                "qa@example.com",
                "QA User",
                "https://example.com/avatar.png",
                AppRole.USER);
    }

    @Test
    void syncProfileRetriesTransientFailuresBeforeSucceeding() {
        when(profileUpsertRepository.upsertProfile(
                "firebase-uid-2",
                "retry@example.com",
                "Retry User",
                null,
                AppRole.USER))
                        .thenThrow(new CannotCreateTransactionException("pool exhausted"))
                        .thenReturn(AppRole.USER);

        AppRole result = profileProvisioningService.syncProfile(
                "firebase-uid-2",
                "retry@example.com",
                "Retry User",
                null,
                AppRole.USER);

        assertEquals(AppRole.USER, result);
        verify(profileUpsertRepository, times(2)).upsertProfile(
                "firebase-uid-2",
                "retry@example.com",
                "Retry User",
                null,
                AppRole.USER);
    }

    @Test
    void syncProfileDoesNotRetryNonTransientFailures() {
        when(profileUpsertRepository.upsertProfile(
                "firebase-uid-3",
                "broken@example.com",
                "Broken User",
                null,
                AppRole.USER)).thenThrow(new IllegalStateException("invalid profile payload"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> profileProvisioningService.syncProfile(
                        "firebase-uid-3",
                        "broken@example.com",
                        "Broken User",
                        null,
                        AppRole.USER));

        assertEquals("invalid profile payload", exception.getMessage());
        verify(profileUpsertRepository).upsertProfile(
                "firebase-uid-3",
                "broken@example.com",
                "Broken User",
                null,
                AppRole.USER);
    }
}
