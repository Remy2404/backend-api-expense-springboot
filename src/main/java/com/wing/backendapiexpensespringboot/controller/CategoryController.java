package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.CategoryDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.CategoryService;
import com.wing.backendapiexpensespringboot.service.DefaultCategoryProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final DefaultCategoryProvisioningService defaultCategoryProvisioningService;

    @GetMapping
    public ResponseEntity<List<CategoryDto>> listCategories(@AuthenticationPrincipal UserPrincipal user) {
        String firebaseUid = requireFirebaseUid(user);
        List<CategoryEntity> entities = categoryService.getCategories(firebaseUid);
        if (entities.isEmpty()) {
            defaultCategoryProvisioningService.provisionMissingDefaultCategories(firebaseUid);
            entities = categoryService.getCategories(firebaseUid);
        }
        List<CategoryDto> dtos = entities.stream().map(this::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDto> getCategory(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable("id") UUID id) {
        String firebaseUid = requireFirebaseUid(user);
        CategoryEntity entity = categoryService.getCategoryById(firebaseUid, id);
        if (entity == null) {
            throw AppException.notFound("Category not found");
        }
        return ResponseEntity.ok(toDto(entity));
    }

    private String requireFirebaseUid(UserPrincipal user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            throw AppException.unauthorized("Missing authenticated user");
        }
        return user.getFirebaseUid();
    }

    private CategoryDto toDto(CategoryEntity entity) {
        return CategoryDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .icon(entity.getIcon())
                .color(entity.getColor())
                .isDefault(entity.getIsDefault())
                .categoryType(entity.getCategoryType())
                .sortOrder(entity.getSortOrder())
                .isDeleted(entity.getIsDeleted())
                .deletedAt(formatDateTime(entity.getDeletedAt()))
                .retryCount(entity.getRetryCount())
                .lastError(entity.getLastError())
                .createdAt(formatDateTime(entity.getCreatedAt()))
                .updatedAt(formatDateTime(entity.getUpdatedAt()))
                .syncedAt(formatDateTime(entity.getSyncedAt()))
                .build();
    }

    private String formatDateTime(OffsetDateTime time) {
        if (time == null) {
            return null;
        }
        return time.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
