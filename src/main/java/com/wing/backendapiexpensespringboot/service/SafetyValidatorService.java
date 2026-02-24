package com.wing.backendapiexpensespringboot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SafetyValidatorService {

    private static final List<String> DANGEROUS_KEYWORDS = List.of(
            "hack", "bypass", "exploit", "malware", "phishing", "fraud"
    );

    private static final double AMOUNT_CHANGE_THRESHOLD = 100.0;

    public List<String> validateText(String text) {
        List<String> warnings = new ArrayList<>();
        if (text == null) return warnings;

        String lowerText = text.toLowerCase();
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (lowerText.contains(keyword)) {
                warnings.add("Text contains potentially sensitive keyword: " + keyword);
            }
        }
        return warnings;
    }

    public void enforceNoAutoDelete(String question) {
        if (question != null) {
            String lower = question.toLowerCase();
            if (lower.contains("delete all") || lower.contains("remove everything")) {
                throw new SecurityException("Cannot process bulk delete requests.");
            }
        }
    }

    public void enforceNoSilentAmountChange(Double originalAmount, Double correctedAmount, Boolean confirmed) {
        if (originalAmount != null && correctedAmount != null) {
            double diff = Math.abs(originalAmount - correctedAmount);
            if (diff > AMOUNT_CHANGE_THRESHOLD && !Boolean.TRUE.equals(confirmed)) {
                throw new SecurityException("Large amount change requires explicit confirmation.");
            }
        }
    }

    public Map<String, Object> finalizePayload(Map<String, Object> response) {
        if (response == null) return new HashMap<>();

        // Add default values if not present
        response.putIfAbsent("needsConfirmation", false);
        response.putIfAbsent("safetyWarnings", new ArrayList<>());

        return response;
    }
}
