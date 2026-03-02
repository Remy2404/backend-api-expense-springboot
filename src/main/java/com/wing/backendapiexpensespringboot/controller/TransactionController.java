package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.CreateTransactionRequest;
import com.wing.backendapiexpensespringboot.dto.CreateTransactionResponse;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.TransactionCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionCommandService transactionCommandService;

    @PostMapping
    public ResponseEntity<CreateTransactionResponse> createTransaction(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        return ResponseEntity.ok(
                transactionCommandService.createTransaction(requireFirebaseUid(user), request)
        );
    }

    private String requireFirebaseUid(UserPrincipal user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            throw AppException.unauthorized("Missing authenticated user");
        }
        return user.getFirebaseUid();
    }
}
