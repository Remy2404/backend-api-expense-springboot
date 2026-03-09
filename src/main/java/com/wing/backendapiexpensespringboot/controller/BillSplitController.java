package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.BillSplitDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.BillSplitQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bill-splits")
@RequiredArgsConstructor
public class BillSplitController {

    private final BillSplitQueryService queryService;
    private final com.wing.backendapiexpensespringboot.service.BillSplitService billSplitService;

    @GetMapping("/groups")
    public ResponseEntity<List<BillSplitDto.GroupSummary>> listGroups(
            @AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(queryService.getGroupsSummary(requireFirebaseUid(user)));
    }

    @GetMapping("/groups/{groupId}")
    public ResponseEntity<BillSplitDto.GroupDetailsPayload> getGroupDetails(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(queryService.getGroupDetails(requireFirebaseUid(user), groupId));
    }

    @PostMapping("/groups")
    public ResponseEntity<Void> createGroup(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody BillSplitDto.CreateGroupRequest request) {
        billSplitService.createGroup(requireFirebaseUid(user), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/groups/{groupId}/expenses")
    public ResponseEntity<Void> addExpense(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID groupId,
            @RequestBody BillSplitDto.AddExpenseRequest request) {
        billSplitService.addExpense(requireFirebaseUid(user), groupId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/groups/{groupId}/settle")
    public ResponseEntity<Void> settleShare(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID groupId,
            @RequestBody BillSplitDto.SettleShareRequest request) {
        billSplitService.settleShare(requireFirebaseUid(user), groupId, request);
        return ResponseEntity.ok().build();
    }

    private String requireFirebaseUid(UserPrincipal user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            throw AppException.unauthorized("Missing authenticated user");
        }
        return user.getFirebaseUid();
    }
}
