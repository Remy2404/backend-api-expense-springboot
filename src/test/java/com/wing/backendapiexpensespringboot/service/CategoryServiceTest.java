package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void getCategoriesReturnsOnlyActiveCategories() {
        String firebaseUid = "firebase-user-1";
        CategoryEntity category = CategoryEntity.builder()
                .firebaseUid(firebaseUid)
                .name("Food")
                .build();

        when(categoryRepository.findActiveByFirebaseUidOrderByNameAsc(firebaseUid))
                .thenReturn(List.of(category));

        List<CategoryEntity> result = categoryService.getCategories(firebaseUid);

        assertEquals(1, result.size());
        assertEquals("Food", result.getFirst().getName());
        verify(categoryRepository).findActiveByFirebaseUidOrderByNameAsc(firebaseUid);
    }
}
