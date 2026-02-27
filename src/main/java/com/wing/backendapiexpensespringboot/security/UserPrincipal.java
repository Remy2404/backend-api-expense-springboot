package com.wing.backendapiexpensespringboot.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal {

    private String firebaseUid;
    private String email;
    private String role;
    private String token;
    private Map<String, Object> claims;

    public List<GrantedAuthority> getAuthorities() {
        String normalizedRole = role == null || role.isBlank() ? AppRole.USER.name() : role;
        return AuthorityUtils.createAuthorityList("ROLE_" + normalizedRole);
    }
}
