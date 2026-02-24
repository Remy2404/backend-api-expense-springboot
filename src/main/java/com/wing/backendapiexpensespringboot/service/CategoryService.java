package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryEntity> getCategories(String firebaseUid) {
        return categoryRepository.findByFirebaseUidOrderByNameAsc(firebaseUid);
    }

    public CategoryEntity getCategoryById(String firebaseUid, UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(c -> c.getFirebaseUid().equals(firebaseUid))
                .orElse(null);
    }

    public CategoryEntity createCategory(String firebaseUid, String name, String icon, String color) {
        CategoryEntity category = CategoryEntity.builder()
                .firebaseUid(firebaseUid)
                .name(name)
                .icon(icon)
                .color(color)
                .build();
        return categoryRepository.save(category);
    }
}
