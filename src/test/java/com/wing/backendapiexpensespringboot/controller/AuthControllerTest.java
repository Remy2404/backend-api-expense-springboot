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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthCookieService authCookieService;

    @Mock
    private FirebaseAuthenticationService firebaseAuthenticationService;

    @Mock
    private UserOnboardingService userOnboardingService;

    @Mock
    private AppConfig appConfig;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private AuthController authController;

    @Test
    void getSessionReturnsEmptyPayloadWhenAuthenticationCookieIsMissing() {
        when(authCookieService.readAccessToken(request)).thenReturn(Optional.empty());

        ResponseEntity<AuthSessionResponse> result = authController.getSession(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().firebaseUid()).isNull();
        assertThat(result.getBody().email()).isNull();
        assertThat(result.getBody().role()).isNull();
        assertThat(result.getBody().expiresAtEpochSeconds()).isNull();
        assertThat(result.getBody().firebaseCustomToken()).isNull();
        verify(firebaseAuthenticationService, never()).authenticate(anyString());
        verify(firebaseAuthenticationService, never()).issueCustomToken(any(UserPrincipal.class));
    }

    @Test
    void getSessionReturnsAuthenticatedUserWhenCookieIsPresent() {
        UserPrincipal principal = UserPrincipal.builder()
                .firebaseUid("firebase-uid-1")
                .email("qa@example.com")
                .role("USER")
                .build();
        AuthenticatedFirebaseUser authenticatedUser = new AuthenticatedFirebaseUser(principal, 1_735_689_600L);

        when(authCookieService.readAccessToken(request)).thenReturn(Optional.of("valid-token"));
        when(firebaseAuthenticationService.authenticateSessionCookie("valid-token")).thenReturn(authenticatedUser);
        when(firebaseAuthenticationService.issueCustomToken(principal)).thenReturn("custom-token");

        ResponseEntity<AuthSessionResponse> result = authController.getSession(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().firebaseUid()).isEqualTo("firebase-uid-1");
        assertThat(result.getBody().email()).isEqualTo("qa@example.com");
        assertThat(result.getBody().role()).isEqualTo("USER");
        assertThat(result.getBody().expiresAtEpochSeconds()).isEqualTo(1_735_689_600L);
        assertThat(result.getBody().firebaseCustomToken()).isEqualTo("custom-token");
    }

    @Test
    void getSessionThrowsUnauthorizedWhenCookieTokenIsInvalid() {
        when(authCookieService.readAccessToken(request)).thenReturn(Optional.of("invalid-token"));
        when(firebaseAuthenticationService.authenticateSessionCookie("invalid-token"))
                .thenThrow(AppException.unauthorized("Authentication token is invalid."));

        assertThatThrownBy(() -> authController.getSession(request))
                .isInstanceOf(AppException.class)
                .hasMessage("Authentication token is invalid.");
    }

    @Test
    void logoutStillClearsCookieWhenTokenValidationFailsWithUnauthorized() {
        when(authCookieService.readAccessToken(request)).thenReturn(Optional.of("expired-token"));
        when(firebaseAuthenticationService.authenticateSessionCookie("expired-token"))
                .thenThrow(AppException.unauthorized("Authentication token has expired."));

        ResponseEntity<Void> result = authController.logout(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authCookieService).clearAccessToken(response);
    }

    @Test
    void logoutClearsCookieAndRethrowsWhenTokenValidationFailsWithServerError() {
        when(authCookieService.readAccessToken(request)).thenReturn(Optional.of("server-error-token"));
        when(firebaseAuthenticationService.authenticateSessionCookie("server-error-token"))
                .thenThrow(AppException.internalError("Firebase service unavailable."));

        assertThatThrownBy(() -> authController.logout(request, response))
                .isInstanceOf(AppException.class)
                .hasMessage("Firebase service unavailable.");
        verify(authCookieService).clearAccessToken(response);
    }

    @Test
    void createSessionReturnsCookieScopedSessionWithoutReauthenticatingSessionCookie() {
        AppConfig.Auth auth = new AppConfig.Auth();
        auth.setSessionMaxAgeSeconds(4_320_000L);
        when(appConfig.getAuth()).thenReturn(auth);

        UserPrincipal principal = UserPrincipal.builder()
                .firebaseUid("firebase-uid-2")
                .email("new@example.com")
                .displayName("New User")
                .photoUrl("https://example.com/photo.png")
                .role("USER")
                .build();
        AuthenticatedFirebaseUser authenticatedUser = new AuthenticatedFirebaseUser(principal, 1_735_689_600L);

        when(firebaseAuthenticationService.authenticate("id-token")).thenReturn(authenticatedUser);
        when(firebaseAuthenticationService.createSessionCookie("id-token")).thenReturn("session-cookie");

        ResponseEntity<AuthSessionResponse> responseEntity = authController.createSession(
                new AuthSessionRequest("id-token"),
                response);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().firebaseUid()).isEqualTo("firebase-uid-2");
        assertThat(responseEntity.getBody().role()).isEqualTo("USER");
        assertThat(responseEntity.getBody().firebaseCustomToken()).isNull();
        verify(firebaseAuthenticationService).authenticate("id-token");
        verify(firebaseAuthenticationService).createSessionCookie("id-token");
        verify(firebaseAuthenticationService, never()).authenticateSessionCookie(anyString());
        verify(authCookieService).writeAccessToken(response, "session-cookie", 4_320_000L);
        verify(userOnboardingService).warmUpAfterSessionCreated(principal);
    }

    @Test
    void createSessionStillSucceedsWhenWarmUpSubmissionIsRejected() {
        AppConfig.Auth auth = new AppConfig.Auth();
        auth.setSessionMaxAgeSeconds(4_320_000L);
        when(appConfig.getAuth()).thenReturn(auth);

        UserPrincipal principal = UserPrincipal.builder()
                .firebaseUid("firebase-uid-3")
                .email("warmup@example.com")
                .role("USER")
                .build();
        AuthenticatedFirebaseUser authenticatedUser = new AuthenticatedFirebaseUser(principal, 1_735_689_600L);

        when(firebaseAuthenticationService.authenticate("id-token")).thenReturn(authenticatedUser);
        when(firebaseAuthenticationService.createSessionCookie("id-token")).thenReturn("session-cookie");
        doThrow(new TaskRejectedException("executor saturated"))
                .when(userOnboardingService)
                .warmUpAfterSessionCreated(principal);

        ResponseEntity<AuthSessionResponse> responseEntity = authController.createSession(
                new AuthSessionRequest("id-token"),
                response);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authCookieService).writeAccessToken(response, "session-cookie", 4_320_000L);
    }
}
