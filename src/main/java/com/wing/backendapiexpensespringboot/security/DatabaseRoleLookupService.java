package com.wing.backendapiexpensespringboot.security;

import com.wing.backendapiexpensespringboot.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DatabaseRoleLookupService implements RoleLookupService {

    private final ProfileRepository profileRepository;

    @Override
    public Optional<String> findRoleByFirebaseUid(String firebaseUid) {
        return profileRepository.findRoleByFirebaseUid(firebaseUid)
                .map(String::trim)
                .filter(role -> !role.isEmpty());
    }
}
