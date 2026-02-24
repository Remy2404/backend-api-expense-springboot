package com.wing.backendapiexpensespringboot.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal {

    private String firebaseUid;
    private String token;
    private Map<String, Object> claims;
}
