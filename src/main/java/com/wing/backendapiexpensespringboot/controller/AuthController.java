package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.config.AppConfig;
import com.wing.backendapiexpensespringboot.dto.AuthSessionRequest;
import com.wing.backendapiexpensespringboot.dto.AuthSessionResponse;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.AuthCookieService;
import com.wing.backendapiexpensespringboot.security.FirebaseAuthenticationService;
import com.wing.backendapiexpensespringboot.security.FirebaseAuthenticationService.AuthenticatedFirebaseUser;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.UserOnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {

    private final AppConfig appConfig;
    private final AuthCookieService authCookieService;
    private final FirebaseAuthenticationService firebaseAuthenticationService;
    private final UserOnboardingService userOnboardingService;

    @GetMapping("/session")
    public ResponseEntity<AuthSessionResponse> getSession(HttpServletRequest request) {
        String sessionCookie = authCookieService.readAccessToken(request).orElse(null);
        if (sessionCookie == null) {
            return ResponseEntity.ok(AuthSessionResponse.builder().build());
        }

        AuthenticatedFirebaseUser authenticatedUser = firebaseAuthenticationService.authenticateSessionCookie(sessionCookie);
        return ResponseEntity.ok(toSessionResponse(
                authenticatedUser,
                firebaseAuthenticationService.issueCustomToken(authenticatedUser.principal())));
    }

    @PostMapping("/session")
    public ResponseEntity<AuthSessionResponse> createSession(
            @Valid @RequestBody AuthSessionRequest request,
            HttpServletResponse response) {
        AuthenticatedFirebaseUser authenticatedUser = firebaseAuthenticationService.authenticate(request.idToken());
        String sessionCookie = firebaseAuthenticationService.createSessionCookie(request.idToken());
        authCookieService.writeAccessToken(
                response,
                sessionCookie,
                appConfig.getAuth().getSessionMaxAgeSeconds());
        submitWarmUp(authenticatedUser.principal());
        return ResponseEntity.ok(toSessionResponse(toSessionScopedUser(authenticatedUser.principal()), null));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            authCookieService.readAccessToken(request).ifPresent(sessionCookie -> {
                try {
                    AuthenticatedFirebaseUser authenticatedUser = firebaseAuthenticationService
                            .authenticateSessionCookie(sessionCookie);
                    firebaseAuthenticationService.revokeRefreshTokens(authenticatedUser.principal().getFirebaseUid());
                } catch (AppException exception) {
                    if (exception.getStatusCode() != HttpStatus.UNAUTHORIZED) {
                        throw exception;
                    }
                }
            });
        } finally {
            SecurityContextHolder.clearContext();
            authCookieService.clearAccessToken(response);
        }

        return ResponseEntity.noContent().build();
    }

    private AuthSessionResponse toSessionResponse(
            AuthenticatedFirebaseUser authenticatedUser,
            String firebaseCustomToken) {
        UserPrincipal principal = authenticatedUser.principal();
        return AuthSessionResponse.builder()
                .firebaseUid(principal.getFirebaseUid())
                .email(principal.getEmail())
                .role(principal.getRole())
                .expiresAtEpochSeconds(authenticatedUser.expiresAtEpochSeconds())
                .firebaseCustomToken(firebaseCustomToken)
                .build();
    }

    private void submitWarmUp(UserPrincipal principal) {
        try {
            userOnboardingService.warmUpAfterSessionCreated(principal);
        } catch (TaskRejectedException taskRejectedException) {
            log.warn("User bootstrap executor is saturated for {}: {}",
                    principal == null ? "unknown" : principal.getFirebaseUid(),
                    taskRejectedException.getMessage());
        }
    }

    private AuthenticatedFirebaseUser toSessionScopedUser(UserPrincipal principal) {
        long expiresAtEpochSeconds = Instant.now().getEpochSecond() + appConfig.getAuth().getSessionMaxAgeSeconds();
        return new AuthenticatedFirebaseUser(principal, expiresAtEpochSeconds);
    }
}
