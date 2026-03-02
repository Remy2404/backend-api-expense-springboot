package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    List<CategoryEntity> findByFirebaseUidOrderByNameAsc(String firebaseUid);

    @Query("""
            SELECT c FROM CategoryEntity c
            WHERE c.firebaseUid = :firebaseUid
              AND COALESCE(c.isDeleted, false) = false
              AND c.categoryType = :categoryType
            ORDER BY c.name ASC
            """)
    List<CategoryEntity> findActiveByFirebaseUidAndCategoryTypeOrderByNameAsc(
            @Param("firebaseUid") String firebaseUid,
            @Param("categoryType") String categoryType
    );

    @Query("""
            SELECT c FROM CategoryEntity c
            WHERE c.id = :categoryId
              AND c.firebaseUid = :firebaseUid
              AND COALESCE(c.isDeleted, false) = false
            """)
    Optional<CategoryEntity> findOwnedActiveCategory(
            @Param("firebaseUid") String firebaseUid,
            @Param("categoryId") UUID categoryId
    );
}
