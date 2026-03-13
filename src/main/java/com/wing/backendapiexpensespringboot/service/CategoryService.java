package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.model.CategoryType;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryEntity> getCategories(String firebaseUid) {
        return categoryRepository.findActiveByFirebaseUidOrderByNameAsc(firebaseUid);
    }

    public List<CategoryEntity> getCategoriesByType(String firebaseUid, CategoryType categoryType) {
        return categoryRepository.findActiveByFirebaseUidAndCategoryTypeOrderByNameAsc(
                firebaseUid,
                categoryType.name()
        );
    }

    public CategoryEntity getCategoryById(String firebaseUid, UUID categoryId) {
        return categoryRepository.findOwnedActiveCategory(firebaseUid, categoryId).orElse(null);
    }

    public CategoryEntity createCategory(
            String firebaseUid,
            String name,
            String icon,
            String color,
            CategoryType categoryType
    ) {
        CategoryEntity category = CategoryEntity.builder()
                .firebaseUid(firebaseUid)
                .name(name)
                .icon(icon)
                .color(color)
                .categoryType(categoryType.name())
                .createdAt(LocalDateTime.now())
                .build();
        return categoryRepository.save(category);
    }
}
