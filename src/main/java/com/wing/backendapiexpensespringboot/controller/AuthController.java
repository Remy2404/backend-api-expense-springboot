package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.AuthSessionRequest;
import com.wing.backendapiexpensespringboot.dto.AuthSessionResponse;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.AuthCookieService;
import com.wing.backendapiexpensespringboot.security.FirebaseAuthenticationService;
import com.wing.backendapiexpensespringboot.security.FirebaseAuthenticationService.AuthenticatedFirebaseUser;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {

    private final AuthCookieService authCookieService;
    private final FirebaseAuthenticationService firebaseAuthenticationService;

    @GetMapping("/session")
    public ResponseEntity<AuthSessionResponse> getSession(HttpServletRequest request) {
        String idToken = authCookieService.readAccessToken(request)
                .orElseThrow(() -> AppException.unauthorized("Missing authentication cookie."));
        AuthenticatedFirebaseUser authenticatedUser = firebaseAuthenticationService.authenticate(idToken, false);
        return ResponseEntity.ok(toSessionResponse(
                authenticatedUser,
                firebaseAuthenticationService.issueCustomToken(authenticatedUser.principal())));
    }

    @PostMapping("/session")
    public ResponseEntity<AuthSessionResponse> createSession(
            @Valid @RequestBody AuthSessionRequest request,
            HttpServletResponse response) {
        AuthenticatedFirebaseUser authenticatedUser = firebaseAuthenticationService.authenticate(request.idToken(),
                true);

        authCookieService.writeAccessToken(response, request.idToken().trim(), authenticatedUser.maxAgeSeconds());
        return ResponseEntity.ok(toSessionResponse(authenticatedUser, null));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            authCookieService.readAccessToken(request).ifPresent(idToken -> {
                try {
                    AuthenticatedFirebaseUser authenticatedUser = firebaseAuthenticationService.authenticate(idToken,
                            false);
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
}
