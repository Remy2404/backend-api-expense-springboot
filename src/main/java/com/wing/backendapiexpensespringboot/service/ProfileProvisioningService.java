package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.repository.ProfileUpsertRepository;
import com.wing.backendapiexpensespringboot.security.AppRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProfileProvisioningService {

    private final ProfileUpsertRepository profileUpsertRepository;
    private final DatabaseRetryExecutor databaseRetryExecutor;

    public AppRole syncProfile(
            String firebaseUid,
            String email,
            String displayName,
            String photoUrl,
            AppRole claimedRole
    ) {
        if (!StringUtils.hasText(firebaseUid)) {
            throw new IllegalArgumentException("firebaseUid is required for profile provisioning.");
        }

        return databaseRetryExecutor.execute(
                "profile upsert",
                () -> profileUpsertRepository.upsertProfile(firebaseUid, email, displayName, photoUrl, claimedRole));
    }
}
