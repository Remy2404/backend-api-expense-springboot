package com.wing.backendapiexpensespringboot.security;

import com.wing.backendapiexpensespringboot.config.AppConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCookieService {

    private final AppConfig appConfig;

    public Optional<String> readAccessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }

        String cookieName = resolveCookieName(appConfig.getAuth().getAccessCookieName());
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst();
    }

    public void writeAccessToken(HttpServletResponse response, String token, long maxAgeSeconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildAccessCookie(token, Math.max(maxAgeSeconds, 0L)).toString());
    }

    public void clearAccessToken(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildAccessCookie("", 0L).toString());
    }

    private ResponseCookie buildAccessCookie(String token, long maxAgeSeconds) {
        AppConfig.Auth authConfig = appConfig.getAuth();
        boolean cookieSecure = resolveCookieSecure(authConfig.getCookieSecure());

        return ResponseCookie.from(resolveCookieName(authConfig.getAccessCookieName()), token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path(resolveCookiePath(authConfig.getCookiePath()))
                .sameSite(resolveSameSite(authConfig.getSameSite(), cookieSecure))
                .maxAge(maxAgeSeconds)
                .build();
    }

    private String resolveCookieName(String cookieName) {
        return StringUtils.hasText(cookieName) ? cookieName.trim() : "access_token";
    }

    private String resolveCookiePath(String cookiePath) {
        return StringUtils.hasText(cookiePath) ? cookiePath.trim() : "/";
    }

    private boolean resolveCookieSecure(Boolean cookieSecure) {
        return Boolean.TRUE.equals(cookieSecure);
    }

    private String resolveSameSite(String sameSite, boolean cookieSecure) {
        if (!StringUtils.hasText(sameSite)) {
            return "Lax";
        }

        String normalized = sameSite.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "strict" -> "Strict";
            case "none" -> {
                if (!cookieSecure) {
                    log.warn("app.auth.same-site=None requires secure cookies; falling back to Lax.");
                    yield "Lax";
                }
                yield "None";
            }
            case "lax" -> "Lax";
            default -> {
                log.warn("Unsupported app.auth.same-site value '{}'; falling back to Lax.", sameSite);
                yield "Lax";
            }
        };
    }
}
