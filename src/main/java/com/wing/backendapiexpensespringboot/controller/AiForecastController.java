package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.ForecastResponse;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.ForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiForecastController {

    private final ForecastService forecastService;

    @GetMapping("/forecast")
    public ResponseEntity<ForecastResponse> getForecast(
            @AuthenticationPrincipal UserPrincipal user) {

        ForecastResponse response = forecastService.getForecast(user.getFirebaseUid());
        return ResponseEntity.ok(response);
    }
}
