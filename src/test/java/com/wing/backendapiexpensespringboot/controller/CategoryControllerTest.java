package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.repository.CategoryRepository;
import com.wing.backendapiexpensespringboot.security.FirebaseAuthFilter;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CategoryService.class)
class CategoryControllerTest {

        private static final String FIREBASE_UID = "firebase-user-1";

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private CategoryRepository categoryRepository;

        @MockitoBean
        private FirebaseAuthFilter firebaseAuthFilter;

        @Test
        void listCategoriesReturnsOnlyActiveCategories() throws Exception {
                UUID activeId = UUID.randomUUID();
                CategoryEntity activeCategory = CategoryEntity.builder()
                                .id(activeId)
                                .firebaseUid(FIREBASE_UID)
                                .name("Food")
                                .icon("utensils")
                                .color("#22c55e")
                                .isDefault(false)
                                .categoryType("EXPENSE")
                                .isDeleted(false)
                                .createdAt(OffsetDateTime.of(2026, 3, 13, 9, 0, 0, 0, ZoneOffset.UTC))
                                .updatedAt(OffsetDateTime.of(2026, 3, 13, 9, 0, 0, 0, ZoneOffset.UTC))
                                .build();

                CategoryEntity deletedCategory = CategoryEntity.builder()
                                .id(UUID.randomUUID())
                                .firebaseUid(FIREBASE_UID)
                                .name("Bills")
                                .icon("receipt")
                                .color("#ef4444")
                                .isDefault(false)
                                .categoryType("EXPENSE")
                                .isDeleted(true)
                                .deletedAt(OffsetDateTime.of(2026, 3, 13, 10, 0, 0, 0, ZoneOffset.UTC))
                                .build();

                when(categoryRepository.findActiveByFirebaseUidOrderByNameAsc(FIREBASE_UID))
                                .thenReturn(List.of(activeCategory));
                when(categoryRepository.findByFirebaseUidOrderByNameAsc(FIREBASE_UID))
                                .thenReturn(List.of(activeCategory, deletedCategory));

                mockMvc.perform(get("/categories").with(authenticatedUser()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].id").value(activeId.toString()))
                                .andExpect(jsonPath("$[0].name").value("Food"));

                verify(categoryRepository).findActiveByFirebaseUidOrderByNameAsc(FIREBASE_UID);
                verify(categoryRepository, never()).findByFirebaseUidOrderByNameAsc(FIREBASE_UID);
        }

        private RequestPostProcessor authenticatedUser() {
                UserPrincipal principal = UserPrincipal.builder()
                                .firebaseUid(FIREBASE_UID)
                                .role("USER")
                                .build();
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities());
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                return request -> {
                        SecurityContextHolder.setContext(context);
                        return request;
                };
        }
}
