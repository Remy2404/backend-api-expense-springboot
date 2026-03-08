package com.wing.backendapiexpensespringboot.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import org.springframework.web.bind.annotation.RequestMethod;

@RestController
public class HealthController {

    @RequestMapping(value = "/health", method = { RequestMethod.GET, RequestMethod.HEAD })
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
