package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.ProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<ProfileEntity, UUID> {

    Optional<ProfileEntity> findByFirebaseUid(String firebaseUid);

    @Query("SELECT p.role FROM ProfileEntity p WHERE p.firebaseUid = :firebaseUid")
    Optional<String> findRoleByFirebaseUid(@Param("firebaseUid") String firebaseUid);
}
