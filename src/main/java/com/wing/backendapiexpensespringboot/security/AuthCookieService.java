package com.wing.backendapiexpensespringboot.security;

import com.wing.backendapiexpensespringboot.config.AppConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

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
        String cookieString = buildAccessCookie(token, Math.max(maxAgeSeconds, 0L)).toString();
        if (cookieString.contains("SameSite=None")) {
            cookieString += "; Partitioned";
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookieString);
    }

    public void clearAccessToken(HttpServletResponse response) {
        String cookieString = buildAccessCookie("", 0L).toString();
        if (cookieString.contains("SameSite=None")) {
            cookieString += "; Partitioned";
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookieString);
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
            throw new IllegalStateException("app.auth.same-site must be configured explicitly");
        }

        String normalized = sameSite.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "strict" -> "Strict";
            case "none" -> {
                if (!cookieSecure) {
                    throw new IllegalStateException("SameSite=None requires secure cookies");
                }
                yield "None";
            }
            case "lax" -> "Lax";
            default -> throw new IllegalStateException("Unsupported app.auth.same-site value: " + sameSite);
        };
    }
}
