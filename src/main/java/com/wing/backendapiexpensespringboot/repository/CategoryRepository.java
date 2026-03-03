package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    List<CategoryEntity> findByFirebaseUidOrderByNameAsc(String firebaseUid);

    Optional<CategoryEntity> findByIdAndFirebaseUid(UUID id, String firebaseUid);

    @Query("""
            SELECT c FROM CategoryEntity c
            WHERE c.firebaseUid = :firebaseUid
              AND (
                  (c.updatedAt IS NULL AND c.createdAt IS NULL)
                  OR COALESCE(c.updatedAt, c.createdAt) >= :since
                  OR (c.deletedAt IS NOT NULL AND c.deletedAt >= :since)
              )
            ORDER BY c.name ASC
            """)
    List<CategoryEntity> findChangedSince(
            @Param("firebaseUid") String firebaseUid,
            @Param("since") LocalDateTime since
    );

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

    @Query("""
            SELECT c FROM CategoryEntity c
            WHERE c.firebaseUid = :firebaseUid
              AND COALESCE(c.isDeleted, false) = false
              AND LOWER(c.name) = LOWER(:name)
              AND c.categoryType = :categoryType
              AND c.id <> :excludeId
            ORDER BY c.updatedAt DESC NULLS LAST, c.createdAt DESC NULLS LAST
            """)
    List<CategoryEntity> findActiveDuplicatesByNameAndTypeExcludingId(
            @Param("firebaseUid") String firebaseUid,
            @Param("name") String name,
            @Param("categoryType") String categoryType,
            @Param("excludeId") UUID excludeId
    );
}
