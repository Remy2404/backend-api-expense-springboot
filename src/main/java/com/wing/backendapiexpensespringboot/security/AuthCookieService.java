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

        String cookieName = appConfig.getAuth().getAccessCookieName();
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

        return ResponseCookie.from(authConfig.getAccessCookieName(), token)
                .httpOnly(true)
                .secure(authConfig.isCookieSecure())
                .path(resolveCookiePath(authConfig.getCookiePath()))
                .sameSite(resolveSameSite(authConfig.getSameSite()))
                .maxAge(maxAgeSeconds)
                .build();
    }

    private String resolveCookiePath(String cookiePath) {
        return StringUtils.hasText(cookiePath) ? cookiePath.trim() : "/";
    }

    private String resolveSameSite(String sameSite) {
        return StringUtils.hasText(sameSite) ? sameSite.trim() : "Lax";
    }
}
