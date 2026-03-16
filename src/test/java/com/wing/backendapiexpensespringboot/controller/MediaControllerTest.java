package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.media.ImageUploadAuthResponse;
import com.wing.backendapiexpensespringboot.dto.media.SignedMediaUrlResponse;
import com.wing.backendapiexpensespringboot.security.FirebaseAuthFilter;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.media.ImageKitMediaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaController.class)
@AutoConfigureMockMvc(addFilters = false)
class MediaControllerTest {

        private static final String FIREBASE_UID = "firebase-user-1";

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private ImageKitMediaService imageKitMediaService;

        @MockitoBean
        private FirebaseAuthFilter firebaseAuthFilter;

        @Test
        void getUploadAuthReturnsPayload() throws Exception {
                when(imageKitMediaService.createUploadAuth(FIREBASE_UID)).thenReturn(ImageUploadAuthResponse.builder()
                                .token("token")
                                .expire(1735689600L)
                                .signature("signature")
                                .publicKey("public")
                                .urlEndpoint("https://ik.example.com/demo")
                                .uploadFolder("/receipts/firebase-user-1")
                                .maxFileSizeBytes(10_485_760L)
                                .allowedMimeTypes(java.util.List.of("image/jpeg"))
                                .privateFile(true)
                                .useUniqueFileName(true)
                                .build());

                mockMvc.perform(get("/media/upload-auth").with(authenticatedUser()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.token").value("token"))
                                .andExpect(jsonPath("$.upload_folder").value("/receipts/firebase-user-1"));

                verify(imageKitMediaService).createUploadAuth(FIREBASE_UID);
        }

        @Test
        void getSignedUrlReturnsPayload() throws Exception {
                when(imageKitMediaService.createSignedUrl(FIREBASE_UID, "/receipts/firebase-user-1/file.jpg"))
                                .thenReturn(SignedMediaUrlResponse.builder()
                                                .path("/receipts/firebase-user-1/file.jpg")
                                                .url("https://ik.example.com/demo/receipts/firebase-user-1/file.jpg?ik-t=1&ik-s=abc")
                                                .expiresAtEpochSeconds(1L)
                                                .build());

                mockMvc.perform(get("/media/signed-url")
                                .param("path", "/receipts/firebase-user-1/file.jpg")
                                .with(authenticatedUser()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.path").value("/receipts/firebase-user-1/file.jpg"))
                                .andExpect(jsonPath("$.url").exists());

                verify(imageKitMediaService).createSignedUrl(FIREBASE_UID, "/receipts/firebase-user-1/file.jpg");
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
