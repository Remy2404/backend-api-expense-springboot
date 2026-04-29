package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.ChatHistoryResponse;
import com.wing.backendapiexpensespringboot.dto.ChatRequest;
import com.wing.backendapiexpensespringboot.dto.ChatResponse;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.AiPendingActionExecutionService;
import com.wing.backendapiexpensespringboot.service.AiChatSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatSessionService aiChatSessionService;
    private final AiPendingActionExecutionService aiPendingActionExecutionService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        ChatResponse response = aiChatSessionService.chat(requireFirebaseUid(user), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/chat/stream")
    public ResponseEntity<ChatResponse> streamChat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        ChatResponse response = aiChatSessionService.streamChat(requireFirebaseUid(user), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/chat/history")
    public ResponseEntity<ChatHistoryResponse> history(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(name = "limit", defaultValue = "40") int limit) {
        return ResponseEntity.ok(ChatHistoryResponse.builder()
                .messages(aiChatSessionService.getHistory(requireFirebaseUid(user), limit))
                .build());
    }

    @DeleteMapping("/chat/history")
    public ResponseEntity<Void> clearHistory(@AuthenticationPrincipal UserPrincipal user) {
        aiChatSessionService.clearHistory(requireFirebaseUid(user));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/chat/actions/{actionId}/confirm")
    public ResponseEntity<Void> confirmAction(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable("actionId") java.util.UUID actionId) {
        aiPendingActionExecutionService.confirmAndExecute(requireFirebaseUid(user), actionId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/chat/actions/{actionId}/cancel")
    public ResponseEntity<Void> cancelAction(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable("actionId") java.util.UUID actionId) {
        aiPendingActionExecutionService.cancel(requireFirebaseUid(user), actionId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private String requireFirebaseUid(UserPrincipal user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            throw AppException.unauthorized("Missing authenticated user");
        }
        return user.getFirebaseUid();
    }
}
